package de.davscha.characterfun.data

/**
 * Baut aus den Feldern einer Figur die Anweisung, die vor jedem Gespräch an das Modell geht.
 * Kurz halten: kleine Modelle vergessen sonst, wer sie sein sollen.
 */
object PromptBuilder {

    fun build(c: Character): String = buildString {
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
        appendLine()
        appendLine("Regeln:")
        appendLine("- Bleib immer in der Rolle von ${c.name} und gib nie zu, eine KI zu sein.")
        appendLine("- Antworte wie in einem Chat: höchstens vier kurze Sätze, keine Aufzählungen, keine Überschriften.")
        appendLine("- Antworte auf Deutsch, außer deine Beschreibung sagt etwas anderes.")
        append("- Handlungen und Gesten schreibst du zwischen Sternchen, z. B. *grinst*.")
    }
}
