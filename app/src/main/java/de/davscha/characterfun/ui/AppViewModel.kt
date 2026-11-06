package de.davscha.characterfun.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davscha.characterfun.data.Character
import de.davscha.characterfun.data.ChatMessage
import de.davscha.characterfun.data.SavedChat
import de.davscha.characterfun.data.CharacterRepository
import de.davscha.characterfun.data.PromptBuilder
import de.davscha.characterfun.data.ProfileGenerator
import de.davscha.characterfun.engine.Accelerator
import de.davscha.characterfun.engine.ChatEngine
import de.davscha.characterfun.engine.LiteRtEngine
import de.davscha.characterfun.model.ModelCatalog
import de.davscha.characterfun.model.ModelDownloader
import de.davscha.characterfun.model.ModelInfo
import de.davscha.characterfun.model.hasSharedStorage
import de.davscha.characterfun.model.internalModelsDir
import de.davscha.characterfun.model.modelsDir
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
    /** Feste Liste + eigene .litertlm-Dateien aus dem Modell-Ordner */
    val allModels = mutableStateListOf<ModelInfo>()
    var selectedModel by mutableStateOf(ModelCatalog.E2B)
        private set

    /** Import einer eigenen Modelldatei: Name und Fortschritt (null = kein Import) */
    var importName by mutableStateOf<String?>(null)
        private set
    var importState by mutableStateOf<DownloadState?>(null)
        private set

    /** Hinweis, wenn die App beim letzten Laden eines Modells abgestürzt ist */
    var crashNotice by mutableStateOf<String?>(null)
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

    /** Chats für "Weiter chatten", neueste zuerst */
    val recentChats = mutableStateListOf<SavedChat>()

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
        val savedId = prefs.getString("modell", null)
        selectedModel = allModels.firstOrNull { it.id == savedId } ?: ModelCatalog.E2B
        // Ist die App beim letzten Laden abgestürzt (z. B. Modell zu groß)? Dann zurück zu E2B.
        prefs.getString("lade_modell", null)?.let { crashedId ->
            val title = allModels.firstOrNull { it.id == crashedId }?.title ?: crashedId.removePrefix("custom:")
            crashNotice = "Beim letzten Laden von $title ist die App abgestürzt – vermutlich ist das Modell zu groß " +
                "für dieses Tablet. Es ist wieder Gemma 4 E2B ausgewählt."
            prefs.edit().remove("lade_modell").putString("modell", ModelCatalog.E2B.id).apply()
            selectedModel = ModelCatalog.E2B
        }
    }

    fun dismissCrashNotice() {
        crashNotice = null
    }

    /** Beim Start und nach Rückkehr aus den Einstellungen: Speicherort prüfen, alte Dateien umziehen. */
    fun refreshStorage() {
        val wasShared = sharedStorage
        sharedStorage = hasSharedStorage()
        allModels.clear()
        allModels.addAll(ModelCatalog.all + ModelCatalog.customModels(context))
        allModels.forEach { m ->
            val busy = downloads[m.id] is DownloadState.Running || downloads[m.id] is DownloadState.Moving
            if (!busy) downloads[m.id] = if (m.isDownloaded(context)) DownloadState.Done else DownloadState.Missing
        }
        if (sharedStorage) moveInternalModelsToShared()
        // Figuren laden – beim Start und wenn sich der Speicherort geändert hat (dann ziehen sie mit um)
        if (characters.isEmpty() || wasShared != sharedStorage) {
            characters.clear()
            characters.addAll(repo.load())
        }
        refreshRecentChats()
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

    /** Eigene .litertlm-Datei in den Modell-Ordner kopieren (LiteRT-LM braucht einen echten Dateipfad). */
    fun importModel(uri: Uri) {
        if (importState is DownloadState.Moving) return
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "modell.litertlm"
        importName = name
        if (!name.endsWith(".litertlm")) {
            importState = DownloadState.Failed("Das ist keine .litertlm-Datei.")
            return
        }
        val target = File(modelsDir(context), name)
        if (target.exists()) {
            importState = DownloadState.Failed("Diese Datei liegt schon im Modell-Ordner.")
            return
        }
        val total = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
        importState = DownloadState.Moving(0, total)
        viewModelScope.launch {
            val tmp = File(target.parentFile, "$name.part")
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(1 shl 20)
                            var copied = 0L
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                copied += n
                                if (copied - lastReport > (32L shl 20)) {
                                    lastReport = copied
                                    withContext(Dispatchers.Main) { importState = DownloadState.Moving(copied, total) }
                                }
                            }
                        }
                    }
                    if (!tmp.renameTo(target)) error("Datei konnte nicht gespeichert werden")
                }
                importState = null
                importName = null
                refreshStorage()
            } catch (e: Exception) {
                tmp.delete()
                importState = DownloadState.Failed(e.message ?: "Import fehlgeschlagen")
            }
        }
    }

    fun dismissImportError() {
        importState = null
        importName = null
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
        // Eigene Modelle verschwinden ganz aus der Liste
        if (model.url.isEmpty()) {
            allModels.remove(model)
            downloads.remove(model.id)
            if (selectedModel == model) select(ModelCatalog.E2B)
        }
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
        repo.deleteChat(id)
        recentChats.removeAll { it.characterId == id }
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

    /** true = der Verlauf im Modell passt nicht mehr zur Anzeige (Variante gewechselt, Chat geladen …) */
    private var needsRebuild = true

    /** Öffnet den Chat mit einer Figur; ein gespeichertes Gespräch geht dort weiter, wo es aufgehört hat. */
    fun openChat(c: Character) {
        stop()
        chatCharacter = c
        messages.clear()
        val saved = repo.loadChat(c.id)
        if (saved != null && saved.messages.isNotEmpty()) messages.addAll(saved.messages)
        else if (c.greeting.isNotBlank()) messages += ChatMessage.bot(c.greeting)
        needsRebuild = true
        navigate(Screen.Chat(c.id))
        prepareConversation()
    }

    /** Neues Gespräch mit derselben Figur – das alte wird gelöscht. */
    fun restartChat() {
        val c = chatCharacter ?: return
        stop()
        repo.deleteChat(c.id)
        refreshRecentChats()
        messages.clear()
        if (c.greeting.isNotBlank()) messages += ChatMessage.bot(c.greeting)
        needsRebuild = true
        prepareConversation()
    }

    fun changeTemperature(value: Double) {
        temperature = value
        prefs.edit().putFloat("kreativitaet", value.toFloat()).apply()
        needsRebuild = true
    }

    /** Nochmal versuchen, z. B. nach Modellwechsel oder Fehler */
    fun retryChat() = prepareConversation()

    /** Lädt bei Bedarf das Modell und baut das Gespräch aus dem Verlauf auf. */
    private fun prepareConversation() {
        val c = chatCharacter ?: return
        val model = selectedModel
        if (downloads[model.id] != DownloadState.Done) {
            engineState = EngineState.Failed("${model.title} ist noch nicht heruntergeladen.")
            return
        }
        chatJob?.cancel()
        preparing = true
        val history = messages.toList()
        chatJob = viewModelScope.launch {
            try {
                ensureModelLoaded(model)
                rebuild(c, history)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                engineState = EngineState.Failed(e.message ?: "Modell konnte nicht geladen werden")
            } finally {
                preparing = false
            }
        }
    }

    private suspend fun ensureModelLoaded(model: ModelInfo) {
        val state = engineState
        if (state is EngineState.Ready && state.model == model) return
        engineState = EngineState.Loading(model.title)
        val start = SystemClock.elapsedRealtime()
        // Merken, was gerade lädt – stürzt die App dabei ab, warnt sie beim nächsten Start
        prefs.edit().putString("lade_modell", model.id).commit()
        val acc = try {
            engine.load(model.file(context).absolutePath)
        } finally {
            prefs.edit().remove("lade_modell").apply()
        }
        engineState = EngineState.Ready(model, acc, (SystemClock.elapsedRealtime() - start) / 1000.0)
        needsRebuild = true
    }

    /**
     * Gibt dem Modell den Verlauf neu mit. Sehr lange Gespräche werden dabei vorne gekürzt,
     * damit sie ins Gedächtnis des Modells passen – die Figur vergisst dann die ältesten Nachrichten.
     */
    private suspend fun rebuild(c: Character, history: List<ChatMessage>) {
        val kept = ArrayDeque<ChatMessage>()
        var chars = 0
        for (m in history.asReversed()) {
            if (chars + m.text.length > HISTORY_CHARS && kept.isNotEmpty()) break
            kept.addFirst(m)
            chars += m.text.length
        }
        withContext(Dispatchers.IO) {
            engine.startConversation(PromptBuilder.build(c), kept.map { it.fromUser to it.text }, temperature)
        }
        needsRebuild = false
        historyChars = chars
    }

    /** Ungefähre Länge des Verlaufs im Modell (Zeichen) – zum rechtzeitigen Kürzen */
    private var historyChars = 0

    /** true, sobald Modell geladen und Gespräch bereit ist */
    val chatReady get() = engineState is EngineState.Ready && !preparing

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || isGenerating || !chatReady) return
        val historyBefore = messages.toList()
        messages += ChatMessage.user(input)
        messages += ChatMessage.bot("")
        generateInto(messages.lastIndex, input, historyBefore)
    }

    /** Neue Variante für die letzte Antwort der Figur. */
    fun regenerate() {
        if (isGenerating || !chatReady || messages.size < 2) return
        val last = messages.last()
        val userMsg = messages[messages.lastIndex - 1]
        if (last.fromUser || !userMsg.fromUser) return
        val historyBefore = messages.subList(0, messages.lastIndex - 1).toList()
        messages[messages.lastIndex] = last.copy(variants = last.variants + "", selected = last.variants.size, stats = null)
        needsRebuild = true // die alte Antwort steckt noch im Modell
        generateInto(messages.lastIndex, userMsg.text, historyBefore)
    }

    /** Zwischen den Varianten der letzten Antwort blättern (-1 = zurück, +1 = vor). */
    fun selectVariant(delta: Int) {
        if (isGenerating || messages.isEmpty()) return
        val last = messages.last()
        if (last.fromUser) return
        val next = (last.selected + delta).coerceIn(0, last.variants.lastIndex)
        if (next == last.selected) return
        messages[messages.lastIndex] = last.copy(selected = next, stats = null)
        needsRebuild = true // das Modell kennt nur die zuletzt erzeugte Variante
        saveChat()
    }

    private fun generateInto(index: Int, input: String, historyBefore: List<ChatMessage>) {
        val c = chatCharacter ?: return
        isGenerating = true
        viewModelScope.launch {
            var firstChunkAt = 0L
            val reply = StringBuilder()
            try {
                // Verlauf neu aufbauen, wenn er nicht mehr stimmt oder zu lang wird
                if (needsRebuild || historyChars + input.length > HISTORY_CHARS * 2) {
                    rebuild(c, historyBefore)
                }
                val start = SystemClock.elapsedRealtime()
                engine.send(input).collect { chunk ->
                    if (firstChunkAt == 0L) firstChunkAt = SystemClock.elapsedRealtime()
                    reply.append(chunk)
                    if (index < messages.size) messages[index] = messages[index].withText(reply.toString())
                }
                val end = SystemClock.elapsedRealtime()
                historyChars += input.length + reply.length
                if (index < messages.size) {
                    messages[index] = messages[index].withText(reply.toString().trim())
                        .copy(stats = formatStats(start, firstChunkAt, end))
                }
            } catch (e: Exception) {
                needsRebuild = true
                if (index < messages.size) {
                    messages[index] = messages[index].withText(
                        reply.toString().ifEmpty { "⚠ ${e.message ?: "Fehler beim Antworten"}" }
                    )
                }
            } finally {
                isGenerating = false
                saveChat()
            }
        }
    }

    fun stop() {
        // Nach einem Abbruch steckt eine halbe Antwort im Modell – vor der nächsten Nachricht neu aufbauen
        if (isGenerating) needsRebuild = true
        engine.stop()
    }

    // ---------- Figur mit KI erstellen ----------

    /**
     * Lässt das Modell aus Stichpunkten ein Profil schreiben. [onProgress] bekommt die Zahl der
     * bisher geschriebenen Zeichen. Das Chat-Gespräch wird danach beim nächsten Senden neu aufgebaut.
     */
    suspend fun generateProfile(notes: String, base: Character, onProgress: (Int) -> Unit): Character {
        val model = selectedModel
        require(downloads[model.id] == DownloadState.Done) { "${model.title} ist noch nicht heruntergeladen (Seite \"Modelle\")." }
        check(!isGenerating) { "Es läuft gerade noch eine Antwort." }
        if (preparing) chatJob?.join()
        ensureModelLoaded(model)
        withContext(Dispatchers.IO) {
            engine.startConversation(ProfileGenerator.SYSTEM_PROMPT, emptyList(), 0.8)
        }
        needsRebuild = true
        val answer = StringBuilder()
        try {
            engine.send(ProfileGenerator.prompt(notes)).collect { chunk ->
                answer.append(chunk)
                onProgress(answer.length)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            engine.stop() // Editor verlassen: Modell nicht weiterschreiben lassen
            throw e
        }
        return ProfileGenerator.parse(answer.toString(), base)
    }

    // ---------- Gespeicherte Chats ----------

    private fun refreshRecentChats() {
        recentChats.clear()
        recentChats.addAll(repo.recentChats().filter { chat -> characters.any { it.id == chat.characterId } })
    }

    private fun saveChat() {
        val c = chatCharacter ?: return
        val chat = SavedChat(c.id, System.currentTimeMillis(), messages.toList())
        if (chat.messages.none { it.fromUser }) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveChat(chat) }
            recentChats.removeAll { it.characterId == c.id }
            recentChats.add(0, chat)
        }
    }

    private fun formatStats(start: Long, firstChunk: Long, end: Long): String {
        val total = (end - start) / 1000.0
        if (firstChunk == 0L) return "%.1f s".format(total)
        val first = (firstChunk - start) / 1000.0
        return "erstes Wort nach %.1f s · %.1f s gesamt".format(first, total)
    }

    private companion object {
        /** Wie viel Verlauf das Modell mitbekommt – grob 2500 Tokens, passt in 4096 mit Platz für Antworten */
        const val HISTORY_CHARS = 8000
    }

    override fun onCleared() {
        engine.close()
    }
}
