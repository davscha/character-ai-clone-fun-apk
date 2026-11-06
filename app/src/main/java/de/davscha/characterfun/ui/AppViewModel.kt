package de.davscha.characterfun.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.CharacterRepository
import de.davscha.characterfun.data.PromptBuilder
import de.davscha.characterfun.engine.Accelerator
import de.davscha.characterfun.engine.ChatEngine
import de.davscha.characterfun.engine.LiteRtEngine
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelDownloader
import de.davscha.characterfun.model.ModelInfo
import de.davscha.characterfun.model.hasSharedStorage
import de.davscha.characterfun.model.internalModelsDir
import de.davscha.characterfun.model.sharedModelsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DownloadState {
    data object Missing : DownloadState
    data class Running(val downloaded: Long, val total: Long) : DownloadState
    /** Modell wird aus dem internen App-Ordner in den dauerhaften Ordner kopiert */
    data class Moving(val copied: Long, val total: Long) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val modelTitle: String) : EngineState
    data class Ready(val model: ModelInfo, val accelerator: Accelerator, val loadSeconds: Double) : EngineState
    data class Failed(val message: String) : EngineState
}

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    /** Nur bei Antworten der Figur: Zeit bis zum ersten Wort und Gesamtzeit */
    val stats: String? = null,
)

/** Die Seiten der App */
sealed interface Screen {
    data object Gallery : Screen
    data object Models : Screen
    data class Chat(val characterId: String) : Screen
    /** characterId = null: neue Figur */
    data class Editor(val characterId: String?) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val engine: ChatEngine = LiteRtEngine(app)
    val repo = CharacterRepository(app)

    // ---------- Navigation ----------

    var screen by mutableStateOf<Screen>(Screen.Gallery)
        private set
    private val backStack = mutableListOf<Screen>()

    fun navigate(to: Screen) {
        backStack += screen
        screen = to
    }

    /** false = es gibt nichts mehr zurück, App darf sich schließen */
    fun back(): Boolean {
        if (screen is Screen.Chat) stop()
        screen = backStack.removeLastOrNull() ?: return false
        return true
    }

    // ---------- Modelle ----------

    val downloads = mutableStateMapOf<String, DownloadState>()
    var selectedModel by mutableStateOf(
        ModelCatalog.all.firstOrNull { it.id == prefs.getString("modell", null) } ?: ModelCatalog.E2B
    )
        private set
    var engineState by mutableStateOf<EngineState>(EngineState.Idle)
        private set

    /** true = Modelle und Figuren liegen dauerhaft in /CharacterFun */
    var sharedStorage by mutableStateOf(hasSharedStorage())
        private set

    private val downloadJobs = mutableMapOf<String, Job>()
    val isDownloading get() = downloads.values.any { it is DownloadState.Running || it is DownloadState.Moving }

    // ---------- Figuren ----------

    val characters = mutableStateListOf<Character>()

    fun character(id: String) = characters.firstOrNull { it.id == id }

    // ---------- Chat ----------

    var chatCharacter by mutableStateOf<Character?>(null)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var isGenerating by mutableStateOf(false)
        private set
    var temperature by mutableStateOf(prefs.getFloat("kreativitaet", 0.9f).toDouble())
        private set

    private var chatJob: Job? = null

    /** true, solange Modell geladen bzw. das Gespräch vorbereitet wird */
    var preparing by mutableStateOf(false)
        private set

    init {
        refreshStorage()
    }

    /** Beim Start und nach Rückkehr aus den Einstellungen: Speicherort prüfen, alte Dateien umziehen. */
    fun refreshStorage() {
        val wasShared = sharedStorage
        sharedStorage = hasSharedStorage()
        ModelCatalog.all.forEach { m ->
            val busy = downloads[m.id] is DownloadState.Running || downloads[m.id] is DownloadState.Moving
            if (!busy) downloads[m.id] = if (m.isDownloaded(context)) DownloadState.Done else DownloadState.Missing
        }
        if (sharedStorage) moveInternalModelsToShared()
        // Figuren laden – beim Start und wenn sich der Speicherort geändert hat (dann ziehen sie mit um)
        if (characters.isEmpty() || wasShared != sharedStorage) {
            characters.clear()
            characters.addAll(repo.load())
        }
    }

