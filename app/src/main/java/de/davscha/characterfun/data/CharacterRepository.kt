package de.davscha.characterfun.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import de.davscha.characterfun.model.hasSharedStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Speichert alle Figuren in einer JSON-Datei (figuren.json), Avatarbilder im Ordner Avatare.
 * Mit der Erlaubnis "Zugriff auf alle Dateien" liegt beides in /CharacterFun und übersteht
 * Neuinstallationen – genau wie die Modelle.
 */
class CharacterRepository(private val context: Context) {

    private val internalDir get() = context.filesDir
    private val sharedDir get() = File(Environment.getExternalStorageDirectory(), "CharacterFun")
    private val dataDir: File get() = (if (hasSharedStorage()) sharedDir else internalDir).apply { mkdirs() }

    private val file get() = File(dataDir, "figuren.json")
    val avatarDir: File get() = File(dataDir, "Avatare").apply { mkdirs() }

    fun avatar(character: Character): File? =
        character.avatarFile.takeIf { it.isNotEmpty() }?.let { File(avatarDir, it) }?.takeIf { it.exists() }

    /** Lädt alle Figuren; beim allerersten Start kommen die Beispiel-Figuren hinein. */
    fun load(): List<Character> {
        moveInternalToShared()
        val f = file
        if (!f.exists()) {
            save(SampleCharacters.all)
            return SampleCharacters.all
        }
        return runCatching {
            val arr = JSONObject(f.readText()).getJSONArray("figuren")
            List(arr.length()) { Character.fromJson(arr.getJSONObject(it)) }
                // Beispiel-Figuren aus älteren Versionen bekommen ihr Geschlecht nachgetragen
                .map { c -> if (c.builtIn && c.gender == Gender.UNKNOWN) c.copy(gender = SampleCharacters.all.firstOrNull { it.id == c.id }?.gender ?: Gender.UNKNOWN) else c }
        }.getOrElse { SampleCharacters.all }
    }

    fun save(characters: List<Character>) {
        val json = JSONObject()
            .put("version", 1)
            .put("figuren", JSONArray(characters.map { it.toJson() }))
        // Erst in eine Hilfsdatei schreiben, dann umbenennen – so geht bei einem Absturz nichts kaputt
        val tmp = File(dataDir, "figuren.json.tmp")
        tmp.writeText(json.toString(2))
        tmp.renameTo(file)
    }

