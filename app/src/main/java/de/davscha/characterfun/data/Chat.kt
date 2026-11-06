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
) {
    val text: String get() = variants.getOrElse(selected) { "" }

    fun withText(t: String) = copy(variants = variants.toMutableList().also { it[selected] = t })

    fun toJson(): JSONObject = JSONObject()
        .put("vonDir", fromUser)
        .put("varianten", JSONArray(variants))
        .put("gewaehlt", selected)

    companion object {
        fun user(text: String) = ChatMessage(fromUser = true, variants = listOf(text))
        fun bot(text: String) = ChatMessage(fromUser = false, variants = listOf(text))

        fun fromJson(o: JSONObject): ChatMessage {
            val arr = o.optJSONArray("varianten") ?: JSONArray()
            val variants = List(arr.length()) { arr.optString(it) }.ifEmpty { listOf("") }
            return ChatMessage(
                fromUser = o.optBoolean("vonDir"),
                variants = variants,
                selected = o.optInt("gewaehlt").coerceIn(0, variants.lastIndex),
            )
        }
    }
}

/** Das laufende Gespräch mit einer Figur – eine Datei pro Figur in CharacterFun/Chats. */
data class SavedChat(
    val characterId: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
) {
    /** Letzte Nachricht für die "Weiter chatten"-Leiste */
    val preview: String get() = messages.lastOrNull()?.text.orEmpty().replace('\n', ' ')

    fun toJson(): JSONObject = JSONObject()
        .put("figur", characterId)
        .put("geaendert", updatedAt)
        .put("nachrichten", JSONArray(messages.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject): SavedChat {
            val arr = o.optJSONArray("nachrichten") ?: JSONArray()
            return SavedChat(
                characterId = o.optString("figur"),
                updatedAt = o.optLong("geaendert"),
                messages = List(arr.length()) { ChatMessage.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}
