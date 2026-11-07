package de.davscha.characterfun.data

import de.davscha.characterfun.i18n.I18n

/**
 * Baut aus den Feldern einer Figur die Anweisung, die vor jedem Gespräch an das Modell geht.
 * Dazu kommen die Erinnerungen: was die Figur über dich weiß, angeheftete Notizen und
 * die Zusammenfassung älterer Nachrichten. Kurz halten: kleine Modelle vergessen sonst, wer sie sind.
 */
/** Geschlecht des Nutzers (aus den Einstellungen) – gilt für alle Prompts */
object UserGender {
    @Volatile var value: Gender = Gender.UNKNOWN
}

object PromptBuilder {

    fun build(
        c: Character,
        aboutUser: String = "",
        memories: List<String> = emptyList(),
        summary: String = "",
        game: GameState? = null,
        mood: Boolean = false,
        userGender: Gender = UserGender.value,
    ): String = buildString {
        appendLine("Du spielst in einem Rollenspiel-Chat die Figur \"${c.name}\".")
        if (c.shortDescription.isNotBlank()) appendLine(c.shortDescription.trim())
        if (c.gender != Gender.UNKNOWN) appendLine("Geschlecht von ${c.name}: ${c.gender.prompt}.")
        if (c.definition.isNotBlank()) {
            appendLine()
            appendLine(c.definition.trim())
        }
        if (c.longDescription.isNotBlank()) {
            appendLine()
            appendLine("Hintergrund: ${c.longDescription.trim()}")
        }
        if (c.scenario.isNotBlank()) {
            appendLine()
            appendLine("Ausgangssituation des Gesprächs: ${c.scenario.trim()}")
        }
        if (aboutUser.isNotBlank()) {
            appendLine()
            appendLine("Über dein Gegenüber im Chat: ${aboutUser.trim()}")
        }
        if (userGender != Gender.UNKNOWN) {
            appendLine("Dein Gegenüber im Chat ist ${userGender.prompt} – sprich es grammatisch passend an.")
        }
        if (memories.isNotEmpty()) {
            appendLine()
            appendLine("Wichtige Erinnerungen an dieses Gespräch:")
            memories.forEach { appendLine("- ${it.trim()}") }
        }
        if (summary.isNotBlank()) {
            appendLine()
            appendLine("Was bisher im Gespräch geschah: ${summary.trim()}")
        }
        appendLine()
        appendLine("Regeln:")
        appendLine("- Bleib immer in der Rolle von ${c.name} und gib nie zu, eine KI zu sein.")
        appendLine("- ${c.replyLength.rule} Keine Aufzählungen, keine Überschriften.")
        appendLine("- Antworte auf ${I18n.lang.promptName}, außer deine Beschreibung sagt etwas anderes.")
        append("- Handlungen und Gesten schreibst du zwischen Sternchen, z. B. *grinst*.")
        if (mood) {
            appendLine()
            append("- Beende jede Antwort mit genau einem Emoji in eckigen Klammern, das deine aktuelle Stimmung zeigt, z. B. [😄], [😠], [😢] oder [🤔]. Deine Stimmung darf sich im Gespräch ändern.")
        }
        if (game != null) {
            appendLine()
            appendLine()
            appendLine("Spiel (diese Regeln gehen den Regeln oben vor):")
            append(Games.rules(game, c))
        }
    }

    // ---------- Rooms (Gruppenchat) ----------

