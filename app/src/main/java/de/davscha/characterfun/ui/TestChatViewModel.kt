package de.davscha.characterfun.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davscha.characterfun.engine.Accelerator
import de.davscha.characterfun.engine.ChatEngine
import de.davscha.characterfun.engine.LiteRtEngine
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelDownloader
import de.davscha.characterfun.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DownloadState {
    data object Missing : DownloadState
    data class Running(val downloaded: Long, val total: Long) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val modelTitle: String) : EngineState
    data class Ready(val modelTitle: String, val accelerator: Accelerator, val loadSeconds: Double) : EngineState
    data class Failed(val message: String) : EngineState
}

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    /** Nur bei Antworten der Figur: Messwerte für den Tempo-Test */
    val stats: String? = null,
)

/** Testbildschirm für Meilenstein 1: Modell laden und mit einer Probe-Figur chatten. */
class TestChatViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val engine: ChatEngine = LiteRtEngine(app)

    val downloads = mutableStateMapOf<String, DownloadState>()
    var selectedModel by mutableStateOf(ModelCatalog.E2B)
        private set
    var engineState by mutableStateOf<EngineState>(EngineState.Idle)
        private set

    var systemPrompt by mutableStateOf(DEFAULT_PROMPT)
    var greeting by mutableStateOf(DEFAULT_GREETING)
    var temperature by mutableStateOf(0.9)

    val messages = mutableStateListOf<ChatMessage>()
    var isGenerating by mutableStateOf(false)
        private set

    private val downloadJobs = mutableMapOf<String, Job>()
    private var generateJob: Job? = null

    val isDownloading get() = downloads.values.any { it is DownloadState.Running }

    init {
        ModelCatalog.all.forEach { m ->
            downloads[m.id] = if (m.isDownloaded(context)) DownloadState.Done else DownloadState.Missing
        }
    }

    fun select(model: ModelInfo) {
        if (model == selectedModel) return
        selectedModel = model
        // Anderes Modell gewählt: altes freigeben, neues muss erst geladen werden
        stop()
        engine.close()
        engineState = EngineState.Idle
        messages.clear()
    }

    fun download(model: ModelInfo) {
        if (downloadJobs[model.id]?.isActive == true) return
        downloads[model.id] = DownloadState.Running(0, model.sizeBytes)
        downloadJobs[model.id] = viewModelScope.launch {
            try {
                ModelDownloader.download(context, model) { done, total ->
                    // Fortschritt kommt aus dem Hintergrund – Anzeige im UI-Thread aktualisieren
                    viewModelScope.launch(Dispatchers.Main) {
                        if (downloads[model.id] is DownloadState.Running) {
                            downloads[model.id] = DownloadState.Running(done, total)
                        }
                    }
                }
                downloads[model.id] = DownloadState.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloads[model.id] = DownloadState.Missing
                throw e
            } catch (e: Exception) {
                downloads[model.id] = DownloadState.Failed(e.message ?: "Download fehlgeschlagen")
            }
        }
    }

    fun cancelDownload(model: ModelInfo) {
        downloadJobs.remove(model.id)?.cancel()
    }

    fun delete(model: ModelInfo) {
        if (model == selectedModel) {
            stop()
            engine.close()
            engineState = EngineState.Idle
            messages.clear()
        }
        model.file(context).delete()
        model.partFile(context).delete()
        downloads[model.id] = DownloadState.Missing
    }

    fun loadSelected() {
        val model = selectedModel
        if (engineState is EngineState.Loading) return
        engineState = EngineState.Loading(model.title)
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            engineState = try {
                val acc = engine.load(model.file(context).absolutePath)
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                withContext(Dispatchers.IO) { startConversationInternal() }
                EngineState.Ready(model.title, acc, seconds)
            } catch (e: Exception) {
                EngineState.Failed(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    /** Neues Gespräch mit der aktuellen Figur (z. B. nach Änderungen am Text). */
    fun restartConversation() {
        if (engineState !is EngineState.Ready) return
        stop()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { startConversationInternal() }
                .onFailure { e -> withContext(Dispatchers.Main) { engineState = EngineState.Failed(e.message ?: "Fehler") } }
        }
    }

    private suspend fun startConversationInternal() {
        engine.startConversation(systemPrompt, greeting, temperature)
        withContext(Dispatchers.Main) {
            messages.clear()
            if (greeting.isNotBlank()) messages += ChatMessage(fromUser = false, text = greeting)
        }
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || isGenerating || engineState !is EngineState.Ready) return
        messages += ChatMessage(fromUser = true, text = input)
        messages += ChatMessage(fromUser = false, text = "")
        val index = messages.lastIndex
        isGenerating = true

        generateJob = viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            var firstChunkAt = 0L
            val reply = StringBuilder()
            try {
                engine.send(input).collect { chunk ->
                    if (firstChunkAt == 0L) firstChunkAt = SystemClock.elapsedRealtime()
                    reply.append(chunk)
                    messages[index] = messages[index].copy(text = reply.toString())
                }
                val end = SystemClock.elapsedRealtime()
                messages[index] = messages[index].copy(
                    text = reply.toString().trim(),
                    stats = formatStats(start, firstChunkAt, end),
                )
            } catch (e: Exception) {
                messages[index] = messages[index].copy(
                    text = reply.toString().ifEmpty { "⚠ ${e.message ?: "Fehler beim Antworten"}" },
                )
            } finally {
                isGenerating = false
            }
        }
    }

    fun stop() {
        engine.stop()
    }

    private fun formatStats(start: Long, firstChunk: Long, end: Long): String {
        val total = (end - start) / 1000.0
        if (firstChunk == 0L) return "%.1f s".format(total)
        val first = (firstChunk - start) / 1000.0
        return "erstes Wort nach %.1f s · %.1f s gesamt".format(first, total)
    }

    override fun onCleared() {
        engine.close()
    }

    companion object {
        val DEFAULT_PROMPT = """
            Du bist Kapitän Kraken, ein gutmütiger, etwas angeberischer Tintenfisch-Piratenkapitän.
            Du sprichst Deutsch wie ein alter Seebär ("Ahoi!", "Bei meinen acht Armen!").
            Du antwortest kurz, in zwei bis vier Sätzen, und bleibst immer in deiner Rolle.
            Du erzählst gern maßlos übertriebene Geschichten von deinen Abenteuern auf den sieben Meeren.
            Du gibst nie zu, eine KI zu sein.
        """.trimIndent()

        const val DEFAULT_GREETING =
            "Ahoi, Landratte! Kapitän Kraken hier – acht Arme, null Langeweile. Was führt dich an Bord meines Schiffes?"
    }
}
