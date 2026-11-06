package de.davscha.characterfun.data

import org.json.JSONObject

/**
 * Figur mit KI-Hilfe: Aus ein paar Stichpunkten schreibt das Modell ein komplettes Profil.
 * Es antwortet als JSON, das hier in die Felder einer [Character] übersetzt wird.
 */
object ProfileGenerator {

    const val SYSTEM_PROMPT =
        "Du bist ein kreativer Autor, der Figuren für eine Rollenspiel-Chat-App entwirft. " +
            "Du antwortest ausschließlich mit einem einzigen JSON-Objekt – ohne Erklärung davor oder danach, ohne Markdown."

    fun prompt(notes: String): String = """
        Erstelle aus diesen Stichpunkten eine Figur:
        ${notes.trim()}

        Gib genau dieses JSON zurück und fülle jedes Feld aus:
        {
          "name": "Name der Figur",
          "emoji": "ein einzelnes passendes Emoji",
          "kategorie": "genau eins von: Abenteuer, Lernen, Helfer, Spiele, Geschichten, Sprachen, Sonstiges",
          "kurzbeschreibung": "ein Satz für die Vorschau-Karte, höchstens 15 Wörter",
          "begruessung": "der erste Satz der Figur im Chat, in ihrem typischen Tonfall",
          "definition": "4 bis 6 kurze Sätze in Du-Form (Du bist …): wie die Figur denkt, redet und sich verhält, mit typischen Ausdrücken",
          "hintergrund": "2 bis 3 Sätze zu Aussehen und Vorgeschichte",
          "starter": ["Gesprächsvorschlag 1", "Gesprächsvorschlag 2", "Gesprächsvorschlag 3"]
        }
        Schreibe alles auf Deutsch.
    """.trimIndent()

    /** Übernimmt die Antwort in [base] (Id und Bild bleiben erhalten). Wirft eine Ausnahme, wenn kein JSON drin ist. */
    fun parse(answer: String, base: Character): Character {
        // Modelle setzen gern ```json … ``` drumherum – einfach vom ersten { bis zur letzten } nehmen
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        require(start >= 0 && end > start) { "Die KI hat kein Profil geliefert. Einfach nochmal versuchen." }
        val o = JSONObject(answer.substring(start, end + 1))

        fun field(key: String, old: String) = o.optString(key).trim().ifEmpty { old }

        val categoryLabel = o.optString("kategorie").trim()
        val category = Category.entries.firstOrNull { it.label.equals(categoryLabel, ignoreCase = true) } ?: base.category
        val starters = o.optJSONArray("starter")?.let { a -> List(a.length()) { a.optString(it).trim() } }
            ?.filter { it.isNotEmpty() }?.take(3)

        return base.copy(
            name = field("name", base.name),
            emoji = if (base.avatarFile.isEmpty()) field("emoji", base.emoji).take(8) else base.emoji,
            category = category,
            shortDescription = field("kurzbeschreibung", base.shortDescription),
            greeting = field("begruessung", base.greeting),
            definition = field("definition", base.definition),
            longDescription = field("hintergrund", base.longDescription),
            starters = starters?.ifEmpty { null } ?: base.starters,
        )
    }
}
