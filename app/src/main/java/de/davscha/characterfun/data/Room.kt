package de.davscha.characterfun.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Eine Nachricht im Gruppenchat. [speakerId] leer = von dir, sonst die Id der Figur. */
data class RoomMessage(val speakerId: String, val text: String, val image: String = "") {
    val fromUser get() = speakerId.isEmpty()
    /** Hinweis der App, z. B. Spielstart */
    val isNarrator get() = speakerId == RoomGames.NARRATOR

    fun toJson(): JSONObject = JSONObject().put("sprecher", speakerId).put("text", text).put("bild", image)

    companion object {
        fun fromJson(o: JSONObject) = RoomMessage(o.optString("sprecher"), o.optString("text"), o.optString("bild"))
    }
}

/** Ein Raum: mehrere Figuren in einem Chat. */
data class Room(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val memberIds: List<String> = emptyList(),
    /** Wo sich alle treffen (optional) */
    val scenario: String = "",
    val messages: List<RoomMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Laufendes Spiel (null = keins) */
    val game: RoomGameState? = null,
    /** Ort aus dem Fundus (leer = keiner) */
    val locationId: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("figuren", JSONArray(memberIds))
        .put("szene", scenario)
        .put("nachrichten", JSONArray(messages.map { it.toJson() }))
        .put("geaendert", updatedAt)
        .apply { game?.let { put("spiel", it.toJson()) } }
        .put("ort", locationId)

    companion object {
        fun fromJson(o: JSONObject): Room {
            val members = o.optJSONArray("figuren") ?: JSONArray()
            val msgs = o.optJSONArray("nachrichten") ?: JSONArray()
            return Room(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                name = o.optString("name"),
                memberIds = List(members.length()) { members.optString(it) },
                scenario = o.optString("szene"),
                messages = List(msgs.length()) { RoomMessage.fromJson(msgs.getJSONObject(it)) },
                updatedAt = o.optLong("geaendert"),
                game = RoomGameState.fromJson(o.optJSONObject("spiel")),
                locationId = o.optString("ort"),
            )
        }
    }
}
