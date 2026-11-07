package de.davscha.characterfun.ui

import de.davscha.characterfun.i18n.AppLanguage
import de.davscha.characterfun.i18n.I18n
import de.davscha.characterfun.i18n.tr
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
import de.davscha.characterfun.data.Room
import de.davscha.characterfun.data.AiHelpers
import de.davscha.characterfun.data.Fundus
import de.davscha.characterfun.data.FundusItem
import de.davscha.characterfun.data.FundusKind
import de.davscha.characterfun.data.StyleItem
import de.davscha.characterfun.data.ImagePrompt
import de.davscha.characterfun.engine.ComfyClient
import de.davscha.characterfun.data.Gender
import de.davscha.characterfun.data.UserGender
import de.davscha.characterfun.data.Mood
import de.davscha.characterfun.data.RandomCharacter
import de.davscha.characterfun.data.Writings
import de.davscha.characterfun.data.RoomGameState
import de.davscha.characterfun.data.RoomGameType
import de.davscha.characterfun.data.RoomGames
import de.davscha.characterfun.data.Game
import de.davscha.characterfun.data.GameState
import de.davscha.characterfun.data.Games
import de.davscha.characterfun.data.RoomMessage
import de.davscha.characterfun.data.CharacterRepository
import de.davscha.characterfun.data.PromptBuilder
import de.davscha.characterfun.data.ProfileGenerator
import de.davscha.characterfun.data.ReplySuggestions
import de.davscha.characterfun.engine.Accelerator
import de.davscha.characterfun.engine.ChatEngine
import de.davscha.characterfun.engine.LiteRtEngine
import de.davscha.characterfun.engine.OllamaClient
import de.davscha.characterfun.engine.OllamaEngine
import de.davscha.characterfun.engine.OllamaModel
import de.davscha.characterfun.engine.Speaker
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

/** Ergebnis des Ollama-Verbindungstests */
sealed interface OllamaStatus {
    data object Untested : OllamaStatus
    data object Testing : OllamaStatus
    data class Ok(val version: String, val models: List<OllamaModel>) : OllamaStatus
    data class Failed(val message: String) : OllamaStatus
}

/** Die Seiten der App */
sealed interface Screen {
    data object Gallery : Screen
    data object Models : Screen
    data class Chat(val characterId: String) : Screen
    /** characterId = null: neue Figur */
    data class Editor(val characterId: String?) : Screen
    data class Room(val roomId: String) : Screen
    /** roomId = null: neuer Raum */
    data class RoomEditor(val roomId: String?) : Screen
    data object FundusPage : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val internalEngine: ChatEngine = LiteRtEngine(app)
    private val ollamaEngine: ChatEngine = OllamaEngine { ollamaHost to ollamaPort }

    /** Die gerade aktive KI: auf dem Gerät oder Ollama auf dem PC */
    private val engine: ChatEngine get() = if (useOllama) ollamaEngine else internalEngine

    // ---------- KI-Quelle: Intern oder Ollama ----------

    /** true = Ollama im Heimnetz, false = Gemma auf dem Gerät */
    var useOllama by mutableStateOf(prefs.getString("quelle", "intern") == "ollama")
        private set
    var ollamaHost by mutableStateOf(prefs.getString("ollama_host", "") ?: "")
        private set
    var ollamaPort by mutableStateOf(prefs.getInt("ollama_port", OLLAMA_DEFAULT_PORT))
        private set
    var ollamaModel by mutableStateOf(prefs.getString("ollama_model", "") ?: "")
        private set
    var ollamaStatus by mutableStateOf<OllamaStatus>(OllamaStatus.Untested)
        private set

    /** Das gewählte Ollama-Modell als ModelInfo (url = "ollama", fileName = Modellname) */
    private val ollamaModelInfo: ModelInfo?
        get() = ollamaModel.takeIf { it.isNotBlank() }?.let {
            ModelInfo(id = "ollama:$it", title = it, description = "Ollama auf dem PC", fileName = it, url = OLLAMA, sizeBytes = 0)
        }

    fun setSource(ollama: Boolean) {
        if (ollama == useOllama) return
        stop()
        engine.close()
        useOllama = ollama
        prefs.edit().putString("quelle", if (ollama) "ollama" else "intern").apply()
        engineState = EngineState.Idle
        needsRebuild = true
        if (ollama && ollamaHost.isNotBlank() && ollamaStatus !is OllamaStatus.Ok) testOllama()
    }

    /** Aus dem Chat heraus: Ollama nicht erreichbar → auf das Gerät umschalten und weitermachen */
    fun switchToInternal() {
        setSource(false)
        if (chatCharacter != null) prepareConversation()
    }

    fun changeOllamaHost(host: String) {
        ollamaHost = host.trim()
        prefs.edit().putString("ollama_host", ollamaHost).apply()
        ollamaStatus = OllamaStatus.Untested
    }

    fun changeOllamaPort(port: Int) {
        ollamaPort = port
        prefs.edit().putInt("ollama_port", port).apply()
        ollamaStatus = OllamaStatus.Untested
    }

    fun selectOllamaModel(name: String) {
        if (name == ollamaModel) return
        stop()
        if (useOllama) engine.close()
        ollamaModel = name
        prefs.edit().putString("ollama_model", name).apply()
        if (useOllama) engineState = EngineState.Idle
        needsRebuild = true
    }

    /** Verbindungstest: Ollama erreichbar? Welche Modelle gibt es, und welche können Bilder? */
    fun testOllama() {
        if (ollamaHost.isBlank()) {
            ollamaStatus = OllamaStatus.Failed(tr("Bitte die IP-Adresse des PCs eintragen."))
            return
        }
        ollamaStatus = OllamaStatus.Testing
        viewModelScope.launch {
            ollamaStatus = try {
                val client = OllamaClient(ollamaHost, ollamaPort)
                val version = client.version()
                val models = client.models()
                // Kein (gültiges) Modell gewählt? Dann ein passendes vorschlagen: Gemma 4 E4B, 12B, irgendein Gemma 4, sonst das kleinste
                if (models.isNotEmpty() && models.none { it.name == ollamaModel }) {
                    val pick = models.firstOrNull { it.name.startsWith("gemma4:e4b") }
                        ?: models.firstOrNull { it.name.startsWith("gemma4:12b") }
                        ?: models.firstOrNull { it.name.startsWith("gemma4") }
                        ?: models.minBy { it.sizeBytes }
                    selectOllamaModel(pick.name)
                }
                OllamaStatus.Ok(version, models)
            } catch (e: Exception) {
                OllamaStatus.Failed(
                    tr("Nicht erreichbar – läuft der PC und ist Ollama fürs Netzwerk freigegeben? (%s)", e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }
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
        if (screen is Screen.Chat || screen is Screen.Room) {
            stop()
            roomJob?.cancel()
            stopSpeaking()
        }
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

    /** Alle Räume (Gruppenchats), neueste zuerst */
    val rooms = mutableStateListOf<Room>()

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
        // Sprache der Oberfläche
        I18n.lang = AppLanguage.entries.firstOrNull { it.name == prefs.getString("sprache", null) } ?: AppLanguage.DE
        refreshStorage()
        val savedId = prefs.getString("modell", null)
        selectedModel = allModels.firstOrNull { it.id == savedId } ?: ModelCatalog.E2B
        // Ist die App beim letzten Laden abgestürzt (z. B. Modell zu groß)? Dann zurück zu E2B.
        prefs.getString("lade_modell", null)?.let { crashedId ->
            val title = allModels.firstOrNull { it.id == crashedId }?.title ?: crashedId.removePrefix("custom:")
            crashNotice = tr("Beim letzten Laden von %s ist die App abgestürzt – vermutlich ist das Modell zu groß für dieses Tablet. Es ist wieder Gemma 4 E2B ausgewählt.", title)
            prefs.edit().remove("lade_modell").putString("modell", ModelCatalog.E2B.id).apply()
            selectedModel = ModelCatalog.E2B
        }
    }

    fun changeLanguage(lang: AppLanguage) {
        I18n.lang = lang
        prefs.edit().putString("sprache", lang.name).apply()
        needsRebuild = true // Figuren antworten ab der nächsten Nachricht in der neuen Sprache
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
        rooms.clear()
        rooms.addAll(repo.loadRooms())
        fundus = repo.loadFundus()
        customWorkflow = repo.loadWorkflow() != null
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
                    downloads[m.id] = DownloadState.Failed(tr("Umzug fehlgeschlagen: %s", e.message))
                }
            }
        }
    }

