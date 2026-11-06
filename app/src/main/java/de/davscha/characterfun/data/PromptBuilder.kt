package de.davscha.characterfun.data

/**
 * Baut aus den Feldern einer Figur die Anweisung, die vor jedem Gespräch an das Modell geht.
 * Dazu kommen die Erinnerungen: was die Figur über dich weiß, angeheftete Notizen und
 * die Zusammenfassung älterer Nachrichten. Kurz halten: kleine Modelle vergessen sonst, wer sie sind.
 */
object PromptBuilder {

    fun build(
        c: Character,
        aboutUser: String = "",
        memories: List<String> = emptyList(),
        summary: String = "",
    ): String = buildString {
        appendLine("Du spielst in einem Rollenspiel-Chat die Figur \"${c.name}\".")
        if (c.shortDescription.isNotBlank()) appendLine(c.shortDescription.trim())
        if (c.definition.isNotBlank()) {
            appendLine()
            appendLine(c.definition.trim())
        }
        if (c.longDescription.isNotBlank()) {
            appendLine()
            appendLine("Hintergrund: ${c.longDescription.trim()}")
        }
        if (aboutUser.isNotBlank()) {
            appendLine()
            appendLine("Über dein Gegenüber im Chat: ${aboutUser.trim()}")
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
        appendLine("- Antworte wie in einem Chat: höchstens vier kurze Sätze, keine Aufzählungen, keine Überschriften.")
        appendLine("- Antworte auf Deutsch, außer deine Beschreibung sagt etwas anderes.")
        append("- Handlungen und Gesten schreibst du zwischen Sternchen, z. B. *grinst*.")
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
        appendLine("Schlage genau fünf mögliche nächste Nachrichten des NUTZERS an $characterName vor, auf Deutsch, je höchstens 20 Wörter.")
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
