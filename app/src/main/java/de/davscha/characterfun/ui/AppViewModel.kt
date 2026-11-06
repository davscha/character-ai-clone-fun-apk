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
import de.davscha.characterfun.data.ReplySuggestions
import de.davscha.characterfun.engine.Accelerator
import de.davscha.characterfun.engine.ChatEngine
import de.davscha.characterfun.engine.LiteRtEngine
import de.davscha.characterfun.engine.OllamaClient
import de.davscha.characterfun.engine.OllamaEngine
import de.davscha.characterfun.engine.OllamaModel
import de.davscha.characterfun.engine.Speaker
import de.davscha.characterfun.engine.VoiceServerClient
import de.davscha.characterfun.data.VoiceMode
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

/** Ergebnis des Tests beim Stimmen-Server */
sealed interface VoiceServerStatus {
    data object Untested : VoiceServerStatus
    data object Testing : VoiceServerStatus
    data class Ok(val device: String, val voices: List<String>) : VoiceServerStatus
    data class Failed(val message: String) : VoiceServerStatus
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
            ollamaStatus = OllamaStatus.Failed("Bitte die IP-Adresse des PCs eintragen.")
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
                    "Nicht erreichbar – läuft der PC und ist Ollama fürs Netzwerk freigegeben? (${e.message ?: e.javaClass.simpleName})"
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
        if (screen is Screen.Chat) {
            stop()
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

    suspend fun importAvatar(uri: Uri, characterId: String): String? =
        withContext(Dispatchers.IO) { repo.importAvatar(uri, characterId) }

    // ---------- Chat ----------

    /** true = der Verlauf im Modell passt nicht mehr zur Anzeige (Variante gewechselt, Chat geladen …) */
    private var needsRebuild = true

    // ---------- Vorlesen ----------

    /** Id der Nachricht, die gerade vorgelesen wird (null = still) */
    var speakingId by mutableStateOf<String?>(null)
        private set
    private val speaker = Speaker(app) { id ->
        viewModelScope.launch(Dispatchers.Main) {
            // "Fertig"-Meldungen der Android-Stimme nicht über eine laufende geklonte Stimme schreiben
            if (id != null || (cloneJob == null && player == null)) speakingId = id
        }
    }

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
        val sample = c.greeting.ifBlank { "Hallo, ich bin ${c.name.ifBlank { "deine neue Figur" }}." }
        say("probe", sample, c)
    }

    fun stopSpeaking() {
        cloneJob?.cancel()
        cloneJob = null
        player?.run { runCatching { stop() }; release() }
        player = null
        speaker.stop()
        speakingId = null
    }

    // ---------- Geklonte Stimmen (Stimmen-Server auf dem PC) ----------

    private var cloneJob: Job? = null
    private var player: android.media.MediaPlayer? = null

    var voiceHost by mutableStateOf(prefs.getString("stimmen_host", null) ?: prefs.getString("ollama_host", "") ?: "")
        private set
    var voicePort by mutableStateOf(prefs.getInt("stimmen_port", 8020))
        private set
    var voiceStatus by mutableStateOf<VoiceServerStatus>(VoiceServerStatus.Untested)
        private set

    fun changeVoiceHost(host: String) {
        voiceHost = host.trim()
        prefs.edit().putString("stimmen_host", voiceHost).apply()
        voiceStatus = VoiceServerStatus.Untested
    }

    fun changeVoicePort(port: Int) {
        voicePort = port
        prefs.edit().putInt("stimmen_port", port).apply()
        voiceStatus = VoiceServerStatus.Untested
    }

