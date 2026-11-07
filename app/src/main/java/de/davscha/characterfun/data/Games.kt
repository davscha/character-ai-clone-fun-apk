package de.davscha.characterfun.data

import org.json.JSONObject

/** Kleine Spiele im Chat mit einer Figur. Texte sind deutsche Schlüssel für tr(). */
enum class Game(val emoji: String, val label: String, val description: String, val opening: String) {
    TWENTY_QUESTIONS(
        "❓", "20 Fragen",
        "%s denkt sich etwas aus, du rätst mit Ja/Nein-Fragen.",
        "Lass uns „20 Fragen“ spielen! Denk dir etwas aus, ich rate.",
    ),
    TWENTY_QUESTIONS_REVERSE(
        "🤔", "20 Fragen andersherum",
        "Du denkst dir etwas aus, %s stellt die Fragen.",
        "Lass uns „20 Fragen“ andersherum spielen! Ich habe mir etwas ausgedacht – stell du die Fragen.",
    ),
    WHO_AM_I(
        "🎭", "Wer bin ich?",
        "%s verwandelt sich heimlich in jemanden – du findest heraus, in wen.",
        "Lass uns „Wer bin ich?“ spielen! Verwandle dich heimlich, ich rate, wer du bist.",
    ),
    ADVENTURE(
        "🗺️", "Text-Abenteuer",
        "%s ist Spielleiter: drei Möglichkeiten pro Runde, Würfel und drei Leben.",
        "Lass uns ein Text-Abenteuer spielen! Du bist der Spielleiter.",
    ),
    STORY(
        "📖", "Geschichten-Kette",
        "Ihr schreibt abwechselnd je einen Satz einer gemeinsamen Geschichte.",
        "Lass uns eine Geschichten-Kette schreiben! Fang du mit dem ersten Satz an.",
    ),
}

/** Ein laufendes Spiel. [secret] kennt nur die App und die Figur; [startIndex] = Nachricht, mit der es losging. */
data class GameState(val game: Game, val secret: String = "", val startIndex: Int = 0) {
    fun toJson(): JSONObject = JSONObject().put("spiel", game.name).put("geheimnis", secret).put("start", startIndex)

    companion object {
        fun fromJson(o: JSONObject?): GameState? {
            val g = o?.optString("spiel")?.let { n -> Game.entries.firstOrNull { it.name == n } } ?: return null
            return GameState(g, o.optString("geheimnis"), o.optInt("start"))
        }
    }
}

object Games {
    const val MAX_QUESTIONS = 20

    // Die App wählt das Geheimnis aus – kleine Modelle vergessen sonst, was sie sich "ausgedacht" haben.
    private val things = listOf(
        "Giraffe", "Pinguin", "Elefant", "Krake", "Igel", "Papagei", "Schnecke", "Delfin", "Biene", "Kamel",
        "Regenschirm", "Zahnbürste", "Fahrrad", "Leuchtturm", "Kühlschrank", "Gitarre", "Kerze", "Brille",
        "Schlüssel", "Teekanne", "Wecker", "Heißluftballon", "Schneemann", "Kaktus", "Pizza", "Banane",
        "Eis am Stiel", "Brezel", "Vulkan", "Regenbogen", "Mond", "Burg", "U-Boot", "Feuerwehrauto",
        "Trampolin", "Sanduhr", "Kompass", "Briefmarke", "Gartenzwerg", "Seifenblase",
    )
    private val identities = listOf(
        "Rotkäppchen", "der böse Wolf", "Schneewittchen", "Rumpelstilzchen", "Robin Hood", "Sherlock Holmes",
        "Graf Dracula", "Frankensteins Monster", "König Artus", "der Zauberer Merlin", "Pinocchio", "Aschenputtel",
        "der Weihnachtsmann", "der Osterhase", "Kleopatra", "Leonardo da Vinci", "Wolfgang Amadeus Mozart",
        "Christoph Kolumbus", "ein Pirat", "ein Astronaut", "ein Koch", "ein Feuerwehrmann", "eine Hexe",
        "ein Ritter", "ein Zauberer", "ein Dinosaurier", "eine Katze", "ein Hund", "ein Gespenst", "ein Roboter",
    )

