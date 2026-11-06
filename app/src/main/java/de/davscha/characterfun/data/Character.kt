package de.davscha.characterfun.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Kategorien wie die Tabs auf der Startseite von character.ai */
enum class Category(val label: String) {
    ABENTEUER("Abenteuer"),
    LERNEN("Lernen"),
    HELFER("Helfer"),
    SPIELE("Spiele"),
    GESCHICHTEN("Geschichten"),
    SPRACHEN("Sprachen"),
    SONSTIGES("Sonstiges"),
}

/** Eine Figur – alle Felder aus dem character.ai-Editor, die für die App Sinn ergeben. */
data class Character(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Ein Emoji als Avatar, falls kein Bild gewählt ist */
    val emoji: String = "",
    /** Dateiname des Avatarbilds im Ordner Avatare (oder leer) */
    val avatarFile: String = "",
    val category: Category = Category.SONSTIGES,
    /** Ein Satz für die Karte in der Galerie */
    val shortDescription: String = "",
    /** Hintergrund, Aussehen, Vorgeschichte (optional) */
    val longDescription: String = "",
    /** Die eigentliche Persönlichkeit: wie die Figur denkt, redet, sich verhält */
    val definition: String = "",
    /** Erster Satz der Figur im Chat */
    val greeting: String = "",
    /** Vorschläge, womit man das Gespräch beginnen kann */
    val starters: List<String> = emptyList(),
    /** true = mitgelieferte Beispiel-Figur */
    val builtIn: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("emoji", emoji)
        .put("avatarFile", avatarFile)
        .put("category", category.name)
        .put("shortDescription", shortDescription)
        .put("longDescription", longDescription)
        .put("definition", definition)
        .put("greeting", greeting)
        .put("starters", JSONArray(starters))
        .put("builtIn", builtIn)

    companion object {
        fun fromJson(o: JSONObject) = Character(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            emoji = o.optString("emoji"),
            avatarFile = o.optString("avatarFile"),
            category = runCatching { Category.valueOf(o.optString("category")) }.getOrDefault(Category.SONSTIGES),
            shortDescription = o.optString("shortDescription"),
            longDescription = o.optString("longDescription"),
            definition = o.optString("definition"),
            greeting = o.optString("greeting"),
            starters = o.optJSONArray("starters")?.let { a -> List(a.length()) { a.optString(it) } }
                ?.filter { it.isNotBlank() } ?: emptyList(),
            builtIn = o.optBoolean("builtIn"),
        )
    }
}