    /** Anweisung für eine Figur im Gruppenchat: ihre Rolle plus wer sonst noch da ist. */
    fun buildForRoom(c: Character, others: List<Character>, scenario: String, aboutUser: String, gameRules: String = ""): String = buildString {
        append(build(c, aboutUser))
        appendLine()
        appendLine()
        appendLine("Gruppenchat:")
        appendLine("- Außer dir sind im Gespräch: ${others.joinToString { "${it.name} (${it.shortDescription.ifBlank { "eine Figur" }})" }} und der Nutzer.")
        if (scenario.isNotBlank()) appendLine("- Ort und Situation: ${scenario.trim()}")
        appendLine("- Nachrichten der anderen stehen mit ihrem Namen davor, z. B. \"${others.firstOrNull()?.name ?: "Figur"}: …\".")
        appendLine("- Antworte nur als ${c.name}. Schreibe nie für die anderen und setze deinen eigenen Namen nicht vor die Antwort.")
        append("- Du darfst die anderen direkt ansprechen und auf sie reagieren.")
        if (gameRules.isNotBlank()) {
            appendLine()
            appendLine()
            appendLine("Spiel (diese Regeln gehen den Regeln oben vor):")
            append(gameRules)
        }
    }

    // ---------- Zusammenfassung älterer Nachrichten ----------

    const val SUMMARY_SYSTEM =
        "Du fasst Rollenspiel-Gespräche knapp und sachlich zusammen. Du antwortest nur mit der Zusammenfassung."

    fun summaryPrompt(characterName: String, oldSummary: String, messages: List<ChatMessage>): String = buildString {
        if (oldSummary.isNotBlank()) {
            appendLine("Bisherige Zusammenfassung:")
            appendLine(oldSummary.trim())
            appendLine()
        }
        appendLine("Neue Nachrichten:")
        messages.forEach { m -> appendLine("${if (m.fromUser) "Nutzer" else characterName}: ${m.text.trim()}") }
        appendLine()
        append(
            "Schreibe eine aktualisierte Zusammenfassung des ganzen Gesprächs in höchstens sechs Sätzen auf Deutsch. " +
                "Behalte wichtige Fakten, Namen, Orte, Abmachungen und was der Nutzer über sich erzählt hat."
        )
    }
}

/** Fünf Ideen, was du als Nächstes schreiben könntest. */
object ReplySuggestions {

    const val SYSTEM =
        "Du hilfst einem Nutzer in einem Rollenspiel-Chat mit Ideen für seine nächste Nachricht. " +
            "Du antwortest ausschließlich mit einem JSON-Array aus Strings, ohne Erklärung, ohne Markdown."

    fun prompt(characterName: String, aboutUser: String, recent: List<ChatMessage>): String = buildString {
        appendLine("Hier ist das Ende eines Chats zwischen dem Nutzer und der Figur \"$characterName\":")
        appendLine()
        recent.forEach { m -> appendLine("${if (m.fromUser) "Nutzer" else characterName}: ${m.text.trim()}") }
        appendLine()
        if (aboutUser.isNotBlank()) appendLine("Über den Nutzer: ${aboutUser.trim()}")
        appendLine("Schlage genau fünf mögliche nächste Nachrichten des NUTZERS an $characterName vor, auf ${I18n.lang.promptName}, je höchstens 20 Wörter.")
        appendLine("Mach sie unterschiedlich: eine neugierige Frage, eine freche oder witzige Antwort, eine gefühlvolle Antwort,")
        appendLine("eine Handlung zwischen Sternchen (z. B. *schaut sich misstrauisch um*) und eine, die die Geschichte in eine neue Richtung lenkt.")
        append("Format: [\"…\", \"…\", \"…\", \"…\", \"…\"]")
    }

    /** Holt die Vorschläge aus der Antwort – zur Not Zeile für Zeile. */
    fun parse(answer: String): List<String> {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        val list = if (start >= 0 && end > start) {
            runCatching {
                val arr = org.json.JSONArray(answer.substring(start, end + 1))
                List(arr.length()) { arr.optString(it) }
            }.getOrNull()
        } else null
        val raw = list ?: answer.lines().map { it.trim().trimStart('-', '•', '*', ' ').replaceFirst(Regex("^\\d+[.)]\\s*"), "") }
        return raw.map { it.trim().trim('"') }.filter { it.length in 2..300 }.distinct().take(5)
    }
}