    fun testVoiceServer() {
        if (voiceHost.isBlank()) {
            voiceStatus = VoiceServerStatus.Failed("Bitte die IP-Adresse des PCs eintragen.")
            return
        }
        voiceStatus = VoiceServerStatus.Testing
        viewModelScope.launch {
            voiceStatus = try {
                val info = VoiceServerClient(voiceHost, voicePort).status()
                VoiceServerStatus.Ok(info.device, info.voices)
            } catch (e: Exception) {
                VoiceServerStatus.Failed("Nicht erreichbar – läuft stimmen_server.py auf dem PC? (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    /** Stimmprobe für eine Figur hochladen. Gibt eine Meldung für den Editor zurück. */
    suspend fun uploadVoiceSample(characterId: String, audio: ByteArray): String {
        if (voiceHost.isBlank()) return "Erst in den Einstellungen den Stimmen-Server eintragen."
        return try {
            val seconds = VoiceServerClient(voiceHost, voicePort).upload(characterId, audio)
            "Stimmprobe gespeichert ✓ (%.1f s)".format(seconds)
        } catch (e: Exception) {
            "Hochladen fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Vorlesen: geklonte Stimme vom PC, falls eingestellt – sonst (oder bei Fehler) die Android-Stimme. */
    private fun say(id: String, text: String, c: Character) {
        stopSpeaking()
        val clean = Speaker.cleanForSpeech(text)
        if (clean.isEmpty()) return
        if (c.voiceMode != VoiceMode.KLON || voiceHost.isBlank()) {
            speaker.speak(id, text, c.voicePitch, c.voiceRate)
            return
        }
        speakingId = id
        cloneJob = viewModelScope.launch {
            try {
                val wav = VoiceServerClient(voiceHost, voicePort).speak(clean, c.id, c.voiceExpression)
                val file = File(context.cacheDir, "stimme-$id.wav").apply { writeBytes(wav) }
                player = android.media.MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { if (speakingId == id) speakingId = null }
                    prepare()
                    start()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // PC aus oder keine Stimmprobe – dann eben mit der Android-Stimme
                speakingId = null
                speaker.speak(id, text, c.voicePitch, c.voiceRate)
            }
        }
    }

    // ---------- Bild für die nächste Nachricht ----------

    /** Pfad des Bildes, das mit der nächsten Nachricht mitgeht (null = keins) */
    var pendingImage by mutableStateOf<String?>(null)
        private set

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
    fun openChat(c: Character) {
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
        needsRebuild = true
        navigate(Screen.Chat(c.id))
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
            engineState = EngineState.Failed("Noch kein Ollama-Modell gewählt – in den Einstellungen \"Verbindung testen\" und ein Modell auswählen.")
            return
        }
        if (!useOllama && downloads[model.id] != DownloadState.Done) {
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
        val acc = if (model.url == OLLAMA) {
            try {
                engine.load(model.fileName)
            } catch (e: Exception) {
                throw IllegalStateException("Ollama nicht erreichbar – läuft der PC? (${e.message ?: e.javaClass.simpleName})", e)
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
        val prompt = PromptBuilder.build(c, aboutUser, memories.toList(), summary)
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
        if ((input.isEmpty() && image == null) || isGenerating || suggesting || !chatReady) return
        suggestions.clear()
        stopSpeaking()
        pendingImage = null
        val historyBefore = messages.toList()
        messages += ChatMessage.user(input, image.orEmpty())
        messages += ChatMessage.bot("")
        generateInto(messages.lastIndex, modelText(input, image), historyBefore, image)
    }

    /** Ohne eigenen Text zum Bild fragt die App die Figur nach ihrer Meinung */
    private fun modelText(input: String, image: String?) =
        if (input.isEmpty() && image != null) "Was sagst du zu diesem Bild?" else input

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

    // ---------- Figur mit KI erstellen ----------

    /**
     * Lässt das Modell aus Stichpunkten ein Profil schreiben. [onProgress] bekommt die Zahl der
     * bisher geschriebenen Zeichen. Das Chat-Gespräch wird danach beim nächsten Senden neu aufgebaut.
     */
    suspend fun generateProfile(notes: String, base: Character, onProgress: (Int) -> Unit): Character {
        val model = if (useOllama) ollamaModelInfo ?: error("Noch kein Ollama-Modell gewählt (Seite \"Einstellungen\").") else selectedModel
        require(useOllama || downloads[model.id] == DownloadState.Done) { "${model.title} ist noch nicht heruntergeladen (Seite \"Einstellungen\")." }
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
        val chat = SavedChat(
            c.id, System.currentTimeMillis(), messages.toList(),
            memories = memories.toList(), summary = summary, summarizedUpTo = summarizedUpTo,
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
        return "erstes Wort nach %.1f s · %.1f s gesamt".format(first, total)
    }

    private companion object {
        const val OLLAMA = "ollama"
        const val OLLAMA_DEFAULT_PORT = 11434

        /** Wie viel Verlauf das Modell mitbekommt – grob 2500 Tokens, passt in 4096 mit Platz für Antworten */
        const val HISTORY_CHARS = 8000
    }

    override fun onCleared() {
        player?.release()
        speaker.shutdown()
        internalEngine.close()
        ollamaEngine.close()
    }
}
