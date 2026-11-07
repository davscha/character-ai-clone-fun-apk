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

    /**
     * Arten der Bild-Nachricht: Anzeige-Schlüssel für tr() -> Anweisung.
     * Das Bild ist kein Foto, das man herzeigt, sondern der Moment im Rollenspiel.
     */
    val imageModes = listOf(
        "Was gerade passiert" to "Das Bild zeigt, was in diesem Moment passiert, wo du bist und was du siehst oder tust. Erzähle es als Handlung zwischen *Sternchen* in der Gegenwart, dazu ein, zwei Sätze wörtliche Rede an",
        "Was ich gerade sehe" to "Das Bild zeigt genau das, was du in diesem Moment mit eigenen Augen vor dir siehst. Beschreibe aus deiner Sicht, was dir auffällt (Menschen, Kleidung, Blicke, Umgebung, Kleinigkeiten), und reagiere darauf – mit einer kleinen Geste zwischen *Sternchen* und wörtlicher Rede an",
        "Was passieren soll" to "Das Bild zeigt, was als Nächstes passieren soll – ein Ort, an den du willst, oder etwas, das du vorhast. Schlage es vor oder fang direkt damit an, mit Handlung zwischen *Sternchen* und wörtlicher Rede an",
        "Was ich denke" to "Das Bild zeigt, woran du gerade denkst oder wovon du träumst. Erzähle deine Gedanken und Gefühle dazu, gern mit einer kleinen Geste zwischen *Sternchen*, gerichtet an",
    )

    const val IMAGE_SYSTEM =
        "Du schreibst für den Nutzer einer Rollenspiel-Chat-App seine nächste Nachricht. Ein Bild zeigt dir den Moment, den er ausdrücken will. " +
            "Du antwortest nur mit der fertigen Nachricht, ohne Anführungszeichen und ohne Erklärung."

    fun imagePrompt(targets: List<Character>, userGender: Gender, hint: String, mode: String, context: String): String = buildString {
        val names = targets.joinToString(" und ") { it.name }
        if (context.isNotBlank()) {
            appendLine("So lief das Gespräch mit $names bisher (Ende):")
            appendLine(context.trim())
            appendLine()
        }
        targets.filter { it.gender != Gender.UNKNOWN }.forEach { appendLine("${it.name} ist ${it.gender.prompt} – sprich ${it.name} grammatisch passend an.") }
        if (userGender != Gender.UNKNOWN) appendLine("Der Nutzer selbst ist ${userGender.prompt}. Alles in Ich-Form muss dazu passen.")
        if (hint.isNotBlank()) appendLine("Hinweis des Nutzers (hat Vorrang vor deiner Deutung des Bildes): ${hint.trim()}")
        appendLine()
        appendLine("Schreibe die nächste Nachricht des Nutzers in Ich-Form. $mode $names.")
        appendLine("Ganz wichtig: Erwähne NIE, dass es ein Bild, Foto oder eine Aufnahme ist. Kein \"schau mal\", kein \"ich zeige dir\". Das Bild ist die Wirklichkeit im Rollenspiel – beschreibe es, als würdest du es gerade erleben.")
        appendLine("Lass die Einzelheiten aus dem Bild lebendig einfließen (Ort, Licht, Farben, Stimmung) und knüpfe natürlich an das bisherige Gespräch an.")
        appendLine("Zeigt das Bild (laut Hinweis oder eindeutig) eine der Figuren aus dem Chat, dann ist das diese Figur selbst, wie sie gerade vor dir ist: sprich sie direkt an (\"du\"/\"Sie\", wie bisher im Gespräch), statt über sie in der dritten Person zu reden.")
        appendLine("Personen auf dem Bild: Lege ihr Geschlecht oder Alter nur fest, wenn es eindeutig ist oder im Hinweis steht – sonst schreibe neutral. Eine Person auf dem Bild ist nicht automatisch der Nutzer, außer der Hinweis sagt es.")
        append("Höchstens 80 Wörter, auf ${I18n.lang.promptName}.")
    }
}
