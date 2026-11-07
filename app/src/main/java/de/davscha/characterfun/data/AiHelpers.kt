package de.davscha.characterfun.data

import de.davscha.characterfun.i18n.I18n

/** Prompts für die kleinen KI-Hilfen (Stil-Knopf, Steckbrief-Check, Erinnerungen, Geschichte). */
object AiHelpers {

    /** Stile für den Stil-Knopf: Anzeige-Schlüssel für tr() -> Anweisung fürs Modell */
    val styles = listOf(
        "Lustiger" to "lustiger und witziger",
        "Dramatischer" to "dramatischer und gefühlvoller",
        "Erzählmodus" to "im Erzählmodus: Handlungen und Gesten zwischen *Sternchen*, dazu kurze wörtliche Rede",
        "Frecher" to "frecher und schlagfertiger",
        "Höflicher" to "höflicher und freundlicher",
        "Kürzer" to "kürzer und knackiger",
    )

    const val STYLE_SYSTEM =
        "Du formulierst Nachrichten in einem Rollenspiel-Chat um. Du antwortest nur mit der umgeschriebenen Nachricht, ohne Anführungszeichen und ohne Erklärung."

    fun stylePrompt(text: String, style: String, characterName: String, lastReply: String?): String = buildString {
        if (!lastReply.isNullOrBlank()) appendLine("Zuletzt hat $characterName geschrieben: ${lastReply.trim().take(600)}")
        appendLine("Der Nutzer will $characterName antworten mit: ${text.trim()}")
        appendLine()
        append("Schreibe diese Nachricht des Nutzers $style um. Behalte Inhalt und Absicht, schreibe auf ${I18n.lang.promptName}, ")
        append("aus Sicht des Nutzers (ich-Form).")
    }

    const val CHECK_SYSTEM =
        "Du bist ein freundlicher Lektor für Rollenspiel-Figuren in einer Chat-App. Du gibst kurze, konkrete Tipps."

    fun checkPrompt(c: Character): String = buildString {
        appendLine("Hier ist der Steckbrief einer Figur:")
        appendLine("Name: ${c.name}")
        appendLine("Kurzbeschreibung: ${c.shortDescription}")
        appendLine("Begrüßung: ${c.greeting}")
        appendLine("Persönlichkeit und Sprechweise: ${c.definition}")
        appendLine("Hintergrund: ${c.longDescription}")
        appendLine("Startszene: ${c.scenario}")
        appendLine("Gesprächsstarter: ${c.starters.joinToString(" | ")}")
        appendLine()
        appendLine("Prüfe den Steckbrief: Was fehlt, was widerspricht sich, was würde die Figur im Chat lebendiger machen?")
        append("Antworte mit drei bis fünf kurzen Tipps als Liste mit \"- \", auf ${I18n.lang.promptName}. Leere Felder sind erlaubt, erwähne sie nur, wenn sie wirklich fehlen.")
    }

    const val MEMORY_SYSTEM =
        "Du hilfst einer Figur in einem Rollenspiel-Chat, sich Wichtiges zu merken. Du antwortest ausschließlich mit einem JSON-Array aus Strings, ohne Erklärung."

    fun memoryPrompt(characterName: String, recent: List<ChatMessage>, known: List<String>): String = buildString {
        appendLine("Hier ist ein Chat zwischen dem Nutzer und \"$characterName\":")
        appendLine()
        recent.forEach { m -> appendLine("${if (m.fromUser) "Nutzer" else characterName}: ${m.text.trim()}") }
        appendLine()
        if (known.isNotEmpty()) appendLine("Schon gemerkt (nicht wiederholen): ${known.joinToString(" | ")}")
        appendLine("Finde bis zu drei Dinge, die sich $characterName langfristig merken sollte: Fakten über den Nutzer, Abmachungen, wichtige Ereignisse.")
        append("Je ein kurzer Satz auf ${I18n.lang.promptName}. Format: [\"…\", \"…\"]")
    }

    const val STORY_SYSTEM =
        "Du bist ein begabter Erzähler. Du machst aus Chat-Verläufen kurze, lebendige Geschichten."

    fun storyPrompt(characterName: String, aboutUser: String, summary: String, recent: List<ChatMessage>): String = buildString {
        if (summary.isNotBlank()) appendLine("Was vorher geschah: ${summary.trim()}")
        appendLine("Chat zwischen dem Nutzer und \"$characterName\":")
        appendLine()
        recent.forEach { m -> appendLine("${if (m.fromUser) "Nutzer" else characterName}: ${m.text.trim()}") }
        appendLine()
        if (aboutUser.isNotBlank()) appendLine("Über den Nutzer: ${aboutUser.trim()}")
        appendLine("Erzähle dieses Gespräch als kurze Geschichte in der dritten Person, auf ${I18n.lang.promptName}.")
        append("Erste Zeile: ein passender Titel. Danach höchstens vier Absätze. Nenne den Nutzer bei seinem Namen, falls bekannt, sonst \"der Besucher\". Keine Aufzählungen.")
    }

    // ---------- Bild als Nachricht ----------

    /** Arten der Bild-Nachricht: Anzeige-Schlüssel für tr() -> Anweisung */
    val imageModes = listOf(
        "Erzählen" to "als lebendige kleine Erzählung mit einer Handlung zwischen *Sternchen* (z. B. *hält dir ein Foto hin*)",
        "Beschreiben" to "als genaue, anschauliche Beschreibung des Bildes",
        "Frage dazu" to "die das Bild kurz beschreibt und dann eine neugierige Frage dazu stellt",
    )

    const val IMAGE_SYSTEM =
        "Du schreibst für den Nutzer einer Rollenspiel-Chat-App eine Nachricht, die ein Bild in Worte fasst. " +
            "Du antwortest nur mit der fertigen Nachricht, ohne Anführungszeichen und ohne Erklärung."

    fun imagePrompt(targets: List<Character>, userGender: Gender, hint: String, mode: String): String = buildString {
        val names = targets.joinToString(" und ") { it.name }
        appendLine("Der Nutzer chattet mit $names und möchte dieses Bild zeigen.")
        targets.filter { it.gender != Gender.UNKNOWN }.forEach { appendLine("${it.name} ist ${it.gender.prompt} – sprich ${it.name} grammatisch passend an.") }
        if (userGender != Gender.UNKNOWN) appendLine("Der Nutzer selbst ist ${userGender.prompt}. Alles in Ich-Form muss dazu passen.")
        if (hint.isNotBlank()) appendLine("Hinweis des Nutzers zum Bild (hat Vorrang vor deiner Deutung): ${hint.trim()}")
        appendLine()
        appendLine("Schreibe in Ich-Form aus Sicht des Nutzers eine Nachricht an $names, $mode.")
        appendLine("Beschreibe das Bild so anschaulich, dass man es sich ohne das Bild vorstellen kann: was zu sehen ist, Farben, Stimmung, Besonderheiten.")
        appendLine("Personen auf dem Bild: Lege ihr Geschlecht oder Alter nur fest, wenn es eindeutig ist oder im Hinweis steht – sonst schreibe neutral (z. B. \"die Person\").")
        appendLine("Die Person auf dem Bild ist nicht automatisch der Nutzer, außer der Hinweis sagt es.")
        append("Höchstens 80 Wörter, auf ${I18n.lang.promptName}.")
    }
}