    fun secretFor(g: Game): String = when (g) {
        Game.TWENTY_QUESTIONS -> things.random()
        Game.WHO_AM_I -> identities.random()
        else -> ""
    }

    /** Spielregeln für den Systemprompt */
    fun rules(state: GameState, c: Character): String = when (state.game) {
        Game.TWENTY_QUESTIONS -> """
            Ihr spielt „20 Fragen“. Du hast dir heimlich diesen Begriff ausgedacht: „${state.secret}“.
            Verrate ihn niemals von selbst und gib keine zu deutlichen Tipps.
            Dein Gegenüber stellt Ja/Nein-Fragen. Antworte ehrlich mit Ja, Nein oder Teils-teils und einem kurzen Kommentar in deiner Rolle.
            Rät es richtig (auch mit einem anderen Wort für dasselbe), gratuliere begeistert und löse auf.
            Die Nachrichten tragen eine Fragen-Nummer. Ist die 20. Frage vorbei und noch nicht geraten, löst du auf.
        """.trimIndent()
        Game.TWENTY_QUESTIONS_REVERSE -> """
            Ihr spielt „20 Fragen“ andersherum: Dein Gegenüber hat sich etwas ausgedacht, du musst es erraten.
            Stell in jeder Antwort genau eine Ja/Nein-Frage, nummeriert (Frage 1, Frage 2 …). Grenze klug ein: erst grob (Lebewesen? Gegenstand? Größer als ein Brot?), dann genauer.
            Merk dir alle Antworten und frag nichts doppelt. Wenn du dir sicher bist, rate. Nach höchstens 20 Fragen musst du raten.
        """.trimIndent()
        Game.WHO_AM_I -> """
            Ihr spielt „Wer bin ich?“. Du bleibst ${c.name}, schlüpfst aber heimlich in die Rolle von: „${state.secret}“.
            Sprich und benimm dich so, dass man es erraten kann, aber nenne den Namen nie.
            Beantworte Fragen mit Hinweisen – am Anfang vage, mit der Zeit deutlicher.
            Rät dein Gegenüber richtig, freu dich und löse auf.
        """.trimIndent()
        Game.ADVENTURE -> """
            Du bist Spielleiter eines Text-Abenteuers und erzählst im Stil von ${c.name}. Denk dir ein spannendes Abenteuer aus, das zu dir passt.
            Beschreibe in jeder Antwort kurz die Lage (drei bis fünf Sätze) und biete danach genau drei Möglichkeiten an, jede in einer eigenen Zeile:
            1. …
            2. …
            3. …
            Dein Gegenüber darf auch etwas ganz anderes tun.
            Es startet mit drei Leben. Verliert es eins, zeige den Stand so: ❤️❤️🤍. Bei null Leben ist das Abenteuer vorbei.
            Bei riskanten Aktionen bitte ums Würfeln mit einem sechsseitigen Würfel: 1–2 geht schief, 3–4 klappt knapp, 5–6 klappt großartig.
        """.trimIndent()
        Game.STORY -> """
            Ihr spielt „Geschichten-Kette“ und schreibt abwechselnd eine gemeinsame Geschichte.
            Du schreibst jedes Mal nur ein oder zwei Sätze, die direkt an den letzten Satz anknüpfen – ohne Kommentar drumherum.
            Bring ab und zu eine überraschende Wendung hinein. Kein Gespräch, nur die Geschichte.
        """.trimIndent()
    }

    /** Die drei Möglichkeiten aus einer Antwort des Spielleiters (für Antippen) */
    fun options(text: String): List<String> = text.lines().mapNotNull { line ->
        Regex("""^\s*\**\s*([1-3])[.):]\**\s*(.+)$""").find(line)?.groupValues?.get(2)?.trim()?.trim('*')?.trim()
    }.filter { it.isNotEmpty() }.take(3)
}
