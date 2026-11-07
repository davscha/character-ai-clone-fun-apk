package de.davscha.characterfun.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Eine Nachricht im Chat. Antworten der Figur können mehrere Varianten haben
 * ("neu würfeln"); angezeigt und ans Modell weitergegeben wird die gewählte.
 */
data class ChatMessage(
    val fromUser: Boolean,
    val variants: List<String>,
    val selected: Int = 0,
    /** Nur bei Antworten der Figur: Zeit bis zum ersten Wort und Gesamtzeit */
    val stats: String? = null,
    /** Absoluter Pfad eines mitgeschickten Bildes (oder leer) */
    val image: String = "",
    /** Hinweis der Spielleitung (z. B. Ortswechsel) – mittig angezeigt, fürs Modell eine Regieanweisung */
    val note: Boolean = false,
) {
    val text: String get() = variants.getOrElse(selected) { "" }

    fun withText(t: String) = copy(variants = variants.toMutableList().also { it[selected] = t })

    fun toJson(): JSONObject = JSONObject()
        .put("vonDir", fromUser)
        .put("varianten", JSONArray(variants))
        .put("gewaehlt", selected)
        .put("bild", image)
        .put("hinweis", note)

    companion object {
        fun user(text: String, image: String = "") = ChatMessage(fromUser = true, variants = listOf(text), image = image)
        fun bot(text: String) = ChatMessage(fromUser = false, variants = listOf(text))
        fun note(text: String) = ChatMessage(fromUser = true, variants = listOf(text), note = true)

        fun fromJson(o: JSONObject): ChatMessage {
            val arr = o.optJSONArray("varianten") ?: JSONArray()
            val variants = List(arr.length()) { arr.optString(it) }.ifEmpty { listOf("") }
            return ChatMessage(
                fromUser = o.optBoolean("vonDir"),
                variants = variants,
                selected = o.optInt("gewaehlt").coerceIn(0, variants.lastIndex),
                image = o.optString("bild"),
                note = o.optBoolean("hinweis"),
            )
        }
    }
}

/** Das laufende Gespräch mit einer Figur – eine Datei pro Figur in CharacterFun/Chats. */
data class SavedChat(
    val characterId: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    /** Angeheftete Erinnerungen – bekommt die Figur immer mit */
    val memories: List<String> = emptyList(),
    /** Automatische Zusammenfassung der Nachrichten, die nicht mehr ins Gedächtnis passen */
    val summary: String = "",
    /** So viele Nachrichten (von vorn) stecken schon in der Zusammenfassung */
    val summarizedUpTo: Int = 0,
    /** Laufendes Spiel (null = keins) */
    val game: GameState? = null,
    /** Gewählter Ort und Kleidung aus dem Fundus (leer = keiner / Standard-Outfit) */
    val locationId: String = "",
    val outfitId: String = "",
) {
    /** Letzte Nachricht für die "Weiter chatten"-Leiste */
    val preview: String get() = Mood.strip(messages.lastOrNull()?.text.orEmpty()).replace('\n', ' ')

    fun toJson(): JSONObject = JSONObject()
        .put("figur", characterId)
        .put("geaendert", updatedAt)
        .put("nachrichten", JSONArray(messages.map { it.toJson() }))
        .put("erinnerungen", JSONArray(memories))
        .put("zusammenfassung", summary)
        .put("zusammengefasstBis", summarizedUpTo)
        .apply { game?.let { put("spiel", it.toJson()) } }
        .put("ort", locationId)
        .put("outfit", outfitId)

    companion object {
        fun fromJson(o: JSONObject): SavedChat {
            val arr = o.optJSONArray("nachrichten") ?: JSONArray()
            val mem = o.optJSONArray("erinnerungen") ?: JSONArray()
            return SavedChat(
                characterId = o.optString("figur"),
                updatedAt = o.optLong("geaendert"),
                messages = List(arr.length()) { ChatMessage.fromJson(arr.getJSONObject(it)) },
                memories = List(mem.length()) { mem.optString(it) }.filter { it.isNotBlank() },
                summary = o.optString("zusammenfassung"),
                summarizedUpTo = o.optInt("zusammengefasstBis"),
                game = GameState.fromJson(o.optJSONObject("spiel")),
                locationId = o.optString("ort"),
                outfitId = o.optString("outfit"),
            )
        }
    }
}
