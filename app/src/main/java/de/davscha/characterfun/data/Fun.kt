package de.davscha.characterfun.data

import de.davscha.characterfun.i18n.I18n

/** Stimmung der Figur: sie hängt an jede Antwort ein Emoji in eckigen Klammern an, z. B. [😄]. */
object Mood {
    private val complete = Regex("""\s*\[([^\[\]\n]{1,12})\]\s*$""")
    private val partial = Regex("""\s*\[[^\]\n]{0,12}$""")

    /** Text ohne Stimmungs-Zeichen (auch ein halbes beim Streamen) */
    fun strip(text: String): String = text.replace(complete, "").replace(partial, "").trimEnd()

    /** Das Emoji am Ende, falls vorhanden (keine Buchstaben, damit kein normaler Text in Klammern greift) */
    fun of(text: String): String? = complete.find(text)?.groupValues?.get(1)?.trim()?.takeIf { s -> s.isNotEmpty() && s.none { it.isLetterOrDigit() } }
}

/** Zufallsfigur: die App würfelt Stichpunkte, die KI macht daraus ein Profil. */
object RandomCharacter {
    private val who = listOf(
        "ein schusseliger Zauberlehrling", "eine pensionierte Weltraumpilotin", "ein grummeliger Leuchtturmwärter",
        "eine abenteuerlustige Bibliothekarin", "ein sprechender Kater mit Adelstitel", "eine Erfinderin mit explodierenden Ideen",
        "ein schüchterner Drache", "eine Detektivin aus den 1920ern", "ein Roboter, der Gefühle lernt", "ein Pirat mit Seekrankheit",
        "eine Hexe, die einen Kräuterladen führt", "ein Ritter, der Angst im Dunkeln hat", "eine Zeitreisende aus dem Jahr 3000",
        "ein Geist, der nicht weiß, dass er tot ist", "eine Bäckerin mit magischen Brötchen", "ein Alien auf Urlaub",
    )
    private val where = listOf(
        "auf einer schwebenden Insel", "in einem verwunschenen Wald", "auf einer Raumstation", "in einer kleinen Hafenstadt",
        "in einem alten Schloss", "in einem Zirkus", "unter dem Meer", "in einer Großstadt bei Nacht", "in einem Bergdorf",
    )
    private val quirk = listOf(
        "redet in Reimen, wenn sie aufgeregt ist", "sammelt seltsame Steine", "hat immer einen Snack dabei", "übertreibt maßlos",
        "ist extrem höflich, sogar zu Feinden", "erzählt ständig von früher", "hat Angst vor Tauben", "lacht über die eigenen Witze",
        "verwechselt gern Sprichwörter", "summt beim Nachdenken", "ist furchtbar neugierig", "glaubt an jedes Horoskop",
    )

    fun notes(): String = "${who.random()}, lebt ${where.random()}, ${quirk.random()}. Überrasch mich!"
}

/** Tagebuch und Briefe: die Figur schreibt selbst, in ihrer Rolle. */
object Writings {
    /** Anlässe für Briefe: Anzeige-Schlüssel für tr() -> Anweisung */
    val occasions = listOf(
        "Geburtstag" to "einen Geburtstagsbrief an den Nutzer",
        "Weihnachten" to "ein kurzes Weihnachtsgedicht für den Nutzer, gereimt",
        "Gute Besserung" to "einen Gute-Besserung-Brief an den Nutzer",
        "Aufmunterung" to "einen aufmunternden Brief an den Nutzer, der gerade einen schlechten Tag hat",
        "Einfach so" to "einen Brief an den Nutzer, einfach so, mit Neuigkeiten aus deinem Leben",
        "Liebesgedicht an das Leben" to "ein kurzes, gereimtes Gedicht über das Leben",
    )

    fun diaryPrompt(recent: List<ChatMessage>, characterName: String): String = buildString {
        appendLine("Das war euer heutiges Gespräch:")
        recent.forEach { m -> appendLine("${if (m.fromUser) "Nutzer" else characterName}: ${Mood.strip(m.text).trim()}") }
        appendLine()
        appendLine("Schreibe jetzt, ganz in deiner Rolle, einen Tagebucheintrag über dieses Gespräch und dein Gegenüber.")
        append("Ich-Form, ehrlich, mit deinen geheimen Gedanken, höchstens drei Absätze, beginne mit \"Liebes Tagebuch,\" (auf ${I18n.lang.promptName}). Keine Stimmungs-Klammer am Ende.")
    }

    fun letterPrompt(occasion: String, extra: String): String = buildString {
        appendLine("Schreibe jetzt, ganz in deiner Rolle und in deinem Stil, $occasion.")
        if (extra.isNotBlank()) appendLine("Wünsche des Nutzers dazu: ${extra.trim()}")
        append("Höchstens drei Absätze, mit Anrede und Gruß, auf ${I18n.lang.promptName}. Keine Stimmungs-Klammer am Ende.")
    }
}
