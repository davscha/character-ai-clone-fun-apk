package de.davscha.characterfun.data

import org.json.JSONObject

/** Spiele im Raum. Texte sind deutsche Schlüssel für tr(). */
enum class RoomGameType(val emoji: String, val label: String, val description: String) {
    QUIZ("🏆", "Quiz-Abend", "Eine Figur ist Quizmaster, alle raten mit – mit Punkte-Tabelle."),
    DETECTIVE("🕵️", "Wer war's?", "Eine Figur ist heimlich der Täter. Befrag alle und überführe sie."),
}

/**
 * Laufendes Raum-Spiel. [hostId] = Quizmaster, [culpritId] = Täter (kennt nur die App und der Täter selbst),
 * [scores]: Id der Figur bzw. "" für dich -> Punkte.
 */
data class RoomGameState(
    val type: RoomGameType,
    val hostId: String = "",
    val culpritId: String = "",
    val case: String = "",
    val scores: Map<String, Int> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("spiel", type.name)
        .put("quizmaster", hostId)
        .put("taeter", culpritId)
        .put("fall", case)
        .put("punkte", JSONObject().apply { scores.forEach { (k, v) -> put(k, v) } })

    companion object {
        fun fromJson(o: JSONObject?): RoomGameState? {
            val t = o?.optString("spiel")?.let { n -> RoomGameType.entries.firstOrNull { it.name == n } } ?: return null
            val p = o.optJSONObject("punkte") ?: JSONObject()
            return RoomGameState(
                t, o.optString("quizmaster"), o.optString("taeter"), o.optString("fall"),
                p.keys().asSequence().associateWith { p.optInt(it) },
            )
        }
    }
}

object RoomGames {
    /** Sprecher-Id für Hinweise der App (Erzähler) */
    const val NARRATOR = "#"

    private val cases = listOf(
        "Wer hat das letzte Stück Kuchen gegessen?",
        "Wer hat die Vase im Flur zerbrochen?",
        "Wer hat den Schlüssel zur Schatzkiste versteckt?",
        "Wer hat heimlich die Uhr um eine Stunde vorgestellt?",
        "Wer hat das Lieblingsbuch des Hausherrn mit Kaffee bekleckert?",
        "Wer hat die Geburtstagsüberraschung verraten?",
        "Wer hat alle Socken aus der Wäsche verschwinden lassen?",
        "Wer hat den Goldfisch in die Badewanne gesetzt?",
        "Wer hat die Kekse gegen Hundekekse ausgetauscht?",
        "Wer hat das Licht im Leuchtturm ausgeschaltet?",
    )

    fun randomCase() = cases.random()

    /** Zusätzliche Regeln für [c] im Systemprompt */
    fun rules(g: RoomGameState, c: Character, name: (String) -> String): String = when (g.type) {
        RoomGameType.QUIZ -> if (c.id == g.hostId) """
            Ihr spielt einen Quiz-Abend, und du bist der Quizmaster.
            Sollst du eine neue Frage stellen: Stell genau eine Quizfrage mit kurzer, eindeutiger Antwort (Allgemeinwissen, abwechslungsreich, nicht zu schwer). Verrate die Lösung nicht.
            Sollst du auflösen: Nenne die richtige Lösung, sag wer richtig lag, und schreibe am Ende eine eigene Zeile genau in dieser Form:
            Punkte: Name, Name
            Dort stehen nur die Namen derer, die richtig lagen (der Nutzer heißt dort „Nutzer“). Lag niemand richtig, schreibe „Punkte: keiner“.
        """.trimIndent() else """
            Ihr spielt einen Quiz-Abend. Quizmaster ist ${name(g.hostId)}.
            Stellt ${name(g.hostId)} eine Frage, antworte kurz mit deinem Tipp – in deiner Rolle. Du darfst auch mal danebenliegen, wenn das zu dir passt.
        """.trimIndent()
        RoomGameType.DETECTIVE -> if (c.id == g.culpritId) """
            Ihr spielt „Wer war's?“. Der Fall: ${g.case}
            Du warst es – heimlich! Streite es ab und lenk den Verdacht ruhig auf andere. Aber lüg nicht perfekt: Verplappere dich ab und zu ein kleines bisschen oder werde nervös, damit man dich überführen kann.
            Gib es erst zu, wenn man dich direkt beschuldigt und es dir gesagt wird.
        """.trimIndent() else """
            Ihr spielt „Wer war's?“. Der Fall: ${g.case}
            Du warst es nicht. Du hast aber eine kleine Beobachtung gemacht, die auf ${name(g.culpritId)} hindeutet. Erzähl sie nur, wenn man dich gezielt fragt, und nur vage – nenne den Namen nicht direkt.
            Du darfst auch mal jemand Falschen verdächtigen.
        """.trimIndent()
    }

    /** Wer laut Quizmaster richtig lag: Ids der Figuren bzw. "" für dich */
    fun parseScores(text: String, members: List<Character>, userNames: List<String>): List<String> {
        val line = text.lines().lastOrNull { it.contains("Punkte", ignoreCase = true) || it.contains("Points", true) || it.contains("Puntos", true) }
            ?: return emptyList()
        val after = line.substringAfter(':', "")
        val hits = members.filter { after.contains(it.name, ignoreCase = true) || after.contains(it.name.substringAfterLast(' '), true) }.map { it.id }
        val user = userNames.any { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(after) }
        return hits + if (user) listOf("") else emptyList()
    }
}