    /** Schon geladene Modelle aus dem internen Ordner übernehmen, statt sie neu herunterzuladen. */
    private fun moveInternalModelsToShared() {
        ModelCatalog.all.forEach { m ->
            val old = File(internalModelsDir(context), m.fileName)
            val new = m.file(context)
            if (!old.exists() || new.exists() || downloads[m.id] is DownloadState.Moving) return@forEach
            if (downloadJobs[m.id]?.isActive == true) return@forEach
            downloads[m.id] = DownloadState.Moving(0, old.length())
            downloadJobs[m.id] = viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val tmp = File(new.parentFile, "${m.fileName}.part")
                        old.inputStream().use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(1 shl 20)
                                var copied = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    copied += n
                                    if (copied % (64L shl 20) < buf.size) {
                                        withContext(Dispatchers.Main) { downloads[m.id] = DownloadState.Moving(copied, old.length()) }
                                    }
                                }
                            }
                        }
                        if (!tmp.renameTo(new)) error("Umbenennen fehlgeschlagen")
                        old.delete()
                    }
                    downloads[m.id] = DownloadState.Done
                } catch (e: Exception) {
                    downloads[m.id] = DownloadState.Failed("Umzug fehlgeschlagen: ${e.message}")
                }
            }
        }
    }

    fun select(model: ModelInfo) {
        if (model == selectedModel) return
        selectedModel = model
        prefs.edit().putString("modell", model.id).apply()
        // Anderes Modell gewählt: altes freigeben, das neue wird beim nächsten Chat geladen
        stop()
        engine.close()
        engineState = EngineState.Idle
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
        val ready = engineState
        if (ready is EngineState.Ready && ready.model == model) {
            stop()
            engine.close()
            engineState = EngineState.Idle
        }
        listOf(internalModelsDir(context), sharedModelsDir()).forEach { dir ->
            File(dir, model.fileName).delete()
            File(dir, "${model.fileName}.part").delete()
        }
        downloads[model.id] = DownloadState.Missing
    }

    // ---------- Figuren bearbeiten ----------

    fun saveCharacter(c: Character) {
        val i = characters.indexOfFirst { it.id == c.id }
        if (i >= 0) characters[i] = c else characters.add(0, c)
        // Im Chat gilt die neue Fassung ab dem nächsten "Neues Gespräch"
        if (chatCharacter?.id == c.id) chatCharacter = c
        persist()
    }

    fun deleteCharacter(id: String) {
        characters.firstOrNull { it.id == id }?.let { c -> repo.avatar(c)?.delete() }
        characters.removeAll { it.id == id }
        // Chat mit der gelöschten Figur aus dem Verlauf entfernen
        backStack.removeAll { it is Screen.Chat && it.characterId == id }
        persist()
    }

    private fun persist() {
        val snapshot = characters.toList()
        viewModelScope.launch(Dispatchers.IO) { repo.save(snapshot) }
    }

    suspend fun importAvatar(uri: Uri, characterId: String): String? =
        withContext(Dispatchers.IO) { repo.importAvatar(uri, characterId) }

    // ---------- Chat ----------

    /** Öffnet den Chat mit einer Figur; lädt das Modell, falls nötig. */
    fun openChat(c: Character) {
        stop()
        chatCharacter = c
        messages.clear()
        if (c.greeting.isNotBlank()) messages += ChatMessage(fromUser = false, text = c.greeting)
        navigate(Screen.Chat(c.id))
        prepareConversation()
    }

    /** Neues Gespräch mit derselben Figur. */
    fun restartChat() {
        val c = chatCharacter ?: return
        stop()
        messages.clear()
        if (c.greeting.isNotBlank()) messages += ChatMessage(fromUser = false, text = c.greeting)
        prepareConversation()
    }

    fun changeTemperature(value: Double) {
        temperature = value
        prefs.edit().putFloat("kreativitaet", value.toFloat()).apply()
    }

    /** Nochmal versuchen, z. B. nach Modellwechsel oder Fehler */
    fun retryChat() = prepareConversation()

    private fun prepareConversation() {
        val c = chatCharacter ?: return
        val model = selectedModel
        if (downloads[model.id] != DownloadState.Done) {
            engineState = EngineState.Failed("${model.title} ist noch nicht heruntergeladen.")
            return
        }
        chatJob?.cancel()
        preparing = true
        chatJob = viewModelScope.launch {
            try {
                val state = engineState
                if (state !is EngineState.Ready || state.model != model) {
                    engineState = EngineState.Loading(model.title)
                    val start = SystemClock.elapsedRealtime()
                    val acc = engine.load(model.file(context).absolutePath)
                    engineState = EngineState.Ready(model, acc, (SystemClock.elapsedRealtime() - start) / 1000.0)
                }
                withContext(Dispatchers.IO) {
                    engine.startConversation(PromptBuilder.build(c), c.greeting, temperature)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                engineState = EngineState.Failed(e.message ?: "Modell konnte nicht geladen werden")
            } finally {
                preparing = false
            }
        }
    }

    /** true, sobald Modell geladen und Gespräch bereit ist */
    val chatReady get() = engineState is EngineState.Ready && !preparing

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || isGenerating || !chatReady) return
        messages += ChatMessage(fromUser = true, text = input)
        messages += ChatMessage(fromUser = false, text = "")
        val index = messages.lastIndex
        isGenerating = true

        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            var firstChunkAt = 0L
            val reply = StringBuilder()
            try {
                engine.send(input).collect { chunk ->
                    if (firstChunkAt == 0L) firstChunkAt = SystemClock.elapsedRealtime()
                    reply.append(chunk)
                    if (index < messages.size) messages[index] = messages[index].copy(text = reply.toString())
                }
                val end = SystemClock.elapsedRealtime()
                if (index < messages.size) {
                    messages[index] = messages[index].copy(
                        text = reply.toString().trim(),
                        stats = formatStats(start, firstChunkAt, end),
                    )
                }
            } catch (e: Exception) {
                if (index < messages.size) {
                    messages[index] = messages[index].copy(
                        text = reply.toString().ifEmpty { "⚠ ${e.message ?: "Fehler beim Antworten"}" },
                    )
                }
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
}