    fun select(model: ModelInfo) {
        if (model == selectedModel) return
        selectedModel = model
        prefs.edit().putString("modell", model.id).apply()
        // Anderes Modell gewählt: altes freigeben, das neue wird beim nächsten Chat geladen
        if (!useOllama) {
            stop()
            internalEngine.close()
            engineState = EngineState.Idle
        }
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
                downloads[model.id] = DownloadState.Failed(e.message ?: tr("Download fehlgeschlagen"))
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
            importState = DownloadState.Failed(tr("Das ist keine .litertlm-Datei."))
            return
        }
        val target = File(modelsDir(context), name)
        if (target.exists()) {
            importState = DownloadState.Failed(tr("Diese Datei liegt schon im Modell-Ordner."))
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
                importState = DownloadState.Failed(e.message ?: tr("Import fehlgeschlagen"))
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
            internalEngine.close()
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
        // Im offenen Chat gilt die neue Fassung ab der nächsten Nachricht
        if (chatCharacter?.id == c.id) {
            chatCharacter = c
            needsRebuild = true
            val loaded = (engineState as? EngineState.Ready)?.model
            if (loaded != null && loaded != chatModel) prepareConversation() // Lieblingsmodell geändert
        }
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

    /** Figur als Datei exportieren. Gibt eine Meldung zurück. */
    suspend fun exportCharacter(c: Character, uri: Uri): String = try {
        withContext(Dispatchers.IO) { repo.exportCharacter(c, uri) }
        tr("%s exportiert", c.name)
    } catch (e: Exception) {
        tr("Export fehlgeschlagen: %s", e.message)
    }

    /** Figur aus einer Datei importieren. Gibt eine Meldung zurück. */
    suspend fun importCharacter(uri: Uri): String = try {
        val c = withContext(Dispatchers.IO) { repo.importCharacter(uri) }
        saveCharacter(c)
        tr("%s importiert", c.name)
    } catch (e: Exception) {
        tr("Import fehlgeschlagen: %s", e.message)
    }

    suspend fun importAvatar(uri: Uri, characterId: String): String? =
        withContext(Dispatchers.IO) { repo.importAvatar(uri, characterId) }

    // ---------- Chat ----------

    /** true = der Verlauf im Modell passt nicht mehr zur Anzeige (Variante gewechselt, Chat geladen …) */
    private var needsRebuild = true

    // ---------- Vorlesen ----------

    /** Id der Nachricht, die gerade vorgelesen wird (null = still) */
    var speakingId by mutableStateOf<String?>(null)
        private set
    private val speaker = Speaker(
        app,
        onSpeakingChanged = { id -> viewModelScope.launch(Dispatchers.Main) { speakingId = id } },
        onVoicesReady = { list -> viewModelScope.launch(Dispatchers.Main) { voices.clear(); voices.addAll(list) } },
    )

    /** Antworten der Figur automatisch vorlesen */
    var autoRead by mutableStateOf(prefs.getBoolean("vorlesen", false))
        private set

    fun toggleAutoRead() {
        autoRead = !autoRead
        prefs.edit().putBoolean("vorlesen", autoRead).apply()
        if (!autoRead) stopSpeaking()
    }

    /** Nachricht [index] vorlesen – oder anhalten, wenn sie gerade läuft. */
    fun speak(index: Int) {
        val c = chatCharacter ?: return
        val msg = messages.getOrNull(index) ?: return
        val id = "msg-$index"
        if (speakingId == id) {
            stopSpeaking()
            return
        }
        say(id, msg.text, c)
    }

    /** Hörprobe im Editor */
    fun previewVoice(c: Character) {
        val sample = c.greeting.ifBlank { tr("Hallo, ich bin %s.", c.name.ifBlank { tr("deine neue Figur") }) }
        say("probe", sample, c)
    }

    fun stopSpeaking() {
        speaker.stop()
        speakingId = null
    }

    /** Vorlesen mit der Android-Stimme der Figur */
    private fun say(id: String, text: String, c: Character) {
        stopSpeaking()
        speaker.speak(id, text, c.voicePitch, c.voiceRate, c.voiceName)
    }

    /** Alle Stimmen der Android-Sprachausgabe (für die Auswahl im Editor) */
    val voices = mutableStateListOf<de.davscha.characterfun.engine.VoiceInfo>()

    // ---------- Bild für die nächste Nachricht ----------

    /** Pfad des Bildes, das mit der nächsten Nachricht mitgeht (null = keins) */
    var pendingImage by mutableStateOf<String?>(null)
        private set

    /** Ein schon geholtes Bild mit der nächsten Nachricht mitschicken */
    fun usePendingImage(path: String) {
        pendingImage = path
    }

    fun attachImage(uri: android.net.Uri) {
        viewModelScope.launch {
            pendingImage = withContext(Dispatchers.IO) { repo.importChatImage(uri) }
        }
    }

    fun clearPendingImage() {
        pendingImage = null
    }

    /** true = das geladene Modell kann Bilder ansehen */
    val imagesSupported get() = engineState is EngineState.Ready && engine.supportsImages

    // ---------- Erinnerungen ----------

    /** "Über dich" – bekommt jede Figur mit */
    var aboutUser by mutableStateOf(prefs.getString("ueber_mich", "") ?: "")
        private set

    /** Angeheftete Erinnerungen im aktuellen Chat */
    val memories = mutableStateListOf<String>()

    /** Automatische Zusammenfassung älterer Nachrichten im aktuellen Chat */
    var summary by mutableStateOf("")
        private set
    private var summarizedUpTo = 0

    /** true, während Gemma das bisherige Gespräch zusammenfasst */
    var summarizing by mutableStateOf(false)
        private set

    fun changeAboutUser(text: String) {
        aboutUser = text
        prefs.edit().putString("ueber_mich", text).apply()
        needsRebuild = true
    }

    fun addMemory(text: String) {
        val t = text.trim().take(400)
        if (t.isEmpty() || t in memories) return
        memories += t
        needsRebuild = true
        saveChat()
    }

    fun removeMemory(index: Int) {
        if (index !in memories.indices) return
        memories.removeAt(index)
        needsRebuild = true
        saveChat()
    }

    fun clearSummary() {
        summary = ""
        summarizedUpTo = 0
        needsRebuild = true
        saveChat()
    }

    /** Öffnet den Chat mit einer Figur; ein gespeichertes Gespräch geht dort weiter, wo es aufgehört hat. */
    /** [replace] = im Tablet-Layout von einem Chat direkt zum nächsten wechseln (ohne Zurück-Schritt) */
    fun openChat(c: Character, replace: Boolean = false) {
        stop()
        chatCharacter = c
        messages.clear()
        suggestions.clear()
        stopSpeaking()
        pendingImage = null
        val saved = repo.loadChat(c.id)
        if (saved != null && saved.messages.isNotEmpty()) messages.addAll(saved.messages)
        else if (c.greeting.isNotBlank()) messages += ChatMessage.bot(c.greeting)
        memories.clear()
        memories.addAll(saved?.memories.orEmpty())
        summary = saved?.summary.orEmpty()
        summarizedUpTo = saved?.summarizedUpTo ?: 0
        game = saved?.game
        needsRebuild = true
        if (replace && screen is Screen.Chat) screen = Screen.Chat(c.id) else navigate(Screen.Chat(c.id))
        prepareConversation()
    }

    /** Neues Gespräch mit derselben Figur – Verlauf und Zusammenfassung werden gelöscht, angeheftete Erinnerungen bleiben. */
    fun restartChat() {
        val c = chatCharacter ?: return
        stop()
        repo.deleteChat(c.id)
        messages.clear()
        if (c.greeting.isNotBlank()) messages += ChatMessage.bot(c.greeting)
        summary = ""
        summarizedUpTo = 0
        if (memories.isNotEmpty()) saveChat()
        refreshRecentChats()
        game = null
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
    /**
     * Das Modell für den aktuellen Chat: das Lieblingsmodell der Figur, falls heruntergeladen,
     * sonst das aus den Einstellungen.
     */
    val chatModel: ModelInfo
        get() = if (useOllama) ollamaModelInfo ?: selectedModel else chatCharacter?.preferredModelId
            ?.let { id -> allModels.firstOrNull { it.id == id } }
            ?.takeIf { downloads[it.id] == DownloadState.Done }
            ?: selectedModel

    private fun prepareConversation() {
        val c = chatCharacter ?: return
        val model = chatModel
        if (useOllama && ollamaModelInfo == null) {
            engineState = EngineState.Failed(tr("Noch kein Ollama-Modell gewählt – in den Einstellungen \"Verbindung testen\" und ein Modell auswählen."))
            return
        }
        if (!useOllama && downloads[model.id] != DownloadState.Done) {
            engineState = EngineState.Failed(tr("%s ist noch nicht heruntergeladen.", model.title))
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
                engineState = EngineState.Failed(e.message ?: tr("Modell konnte nicht geladen werden"))
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
        val acc = if (model.url == OLLAMA) {
            try {
                engine.load(model.fileName)
            } catch (e: Exception) {
                throw IllegalStateException(tr("Ollama nicht erreichbar – läuft der PC? (%s)", e.message ?: e.javaClass.simpleName), e)
            }
        } else {
            // Merken, was gerade lädt – stürzt die App dabei ab, warnt sie beim nächsten Start
            prefs.edit().putString("lade_modell", model.id).commit()
            try {
                engine.load(model.file(context).absolutePath)
            } finally {
                prefs.edit().remove("lade_modell").apply()
            }
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
        // Was vorne herausfällt und noch nicht zusammengefasst ist, fasst Gemma jetzt zusammen
        val dropped = history.size - kept.size
        if (summarizedUpTo > history.size) summarizedUpTo = history.size // z. B. nach Neu würfeln
        // In Häppchen, damit auch ein sehr langer alter Chat ins Gedächtnis des Modells passt
        while (dropped > summarizedUpTo) {
            var end = summarizedUpTo
            var size = 0
            while (end < dropped && (end == summarizedUpTo || size + history[end].text.length < HISTORY_CHARS)) {
                size += history[end].text.length
                end++
            }
            val before = summarizedUpTo
            summarize(c, history.subList(summarizedUpTo, end), end)
            if (summarizedUpTo == before) break // Zusammenfassen hat nicht geklappt – ohne weiter
        }
        val prompt = PromptBuilder.build(c, aboutUser, memories.toList(), summary, game, moodEnabled)
        withContext(Dispatchers.IO) {
            engine.startConversation(prompt, kept.map { it.fromUser to historyText(it) }, temperature)
        }
        needsRebuild = false
        historyChars = chars
    }

    /** Im neu aufgebauten Verlauf stehen Bilder nur als Hinweis – die Antwort der Figur darauf trägt den Inhalt weiter. */
    private fun historyText(m: ChatMessage) =
        if (m.image.isNotEmpty()) "[schickt ein Bild] ${m.text}".trim() else m.text

    /** Lässt Gemma die herausfallenden Nachrichten in die Zusammenfassung einarbeiten. */
    private suspend fun summarize(c: Character, newMessages: List<ChatMessage>, upTo: Int) {
        summarizing = true
        try {
            withContext(Dispatchers.IO) {
                engine.startConversation(PromptBuilder.SUMMARY_SYSTEM, emptyList(), 0.3)
            }
            val sb = StringBuilder()
            engine.send(PromptBuilder.summaryPrompt(c.name, summary, newMessages)).collect { sb.append(it) }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) {
                summary = text.take(1200)
                summarizedUpTo = upTo
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Zusammenfassen ist nur eine Hilfe – klappt es nicht, geht es ohne weiter
        } finally {
            summarizing = false
        }
    }

    /** Ungefähre Länge des Verlaufs im Modell (Zeichen) – zum rechtzeitigen Kürzen */
    private var historyChars = 0

    /** true, sobald Modell geladen und Gespräch bereit ist */
    val chatReady get() = engineState is EngineState.Ready && !preparing

    fun send(text: String) {
        val input = text.trim()
        val image = pendingImage
        if ((input.isEmpty() && image == null) || isGenerating || suggesting || helperBusy || !chatReady) return
        suggestions.clear()
        stopSpeaking()
        pendingImage = null
        val historyBefore = messages.toList()
        messages += ChatMessage.user(input, image.orEmpty())
        messages += ChatMessage.bot("")
        generateInto(messages.lastIndex, gameText(modelText(input, image)), historyBefore, image)
    }

    // ---------- Spiele ----------

    /** Das laufende Spiel im Chat (null = keins) */
    var game by mutableStateOf<GameState?>(null)
        private set

    /** Wie viele Nachrichten du seit Spielbeginn geschrieben hast (bei 20 Fragen = Fragen) */
    val gameQuestions: Int get() {
        val g = game ?: return 0
        return messages.drop(g.startIndex + 1).count { it.fromUser }
    }

    fun startGame(g: Game) {
        if (!chatReady || isGenerating || suggesting) return
        game = GameState(g, Games.secretFor(g), messages.size)
        needsRebuild = true // die Figur braucht die Spielregeln
        send(tr(g.opening))
    }

    /** Spiel beenden; [reveal] = bei 20 Fragen / Wer bin ich die Lösung zeigen */
    fun endGame(reveal: Boolean = false) {
        val g = game ?: return
        if (isGenerating) stop()
        if (reveal && g.secret.isNotBlank()) messages += ChatMessage.bot("*${tr("Die Lösung war: %s", g.secret)}*")
        game = null
        needsRebuild = true
        saveChat()
    }

    /** Würfeln im Text-Abenteuer – die Zahl würfelt die App, nicht das Modell */
    fun rollDice() = send("🎲 *${tr("würfelt eine %s", (1..6).random())}*")

    /** Die drei Möglichkeiten der letzten Spielleiter-Antwort */
    val gameOptions: List<String> get() {
        if (game?.game != Game.ADVENTURE || isGenerating) return emptyList()
        val last = messages.lastOrNull()?.takeIf { !it.fromUser } ?: return emptyList()
        return Games.options(last.text)
    }

    /** Bei 20 Fragen bekommt das Modell die Nummer der Frage mit (du siehst sie nicht) */
    private fun gameText(input: String): String {
        val g = game ?: return input
        if (g.game != Game.TWENTY_QUESTIONS || messages.size <= g.startIndex + 2) return input
        return "$input\n(${tr("Frage %s von %s", gameQuestions, Games.MAX_QUESTIONS)})"
    }

    /** Ohne eigenen Text zum Bild fragt die App die Figur nach ihrer Meinung */
    private fun modelText(input: String, image: String?) =
        if (input.isEmpty() && image != null) tr("Was sagst du zu diesem Bild?") else input

    // ---------- Nachrichten bearbeiten und löschen ----------

    /**
     * Ändert eine Nachricht. Bei deiner eigenen Nachricht fällt alles danach weg und die Figur
     * antwortet neu; bei einer Antwort der Figur wird nur der Text ersetzt.
     */
    fun editMessage(index: Int, newText: String) {
        val t = newText.trim()
        if (t.isEmpty() || isGenerating || suggesting || index !in messages.indices) return
        val msg = messages[index]
        suggestions.clear()
        if (msg.fromUser) {
            if (!chatReady) return
            val historyBefore = messages.subList(0, index).toList()
            while (messages.size > index) messages.removeAt(messages.lastIndex)
            if (summarizedUpTo > index) summarizedUpTo = index
            messages += ChatMessage.user(t, msg.image)
            messages += ChatMessage.bot("")
            needsRebuild = true
            generateInto(messages.lastIndex, t, historyBefore, msg.image.ifEmpty { null })
        } else {
            messages[index] = msg.withText(t).copy(stats = null)
            needsRebuild = true
            saveChat()
        }
    }

    /** Löscht eine einzelne Nachricht. */
    fun deleteMessage(index: Int) {
        if (isGenerating || suggesting || index !in messages.indices) return
        messages.removeAt(index)
        if (index < summarizedUpTo) summarizedUpTo--
        suggestions.clear()
        needsRebuild = true
        saveChat()
    }

    /** Neue Variante für die letzte Antwort der Figur. */
    fun regenerate() {
        if (isGenerating || suggesting || !chatReady || messages.size < 2) return
        suggestions.clear()
        val last = messages.last()
        val userMsg = messages[messages.lastIndex - 1]
        if (last.fromUser || !userMsg.fromUser) return
        val historyBefore = messages.subList(0, messages.lastIndex - 1).toList()
        messages[messages.lastIndex] = last.copy(variants = last.variants + "", selected = last.variants.size, stats = null)
        needsRebuild = true // die alte Antwort steckt noch im Modell
        generateInto(messages.lastIndex, modelText(userMsg.text, userMsg.image.ifEmpty { null }), historyBefore, userMsg.image.ifEmpty { null })
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

    private fun generateInto(index: Int, input: String, historyBefore: List<ChatMessage>, imagePath: String? = null) {
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
                engine.send(input, imagePath?.takeIf { java.io.File(it).exists() }).collect { chunk ->
                    if (firstChunkAt == 0L) firstChunkAt = SystemClock.elapsedRealtime()
                    reply.append(chunk)
                    if (index < messages.size) messages[index] = messages[index].withText(reply.toString())
                }
                val end = SystemClock.elapsedRealtime()
                historyChars += input.length + reply.length
                if (index < messages.size) {
                    messages[index] = messages[index].withText(reply.toString().trim())
                        .copy(stats = formatStats(start, firstChunkAt, end))
                    if (autoRead) speak(index)
                }
            } catch (e: Exception) {
                needsRebuild = true
                if (index < messages.size) {
                    messages[index] = messages[index].withText(
                        reply.toString().ifEmpty { "⚠ ${e.message ?: tr("Fehler beim Antworten")}" }
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

    // ---------- Antwortvorschläge ----------

    /** Fünf Ideen für deine nächste Nachricht (leer = keine angezeigt) */
    val suggestions = mutableStateListOf<String>()
    var suggesting by mutableStateOf(false)
        private set

    /** Lässt Gemma fünf mögliche nächste Nachrichten für dich schreiben. */
    fun suggestReplies() {
        val c = chatCharacter ?: return
        if (suggesting || isGenerating || !chatReady) return
        suggesting = true
        suggestions.clear()
        // Die letzten Nachrichten reichen – grob 3000 Zeichen
        val recent = ArrayDeque<ChatMessage>()
        var chars = 0
        for (m in messages.asReversed()) {
            if (chars + m.text.length > 3000 && recent.isNotEmpty()) break
            recent.addFirst(m)
            chars += m.text.length
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { engine.startConversation(ReplySuggestions.SYSTEM, emptyList(), 0.9) }
                needsRebuild = true // das Chat-Gespräch wird vor der nächsten Nachricht neu aufgebaut
                val sb = StringBuilder()
                engine.send(ReplySuggestions.prompt(c.name, aboutUser, recent.toList())).collect { sb.append(it) }
                suggestions.addAll(ReplySuggestions.parse(sb.toString()))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keine Vorschläge – nicht schlimm
            } finally {
                suggesting = false
            }
        }
    }

    fun clearSuggestions() = suggestions.clear()

    // ---------- Spaß: Stimmung, Tagebuch, Briefe, Zufallsfigur ----------

    /** Stimmungs-Anzeige: die Figur zeigt ihre Laune als Emoji */
    var moodEnabled by mutableStateOf(prefs.getBoolean("stimmung", true))
        private set

    fun toggleMood() {
        moodEnabled = !moodEnabled
        prefs.edit().putBoolean("stimmung", moodEnabled).apply()
        needsRebuild = true
    }

    /** Aktuelle Laune der Figur (aus ihrer letzten Antwort) */
    val currentMood: String? get() = if (!moodEnabled) null else messages.lastOrNull { !it.fromUser }?.let { Mood.of(it.text) }

    private fun characterSystem(c: Character) = PromptBuilder.build(c, aboutUser, memories.toList(), summary)

    suspend fun writeDiary(): String {
        val c = chatCharacter ?: return ""
        return Mood.strip(askHelper(characterSystem(c), Writings.diaryPrompt(recentMessages(5000), c.name), 0.9))
    }

    suspend fun writeLetter(occasion: String, extra: String): String {
        val c = chatCharacter ?: return ""
        return Mood.strip(askHelper(characterSystem(c), Writings.letterPrompt(occasion, extra), 0.9))
    }

    /** Würfelt eine neue Figur, speichert sie und gibt sie zurück */
    suspend fun createRandomCharacter(onProgress: (Int) -> Unit): Character {
        val made = generateProfile(RandomCharacter.notes(), Character(), onProgress)
        saveCharacter(made)
        return made
    }

    // ---------- KI-Hilfen ----------

    /** true, während eine KI-Hilfe (Stil, Check, Erinnerungen, Geschichte) arbeitet */
    var helperBusy by mutableStateOf(false)
        private set

    /** Irgendein Modell bereit machen – ein schon geladenes wird einfach benutzt. */
    private suspend fun helperModelReady() {
        if (engineState is EngineState.Ready) return
        if (preparing) chatJob?.join()
        if (engineState is EngineState.Ready) return
        val model = defaultModel
        require(useOllama || downloads[model.id] == DownloadState.Done) { tr("%s ist noch nicht heruntergeladen (Seite \"Einstellungen\").", model.title) }
        ensureModelLoaded(model)
    }

    /** Eine einzelne Frage ans Modell, außerhalb des Chats */
    private suspend fun askHelper(system: String, prompt: String, temp: Double = 0.7, imagePath: String? = null): String {
        check(!isGenerating && !roomBusy && !suggesting && !helperBusy) { tr("Es läuft gerade noch eine Antwort.") }
        helperBusy = true
        try {
            helperModelReady()
            withContext(Dispatchers.IO) { engine.startConversation(system, emptyList(), temp) }
            needsRebuild = true // der Chat wird vor der nächsten Nachricht neu aufgebaut
            val sb = StringBuilder()
            engine.send(prompt, imagePath).collect { sb.append(it) }
            return sb.toString().trim()
        } catch (e: kotlinx.coroutines.CancellationException) {
            engine.stop()
            throw e
        } finally {
            helperBusy = false
        }
    }

    private fun recentMessages(maxChars: Int): List<ChatMessage> {
        val recent = ArrayDeque<ChatMessage>()
        var chars = 0
        for (m in messages.asReversed()) {
            if (chars + m.text.length > maxChars && recent.isNotEmpty()) break
            recent.addFirst(m)
            chars += m.text.length
        }
        return recent.toList()
    }

    /** Stil-Knopf: deine Nachricht umschreiben (lustiger, dramatischer …) */
    suspend fun restyle(text: String, style: String): String {
        val c = chatCharacter ?: return text
        val last = messages.lastOrNull { !it.fromUser }?.text
        return askHelper(AiHelpers.STYLE_SYSTEM, AiHelpers.stylePrompt(text, style, c.name, last), 0.9)
            .trim().trim('"', '„', '“').ifBlank { text }
    }

    /** Dein Geschlecht – für richtige Ich-Form und Anrede */
    var userGender by mutableStateOf(Gender.of(prefs.getString("geschlecht", null)).also { UserGender.value = it })
        private set

    fun changeUserGender(g: Gender) {
        userGender = g
        UserGender.value = g
        prefs.edit().putString("geschlecht", g.name).apply()
        needsRebuild = true
    }

    /** Bild in den App-Ordner holen (verkleinert); null = hat nicht geklappt */
    suspend fun importImage(uri: android.net.Uri): String? = withContext(Dispatchers.IO) { repo.importChatImage(uri) }

    /** Die KI schaut sich das Bild an und schreibt daraus deine Nachricht an [targets] */
    suspend fun describeImage(path: String, hint: String, mode: String, targets: List<Character>): String {
        check(engine.supportsImages || engineState !is EngineState.Ready) { tr("Das Modell kann keine Bilder ansehen.") }
        // Das Ende des Gesprächs, damit die Nachricht natürlich anschließt (Einzelchat oder Raum)
        val context = if (screen is Screen.Room) {
            currentRoom?.messages?.takeLast(6)?.joinToString("\n") { m ->
                val who = when { m.fromUser -> "Nutzer"; m.isNarrator -> "Spielleitung"; else -> character(m.speakerId)?.name ?: "?" }
                "$who: ${m.text.take(400)}"
            }.orEmpty()
        } else {
            val name = chatCharacter?.name ?: "Figur"
            recentMessages(1500).joinToString("\n") { m -> "${if (m.fromUser) "Nutzer" else name}: ${Mood.strip(m.text)}" }
        }
        val text = askHelper(AiHelpers.IMAGE_SYSTEM, AiHelpers.imagePrompt(targets, userGender, hint, mode, context), 0.8, path)
        return text.trim().trim('"', '„', '“')
    }

    /** Steckbrief-Check im Editor */
    suspend fun checkProfile(c: Character): String = askHelper(AiHelpers.CHECK_SYSTEM, AiHelpers.checkPrompt(c))

    /** Bis zu drei Vorschläge für Erinnerungen aus dem Gespräch */
    suspend fun suggestMemories(): List<String> {
        val c = chatCharacter ?: return emptyList()
        val answer = askHelper(AiHelpers.MEMORY_SYSTEM, AiHelpers.memoryPrompt(c.name, recentMessages(4000), memories.toList()), 0.5)
        return ReplySuggestions.parse(answer).filter { it !in memories }.take(3)
    }

    /** Das Gespräch als kurze Geschichte */
    suspend fun chatAsStory(): String {
        val c = chatCharacter ?: return ""
        return askHelper(AiHelpers.STORY_SYSTEM, AiHelpers.storyPrompt(c.name, aboutUser, summary, recentMessages(HISTORY_CHARS)), 0.8)
    }

    /** Weiterschreiben: die Figur setzt ihre letzte Antwort fort, ohne dass du etwas schreibst */
    fun continueReply() {
        val c = chatCharacter ?: return
        if (isGenerating || suggesting || helperBusy || !chatReady) return
        val idx = messages.lastIndex
        val last = messages.getOrNull(idx)?.takeIf { !it.fromUser && it.text.isNotBlank() } ?: return
        isGenerating = true
        viewModelScope.launch {
            val base = last.text
            val add = StringBuilder()
            try {
                if (needsRebuild) rebuild(c, messages.toList())
                engine.send("(Erzähl einfach weiter: Setze deine letzte Antwort mit einem neuen Absatz fort, ohne etwas zu wiederholen und ohne auf mich zu warten.)")
                    .collect { chunk ->
                        add.append(chunk)
                        if (idx < messages.size) messages[idx] = messages[idx].withText(base + "\n\n" + add)
                    }
                if (idx < messages.size) messages[idx] = messages[idx].withText((base + "\n\n" + add.toString().trim()).trim()).copy(stats = null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nicht geklappt – die alte Antwort bleibt
            } finally {
                isGenerating = false
                needsRebuild = true // die Anweisung "erzähl weiter" soll nicht im Verlauf bleiben
                saveChat()
            }
        }
    }

    // ---------- Figur mit KI erstellen ----------

    /**
     * Lässt das Modell aus Stichpunkten ein Profil schreiben. [onProgress] bekommt die Zahl der
     * bisher geschriebenen Zeichen. Das Chat-Gespräch wird danach beim nächsten Senden neu aufgebaut.
     */
    suspend fun generateProfile(notes: String, base: Character, onProgress: (Int) -> Unit): Character {
        val model = if (useOllama) ollamaModelInfo ?: error(tr("Noch kein Ollama-Modell gewählt (Seite \"Einstellungen\").")) else selectedModel
        require(useOllama || downloads[model.id] == DownloadState.Done) { tr("%s ist noch nicht heruntergeladen (Seite \"Einstellungen\").", model.title) }
        check(!isGenerating) { tr("Es läuft gerade noch eine Antwort.") }
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

    // ---------- Bilder mit ComfyUI (auf dem PC) ----------

    var comfyHost by mutableStateOf(prefs.getString("comfy_host", null) ?: ollamaHost)
        private set
    var comfyPort by mutableStateOf(prefs.getInt("comfy_port", COMFY_DEFAULT_PORT))
        private set
    /** Ergebnis des letzten Verbindungstests (null = noch nicht getestet) */
    var comfyStatus by mutableStateOf<String?>(null)
        private set
    var comfyOk by mutableStateOf(false)
        private set
    var comfyTesting by mutableStateOf(false)
        private set
    var basePositive by mutableStateOf(prefs.getString("bild_positiv", null) ?: DEFAULT_POSITIVE)
        private set
    var baseNegative by mutableStateOf(prefs.getString("bild_negativ", null) ?: DEFAULT_NEGATIVE)
        private set
    /** true = eigener Workflow importiert, sonst eingebaute Vorlage */
    var customWorkflow by mutableStateOf(false)
        private set
    /** LoRAs, die ComfyUI kennt (nach dem Test) */
    val loras = mutableStateListOf<String>()

    var fundus by mutableStateOf(Fundus())
        private set
    /** Ids von Einträgen, deren Vorschau gerade gemalt wird */
    val previewBusy = mutableStateMapOf<String, Boolean>()
    /** Letzter Fehler beim Malen einer Vorschau (Id -> Text) */
    val previewError = mutableStateMapOf<String, String>()

    private val comfy get() = ComfyClient(comfyHost, comfyPort)
    private val workflowTemplate get() = repo.loadWorkflow() ?: ComfyClient.DEFAULT_WORKFLOW
    val comfyConfigured get() = comfyHost.isNotBlank()
    /** ComfyUI eingetragen UND erreichbar – sonst sind alle Bild-Knöpfe ausgegraut */
    val comfyReady get() = comfyConfigured && comfyOk

    fun changeComfyHost(h: String) {
        comfyHost = h.trim()
        prefs.edit().putString("comfy_host", comfyHost).apply()
        comfyOk = false
        comfyStatus = null
    }

    fun changeComfyPort(p: Int) {
        comfyPort = p
        prefs.edit().putInt("comfy_port", p).apply()
        comfyOk = false
        comfyStatus = null
    }

    fun changeBasePositive(t: String) {
        basePositive = t
        prefs.edit().putString("bild_positiv", t).apply()
    }

    fun changeBaseNegative(t: String) {
        baseNegative = t
        prefs.edit().putString("bild_negativ", t).apply()
    }

    fun testComfy() {
        if (comfyHost.isBlank() || comfyTesting) return
        comfyTesting = true
        viewModelScope.launch {
            try {
                comfyStatus = comfy.test()
                comfyOk = true
                val list = runCatching { comfy.loras() }.getOrDefault(emptyList())
                loras.clear()
                loras.addAll(list)
            } catch (e: Exception) {
                comfyOk = false
                comfyStatus = tr("Nicht erreichbar – läuft ComfyUI mit --listen? (%s)", e.message ?: e.javaClass.simpleName)
            } finally {
                comfyTesting = false
            }
        }
    }

    /** Eigenen Workflow laden – muss im API-Format exportiert sein */
    suspend fun importWorkflow(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val text = repo.readText(uri) ?: return@withContext tr("Datei konnte nicht gelesen werden")
        val o = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return@withContext tr("Das ist keine JSON-Datei.")
        if (o.has("nodes")) return@withContext tr("Bitte in ComfyUI mit „Export (API)“ speichern – dieser Workflow ist im normalen Format.")
        val ok = runCatching { ComfyClient.prepare(org.json.JSONObject(text), "", "", 1, null, 1f, null, null) }.isSuccess
        if (!ok) return@withContext tr("Im Workflow fehlt ein KSampler.")
        repo.saveWorkflow(text)
        customWorkflow = true
        tr("Workflow übernommen.")
    }

    fun resetWorkflow() {
        repo.saveWorkflow(null)
        customWorkflow = false
    }

    /** Malt ein Bild: Basis + Trigger des Stils + [tags]. Gibt die PNG-Bytes zurück. */
    suspend fun paint(tags: String, style: StyleItem? = null, seed: Long = (0..999_999_999L).random(), onProgress: (Int) -> Unit = {}): ByteArray {
        require(comfyConfigured) { tr("ComfyUI ist noch nicht eingerichtet (Einstellungen → Bilder).") }
        val st = style ?: fundus.defaultStyle
        val positive = ImagePrompt.join(basePositive, st?.trigger, tags)
        try {
            return comfy.generate(workflowTemplate, positive, baseNegative, seed, st?.lora, st?.strength ?: 1f, onProgress = onProgress)
        } catch (e: java.io.IOException) {
            // PC aus oder ComfyUI beendet: Knöpfe ausgrauen, bis der nächste Test klappt
            if (e is java.net.ConnectException || e is java.net.SocketTimeoutException || e is java.net.NoRouteToHostException) {
                comfyOk = false
                comfyStatus = tr("Nicht erreichbar – läuft ComfyUI mit --listen? (%s)", e.message ?: e.javaClass.simpleName)
            }
            throw e
        }
    }

    fun fundusImage(name: String) = repo.fundusImage(name)

    private fun updateFundus(f: Fundus) {
        fundus = f
        viewModelScope.launch(Dispatchers.IO) { repo.saveFundus(f) }
    }

    private fun Fundus.list(kind: FundusKind) = if (kind == FundusKind.LOCATION) locations else clothing
    private fun Fundus.with(kind: FundusKind, items: List<FundusItem>) =
        if (kind == FundusKind.LOCATION) copy(locations = items) else copy(clothing = items)

    fun saveFundusItem(kind: FundusKind, item: FundusItem) {
        val list = fundus.list(kind).toMutableList()
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list[i] = item else list.add(item)
        updateFundus(fundus.with(kind, list))
    }

    fun deleteFundusItem(kind: FundusKind, id: String) {
        updateFundus(fundus.with(kind, fundus.list(kind).filter { it.id != id }))
    }

    fun saveStyle(s: StyleItem, makeDefault: Boolean = false) {
        val list = fundus.styles.toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list.add(s)
        updateFundus(fundus.copy(styles = list, defaultStyleId = if (makeDefault || fundus.defaultStyleId.isBlank()) s.id else fundus.defaultStyleId))
    }

    fun deleteStyle(id: String) {
        val list = fundus.styles.filter { it.id != id }
        updateFundus(fundus.copy(styles = list, defaultStyleId = if (fundus.defaultStyleId == id) list.firstOrNull()?.id.orEmpty() else fundus.defaultStyleId))
    }

    /** Vorschau für einen Ort oder ein Kleidungsstück malen (speichert den Eintrag vorher) */
    fun paintPreview(kind: FundusKind, item: FundusItem) {
        if (previewBusy[item.id] == true) return
        saveFundusItem(kind, item)
        previewBusy[item.id] = true
        previewError.remove(item.id)
        viewModelScope.launch {
            try {
                val tags = if (kind == FundusKind.LOCATION) ImagePrompt.locationPreview(item) else ImagePrompt.clothingPreview(item)
                val png = paint(tags)
                val name = withContext(Dispatchers.IO) { repo.saveFundusImage(if (kind == FundusKind.LOCATION) "ort" else "kleidung", png) }
                val current = fundus.list(kind).firstOrNull { it.id == item.id } ?: item
                saveFundusItem(kind, current.copy(preview = name))
            } catch (e: Exception) {
                previewError[item.id] = e.message ?: tr("Fehler")
            } finally {
                previewBusy.remove(item.id)
            }
        }
    }

    fun paintStylePreview(style: StyleItem) {
        if (previewBusy[style.id] == true) return
        saveStyle(style)
        previewBusy[style.id] = true
        previewError.remove(style.id)
        viewModelScope.launch {
            try {
                val png = paint(ImagePrompt.STYLE_PREVIEW, style, seed = 7)
                val name = withContext(Dispatchers.IO) { repo.saveFundusImage("stil", png) }
                val current = fundus.styles.firstOrNull { it.id == style.id } ?: style
                saveStyle(current.copy(preview = name))
            } catch (e: Exception) {
                previewError[style.id] = e.message ?: tr("Fehler")
            } finally {
                previewBusy.remove(style.id)
            }
        }
    }

    /** Gemma macht aus einer Beschreibung (z. B. "gemütliches Café am Abend") Bild-Tags */
    suspend fun tagsFromDescription(description: String, kind: FundusKind?): String {
        val what = when (kind) {
            FundusKind.LOCATION -> "einen Ort (Hintergrund, keine Personen)"
            FundusKind.CLOTHING -> "ein Outfit (nur Kleidung und Accessoires, keine Körpermerkmale)"
            null -> "das Aussehen einer Figur (Haare, Augen, Körper, Merkmale – keine Kleidung)"
        }
        val answer = askHelper(
            "Du schreibst Prompts für einen Anime-Bildgenerator im Danbooru-Tag-Stil. Du antwortest nur mit einer Komma-Liste englischer Tags, ohne Erklärung.",
            "Beschreibung: ${description.trim()}\n\nSchreibe 6 bis 15 englische Danbooru-Tags für $what. Nur die Tags, durch Kommas getrennt.",
            0.4,
        )
        return ImagePrompt.join(answer.lines().joinToString(",") { it.trim().trimStart('-', '*', ' ') }.replace("\"", ""))
    }

    // ---------- Rooms (Gruppenchat) ----------

    var currentRoom by mutableStateOf<Room?>(null)
        private set
    /** true, solange Figuren im Raum antworten */
    var roomBusy by mutableStateOf(false)
        private set
    /** Wer im Raum gerade schreibt */
    var roomSpeakerId by mutableStateOf<String?>(null)
        private set
    private var roomJob: Job? = null

    fun room(id: String) = rooms.firstOrNull { it.id == id }
    fun roomMembers(r: Room) = r.memberIds.mapNotNull { character(it) }

    /** Das Modell für Räume: aus den Einstellungen (Lieblingsmodelle gelten hier nicht) */
    private val defaultModel: ModelInfo get() = if (useOllama) ollamaModelInfo ?: selectedModel else selectedModel

    fun saveRoom(r: Room) {
        val updated = r.copy(updatedAt = System.currentTimeMillis())
        val i = rooms.indexOfFirst { it.id == r.id }
        if (i >= 0) rooms[i] = updated else rooms.add(0, updated)
        if (currentRoom?.id == r.id) currentRoom = updated
        viewModelScope.launch(Dispatchers.IO) { repo.saveRoom(updated) }
    }

    fun deleteRoom(id: String) {
        rooms.removeAll { it.id == id }
        backStack.removeAll { it is Screen.Room && it.roomId == id }
        viewModelScope.launch(Dispatchers.IO) { repo.deleteRoom(id) }
    }

    fun openRoom(r: Room) {
        stop()
        stopSpeaking()
        currentRoom = r
        navigate(Screen.Room(r.id))
    }

    /** Neuer Anfang im Raum: Nachrichten löschen, Figuren und Szene bleiben. */
    fun clearRoom() {
        val r = currentRoom ?: return
        stopRoom()
        saveRoom(r.copy(messages = emptyList(), game = null))
    }

    /** Deine Nachricht – danach antworten alle Figuren einmal, in zufälliger Reihenfolge. */
    fun sendToRoom(text: String) {
        val r = currentRoom ?: return
        val t = text.trim()
        if (t.isEmpty() || roomBusy) return
        currentRoom = r.copy(messages = r.messages + RoomMessage("", t))
        val g = r.game
        val host = g?.takeIf { it.type == RoomGameType.QUIZ }?.let { character(it.hostId) }
        if (host != null) {
            // Quiz: du hast geantwortet – der Quizmaster löst auf und vergibt Punkte
            runRoomTurns(listOf(host to "Alle haben geantwortet. Löse jetzt auf und vergib Punkte.")) { c, reply ->
                if (c.id == host.id) addQuizPoints(reply)
            }
        } else {
            runRoomTurns(roomMembers(r).shuffled().map { it to null })
        }
    }

    // ---------- Spiele im Raum ----------

    fun startRoomGame(type: RoomGameType, hostId: String? = null) {
        val r = currentRoom ?: return
        if (roomBusy) return
        val members = roomMembers(r)
        if (members.size < 2) return
        when (type) {
            RoomGameType.QUIZ -> {
                val host = members.firstOrNull { it.id == hostId } ?: members.random()
                val scores = (members.filter { it.id != host.id }.map { it.id } + "").associateWith { 0 }
                currentRoom = r.copy(
                    game = RoomGameState(type, hostId = host.id, scores = scores),
                    messages = r.messages + RoomMessage(RoomGames.NARRATOR, tr("🏆 Quiz-Abend! Quizmaster ist %s.", host.name)),
                )
                quizNextQuestion()
            }
            RoomGameType.DETECTIVE -> {
                val case = RoomGames.randomCase()
                currentRoom = r.copy(
                    game = RoomGameState(type, culpritId = members.random().id, case = case),
                    messages = r.messages + RoomMessage(RoomGames.NARRATOR, tr("🕵️ Ein neuer Fall: %s Einer von ihnen war's – befrag sie und beschuldige den Täter.", tr(case))),
                )
                runRoomTurns(members.shuffled().map { it to "Reagiere kurz auf den Fall." })
            }
        }
    }

    /** Quiz: der Quizmaster stellt eine Frage, danach geben die anderen Figuren ihren Tipp ab */
    fun quizNextQuestion() {
        val r = currentRoom ?: return
        val g = r.game?.takeIf { it.type == RoomGameType.QUIZ } ?: return
        if (roomBusy) return
        val host = character(g.hostId) ?: return
        val others = roomMembers(r).filter { it.id != host.id }.shuffled()
        runRoomTurns(listOf(host to "Stell jetzt die nächste Quizfrage.") + others.map { it to "Gib deinen Tipp ab." })
    }

    private fun addQuizPoints(reply: String) {
        val r = currentRoom ?: return
        val g = r.game ?: return
        val winners = RoomGames.parseScores(reply, roomMembers(r).filter { it.id != g.hostId }, listOf("Nutzer", "User", "Usuario", "Utilisateur", "du", "you", "tú", "toi"))
        if (winners.isEmpty()) return
        val scores = g.scores.toMutableMap()
        winners.forEach { scores[it] = (scores[it] ?: 0) + 1 }
        currentRoom = r.copy(game = g.copy(scores = scores))
    }

    /** Wer war's: du beschuldigst eine Figur. Danach ist der Fall zu Ende. */
    fun accuse(id: String) {
        val r = currentRoom ?: return
        val g = r.game?.takeIf { it.type == RoomGameType.DETECTIVE } ?: return
        if (roomBusy) return
        val accused = character(id) ?: return
        val culprit = character(g.culpritId) ?: return
        currentRoom = r.copy(messages = r.messages + RoomMessage("", tr("Ich beschuldige %s!", accused.name)))
        val right = accused.id == culprit.id
        val turns = if (right) listOf(culprit to "Du wurdest richtig überführt. Gestehe jetzt – mit Stil.")
        else listOf(
            accused to "Du wurdest zu Unrecht beschuldigt. Empöre dich.",
            culprit to "Jetzt kommt heraus, dass du es warst. Gib es triumphierend zu.",
        )
        runRoomTurns(turns, onDone = {
            val end = if (right) tr("🎉 Fall gelöst! Es war %s.", culprit.name) else tr("Daneben – es war %s.", culprit.name)
            currentRoom?.let { cur -> currentRoom = cur.copy(game = null, messages = cur.messages + RoomMessage(RoomGames.NARRATOR, end)) }
        })
    }

    fun endRoomGame() {
        val r = currentRoom ?: return
        val g = r.game ?: return
        if (roomBusy) stopRoom()
        val note = when (g.type) {
            RoomGameType.QUIZ -> tr("Endstand: %s", scoreLine(g))
            RoomGameType.DETECTIVE -> tr("Fall abgebrochen – es war %s.", character(g.culpritId)?.name ?: "?")
        }
        saveRoom(r.copy(game = null, messages = r.messages + RoomMessage(RoomGames.NARRATOR, note)))
    }

    /** Punkte-Tabelle als Text, beste zuerst */
    fun scoreLine(g: RoomGameState): String = g.scores.entries.sortedByDescending { it.value }
        .joinToString(" · ") { (id, p) -> "${if (id.isEmpty()) tr("Du") else character(id)?.name ?: "?"} $p" }

    /** Eine Figur sagt etwas, ohne dass du schreibst – [speakerId] = null: wer als Nächstes dran ist. */
    fun roomContinue(speakerId: String? = null) {
        val r = currentRoom ?: return
        if (roomBusy) return
        val members = roomMembers(r)
        if (members.isEmpty()) return
        val next = speakerId?.let { id -> members.firstOrNull { it.id == id } } ?: run {
            // Reihum: nach der Figur, die zuletzt gesprochen hat
            val lastIdx = members.indexOfFirst { it.id == r.messages.lastOrNull { m -> !m.fromUser }?.speakerId }
            members[(lastIdx + 1) % members.size]
        }
        runRoomTurns(listOf(next to null))
    }

    fun stopRoom() {
        roomJob?.cancel()
        engine.stop()
    }

    private fun runRoomTurns(
        turns: List<Pair<Character, String?>>,
        onDone: () -> Unit = {},
        onReply: (Character, String) -> Unit = { _, _ -> },
    ) {
        roomBusy = true
        roomJob = viewModelScope.launch {
            try {
                if (engineState !is EngineState.Ready) {
                    if (preparing) chatJob?.join()
                    if (engineState !is EngineState.Ready) ensureModelLoaded(defaultModel)
                }
                for ((c, instruction) in turns) onReply(c, generateRoomTurn(c, instruction))
                onDone()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopp gedrückt oder Raum verlassen
            } catch (e: Exception) {
                // Steht dann als Hinweis über dem Eingabefeld
                engineState = EngineState.Failed(e.message ?: tr("Modell konnte nicht geladen werden"))
            } finally {
                roomBusy = false
                roomSpeakerId = null
                currentRoom?.let { saveRoom(it) }
            }
        }
    }

    /** Eine Figur antwortet im Raum. Aus ihrer Sicht sind alle anderen Nachrichten "von außen" mit Namen davor. */
    private suspend fun generateRoomTurn(c: Character, instruction: String? = null): String {
        val room = currentRoom ?: return ""
        val others = roomMembers(room).filter { it.id != c.id }
        fun nameOf(m: RoomMessage) = when {
            m.fromUser -> "Nutzer"
            m.isNarrator -> "Spielleitung"
            else -> character(m.speakerId)?.name ?: "?"
        }

        // Nur der neuere Teil des Gesprächs, damit er ins Gedächtnis passt
        val recent = ArrayDeque<RoomMessage>()
        var chars = 0
        for (m in room.messages.asReversed()) {
            if (chars + m.text.length > HISTORY_CHARS && recent.isNotEmpty()) break
            recent.addFirst(m)
            chars += m.text.length
        }
        // Aufeinanderfolgende Nachrichten "von außen" zu einem Block zusammenfassen
        val turns = mutableListOf<Pair<Boolean, String>>()
        for (m in recent) {
            val mine = m.speakerId == c.id
            val line = if (mine) m.text else "${nameOf(m)}: ${m.text}"
            val last = turns.lastOrNull()
            if (last != null && last.first == !mine) turns[turns.lastIndex] = last.first to (last.second + "\n" + line)
            else turns += !mine to line
        }
        val outside = if (turns.lastOrNull()?.first == true) turns.removeAt(turns.lastIndex).second else ""
        val sendText = when {
            instruction != null && outside.isNotEmpty() -> "$outside\n\n($instruction)"
            instruction != null -> "($instruction)"
            outside.isNotEmpty() -> outside
            else -> "(Mach weiter.)"
        }

        val rules = room.game?.let { g -> RoomGames.rules(g, c) { id -> character(id)?.name ?: "?" } }.orEmpty()
        val prompt = PromptBuilder.buildForRoom(c, others, room.scenario, aboutUser, rules)
        withContext(Dispatchers.IO) { engine.startConversation(prompt, turns, temperature) }
        needsRebuild = true // ein offener Einzelchat muss danach neu aufgebaut werden

        roomSpeakerId = c.id
        val index = room.messages.size
        currentRoom = room.copy(messages = room.messages + RoomMessage(c.id, ""))
        val reply = StringBuilder()
        engine.send(sendText).collect { chunk ->
            reply.append(chunk)
            currentRoom?.let { r ->
                if (index < r.messages.size) {
                    currentRoom = r.copy(messages = r.messages.toMutableList().also { it[index] = RoomMessage(c.id, reply.toString()) })
                }
            }
        }
        // Falls die Figur doch ihren Namen davorgesetzt hat: weg damit
        val clean = reply.toString().trim().removePrefix("${c.name}:").trim()
        currentRoom?.let { r ->
            if (index < r.messages.size) {
                val list = r.messages.toMutableList()
                if (clean.isEmpty()) list.removeAt(index) else list[index] = RoomMessage(c.id, clean)
                currentRoom = r.copy(messages = list)
            }
        }
        currentRoom?.let { saveRoom(it) }
        // Vorlesen: erst wenn die Figur fertig gesprochen hat, ist die nächste dran
        if (autoRead && clean.isNotEmpty()) {
            say("raum-$index", clean, c)
            kotlinx.coroutines.delay(400)
            while (speakingId != null) kotlinx.coroutines.delay(200)
        }
        return clean
    }

    /** Eine Nachricht im Raum vorlesen (nochmal tippen = aufhören) */
    fun speakRoom(index: Int) {
        val r = currentRoom ?: return
        val m = r.messages.getOrNull(index) ?: return
        val c = character(m.speakerId) ?: return
        val id = "raum-$index"
        if (speakingId == id) { stopSpeaking(); return }
        say(id, m.text, c)
    }

    fun deleteRoomMessage(index: Int) {
        val r = currentRoom ?: return
        if (roomBusy || index !in r.messages.indices) return
        saveRoom(r.copy(messages = r.messages.toMutableList().also { it.removeAt(index) }))
    }

    // ---------- Gespeicherte Chats ----------

    private fun refreshRecentChats() {
        recentChats.clear()
        recentChats.addAll(repo.recentChats().filter { chat -> characters.any { it.id == chat.characterId } })
    }

    private fun saveChat() {
        val c = chatCharacter ?: return
        val chat = SavedChat(
            c.id, System.currentTimeMillis(), messages.toList(),
            memories = memories.toList(), summary = summary, summarizedUpTo = summarizedUpTo, game = game,
        )
        if (chat.messages.none { it.fromUser } && chat.memories.isEmpty()) {
            // Nichts mehr übrig (z. B. alles gelöscht) – gespeicherten Chat entfernen
            repo.deleteChat(c.id)
            recentChats.removeAll { it.characterId == c.id }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveChat(chat) }
            recentChats.removeAll { it.characterId == c.id }
            if (chat.messages.any { it.fromUser }) recentChats.add(0, chat)
        }
    }

    private fun formatStats(start: Long, firstChunk: Long, end: Long): String {
        val total = (end - start) / 1000.0
        if (firstChunk == 0L) return "%.1f s".format(total)
        val first = (firstChunk - start) / 1000.0
        return tr("erstes Wort nach %s s · %s s gesamt", "%.1f".format(first), "%.1f".format(total))
    }

    private companion object {
        const val OLLAMA = "ollama"
        const val OLLAMA_DEFAULT_PORT = 11434

        /** Wie viel Verlauf das Modell mitbekommt – grob 2500 Tokens, passt in 4096 mit Platz für Antworten */
        const val HISTORY_CHARS = 8000
        const val COMFY_DEFAULT_PORT = 8080
        const val DEFAULT_POSITIVE = "masterpiece, highres"
        const val DEFAULT_NEGATIVE = "lowres, photorealistic, photo background, multiple views"
    }

    override fun onCleared() {
        speaker.shutdown()
        internalEngine.close()
        ollamaEngine.close()
    }
}