    /** Kopiert ein gewähltes Bild als kleines JPEG in den Avatar-Ordner. Gibt den Dateinamen zurück. */
    fun importAvatar(uri: Uri, characterId: String): String? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val side = 512
        val scale = side.toFloat() / minOf(bitmap.width, bitmap.height)
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true,
        )
        // Quadratisch zuschneiden (Mitte), passt zum runden Avatar
        val x = (scaled.width - side).coerceAtLeast(0) / 2
        val y = (scaled.height - side).coerceAtLeast(0) / 2
        val square = Bitmap.createBitmap(scaled, x, y, minOf(side, scaled.width), minOf(side, scaled.height))
        val name = "$characterId-${System.currentTimeMillis()}.jpg"
        File(avatarDir, name).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        name
    }.getOrNull()

    // ---------- Figuren teilen: eine .json-Datei mit eingebettetem Bild ----------

    /** Figur samt Avatar als JSON (ohne Id) */
    private fun characterJson(c: Character): JSONObject {
        val json = JSONObject()
            .put("format", "characterfun-figur")
            .put("version", 1)
            .put("figur", c.toJson().apply { remove("id"); remove("builtIn"); remove("avatarFile") })
        avatar(c)?.let { json.put("bild", android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)) }
        return json
    }

    /** Figur aus JSON – bekommt eine neue Id und gilt als eigene Figur */
    private fun characterFromJson(json: JSONObject): Character {
        var c = Character.fromJson(json.getJSONObject("figur").put("id", "")).copy(builtIn = false)
        json.optString("bild").takeIf { it.isNotEmpty() }?.let { b64 ->
            val name = "${c.id}-${System.currentTimeMillis()}.jpg"
            File(avatarDir, name).writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
            c = c.copy(avatarFile = name)
        }
        return c
    }

    /** Schreibt die Figur samt Avatar in die gewählte Datei. */
    fun exportCharacter(c: Character, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(characterJson(c).toString(2).toByteArray()) }
            ?: error("Datei konnte nicht geschrieben werden")
    }

    /** Raum samt Figuren (mit Avatar) und Verlauf in eine Datei – Bilder im Verlauf bleiben draußen */
    fun exportRoom(room: Room, members: List<Character>, uri: Uri) {
        val json = JSONObject()
            .put("format", "characterfun-raum")
            .put("version", 1)
            .put("raum", room.copy(messages = room.messages.map { it.copy(image = "") }).toJson())
            .put("figuren", JSONArray(members.map { m -> characterJson(m).put("alteId", m.id) }))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
            ?: error("Datei konnte nicht geschrieben werden")
    }

    /**
     * Liest einen exportierten Raum. Figuren, die es hier schon gibt (gleiche Id oder gleicher Name),
     * werden wiederverwendet, die anderen neu angelegt. Gibt den Raum (neue Id) und die neuen Figuren zurück.
     */
    fun importRoom(uri: Uri, existing: List<Character>): Pair<Room, List<Character>> {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Datei konnte nicht gelesen werden")
        val json = JSONObject(text)
        require(json.optString("format") == "characterfun-raum") { "Das ist kein Character-Fun-Raum." }
        val idMap = mutableMapOf<String, String>()
        val added = mutableListOf<Character>()
        val arr = json.optJSONArray("figuren") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val old = o.optString("alteId")
            val name = o.optJSONObject("figur")?.optString("name").orEmpty()
            val match = existing.firstOrNull { it.id == old } ?: existing.firstOrNull { name.isNotBlank() && it.name.equals(name, true) }
            if (match != null) idMap[old] = match.id
            else characterFromJson(o).also { added += it; idMap[old] = it.id }
        }
        val r = Room.fromJson(json.getJSONObject("raum"))
        val room = r.copy(
            id = java.util.UUID.randomUUID().toString(),
            memberIds = r.memberIds.map { idMap[it] ?: it },
            messages = r.messages.map { m -> if (m.speakerId in idMap) m.copy(speakerId = idMap.getValue(m.speakerId)) else m },
            updatedAt = System.currentTimeMillis(),
        )
        return room to added
    }

    /** Liest eine exportierte Figur ein – sie bekommt eine neue Id und gilt als eigene Figur. */
    fun importCharacter(uri: Uri): Character {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Datei konnte nicht gelesen werden")
        val json = JSONObject(text)
        require(json.optString("format") == "characterfun-figur") { "Das ist keine Character-Fun-Figur." }
        return characterFromJson(json)
    }

    // ---------- Fundus (Orte, Kleidung, Stile) und ComfyUI-Vorlage ----------

    val fundusDir: File get() = File(dataDir, "Fundus").apply { mkdirs() }
    private val fundusFile get() = File(dataDir, "fundus.json")
    private val workflowFile get() = File(dataDir, "comfy_workflow.json")

    fun loadFundus(): Fundus {
        val f = fundusFile
        if (!f.exists()) return Fundus.starter.also { saveFundus(it) }
        val fu = runCatching { Fundus.fromJson(JSONObject(f.readText())) }.getOrElse { Fundus.starter }
        // Umstieg auf MOSS Image: Stile in Worten einmalig nachliefern, Disney wird Standard statt der alten LoRA
        val known = fu.styles.map { it.id }.toSet()
        if (Fundus.mossStyles.none { it.id in known }) {
            val old = fu.defaultStyle
            val moved = fu.copy(
                styles = Fundus.mossStyles + fu.styles,
                defaultStyleId = if (old == null || old.lora.isNotBlank()) Fundus.mossStyles.first().id else fu.defaultStyleId,
            )
            saveFundus(moved)
            return moved
        }
        return fu
    }

    fun saveFundus(fundus: Fundus) {
        val tmp = File(dataDir, "fundus.json.tmp")
        tmp.writeText(fundus.toJson().toString(2))
        tmp.renameTo(fundusFile)
    }

    fun fundusImage(name: String): File? = name.takeIf { it.isNotEmpty() }?.let { File(fundusDir, it) }?.takeIf { it.exists() }

    /** Speichert ein Bild von ComfyUI im Fundus-Ordner und gibt den Dateinamen zurück */
    fun saveFundusImage(prefix: String, png: ByteArray): String {
        val name = "$prefix-${System.currentTimeMillis()}.png"
        File(fundusDir, name).writeBytes(png)
        return name
    }

    /** Ein Bild aus dem Fundus-Ordner als Avatar übernehmen. [topFocus] = oberen Teil nehmen (Ganzkörperbild → Gesicht). */
    fun adoptAvatar(file: File, characterId: String, topFocus: Boolean): String? = runCatching {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val side = if (topFocus) (minOf(bmp.width, bmp.height) * 0.55f).toInt() else minOf(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = if (topFocus) (bmp.height * 0.04f).toInt() else (bmp.height - side) / 2
        val square = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, x, y, side, side), 512, 512, true)
        val name = "$characterId-${System.currentTimeMillis()}.jpg"
        File(avatarDir, name).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        name
    }.getOrNull()

    /** Eigener Workflow (API-Format) oder null = eingebaute Vorlage */
    fun loadWorkflow(): String? = workflowFile.takeIf { it.exists() }?.readText()

    fun saveWorkflow(json: String?) {
        if (json == null) workflowFile.delete() else workflowFile.writeText(json)
    }

    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()

    // ---------- Rooms: eine Datei pro Raum in Rooms/ ----------

    private val roomsDir: File get() = File(dataDir, "Rooms").apply { mkdirs() }

    fun loadRooms(): List<Room> =
        roomsDir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> runCatching { Room.fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun saveRoom(room: Room) {
        val tmp = File(roomsDir, "${room.id}.json.tmp")
        tmp.writeText(room.toJson().toString(2))
        tmp.renameTo(File(roomsDir, "${room.id}.json"))
    }

    fun deleteRoom(id: String) {
        File(roomsDir, "$id.json").delete()
    }

    // ---------- Chats: eine Datei pro Figur in Chats/ ----------

    private val chatsDir: File get() = File(dataDir, "Chats").apply { mkdirs() }

    /** Erster Strang: <figur>.json, weitere: <figur>~<strang>.json */
    private fun chatFile(characterId: String, threadId: String = "") =
        File(chatsDir, if (threadId.isBlank()) "$characterId.json" else "$characterId~$threadId.json")

    fun loadChat(characterId: String, threadId: String = ""): SavedChat? = runCatching {
        chatFile(characterId, threadId).takeIf { it.exists() }?.let { SavedChat.fromJson(JSONObject(it.readText())).copy(threadId = threadId) }
    }.getOrNull()

    fun saveChat(chat: SavedChat) {
        val tmp = File(chatsDir, "${chat.key}.json.tmp")
        tmp.writeText(chat.toJson().toString(2))
        tmp.renameTo(chatFile(chat.characterId, chat.threadId))
    }

    fun deleteChat(characterId: String, threadId: String = "") {
        chatFile(characterId, threadId).delete()
    }

    /** Alle Stränge einer Figur löschen */
    fun deleteAllChats(characterId: String) {
        chatsDir.listFiles { f -> f.name == "$characterId.json" || f.name.startsWith("$characterId~") }?.forEach { it.delete() }
    }

    /** Ein Bild für den Chat als verkleinertes JPEG in Chats/Bilder ablegen. Gibt den Pfad zurück. */
    fun importChatImage(uri: Uri): String? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxSide = 1024
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val dir = File(chatsDir, "Bilder").apply { mkdirs() }
        val file = File(dir, "bild-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()

    /** Ein von ComfyUI gemaltes Bild für den Chat speichern; gibt den absoluten Pfad zurück */
    fun saveChatImageBytes(png: ByteArray): String {
        val dir = File(chatsDir, "Bilder").apply { mkdirs() }
        val f = File(dir, "szene-${System.currentTimeMillis()}.png")
        f.writeBytes(png)
        return f.absolutePath
    }

    /** Alle gespeicherten Chats, neueste zuerst – für "Weiter chatten" */
    fun recentChats(): List<SavedChat> =
        chatsDir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> runCatching { SavedChat.fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?.filter { chat -> chat.messages.any { it.fromUser } }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    /** Nach dem Erteilen der Erlaubnis: Figuren aus dem App-Ordner in /CharacterFun übernehmen. */
    private fun moveInternalToShared() {
        if (!hasSharedStorage()) return
        val oldFile = File(internalDir, "figuren.json")
        if (!oldFile.exists()) return
        sharedDir.mkdirs()
        val newFile = File(sharedDir, "figuren.json")
        if (!newFile.exists()) oldFile.copyTo(newFile)
        File(internalDir, "Avatare").listFiles()?.forEach { img ->
            val target = File(avatarDir, img.name)
            if (!target.exists()) img.copyTo(target)
            img.delete()
        }
        File(internalDir, "Chats").listFiles()?.forEach { chat ->
            val target = File(chatsDir, chat.name)
            if (!target.exists()) chat.copyTo(target)
            chat.delete()
        }
        oldFile.delete()
    }
}
