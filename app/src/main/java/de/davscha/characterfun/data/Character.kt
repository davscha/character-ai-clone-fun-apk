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

/** Wie lang die Figur antwortet */
enum class ReplyLength(val label: String, val rule: String) {
    KURZ("Kurz", "Antworte sehr knapp: höchstens zwei kurze Sätze."),
    MITTEL("Mittel", "Antworte wie in einem Chat: höchstens vier kurze Sätze."),
    LANG("Lang", "Antworte ausführlich mit fünf bis acht Sätzen und beschreibe dabei auch die Szene."),
}

/** Geschlecht – wichtig für die richtige Anrede und Ich-Form im Deutschen. [label] ist Schlüssel für tr(). */
enum class Gender(val label: String, val prompt: String) {
    UNKNOWN("keine Angabe", ""),
    MALE("männlich", "männlich"),
    FEMALE("weiblich", "weiblich"),
    DIVERSE("divers", "divers (neutrale Formen verwenden)");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
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
    /** Ausgangssituation, in der das Gespräch beginnt (optional) */
    val scenario: String = "",
    val replyLength: ReplyLength = ReplyLength.MITTEL,
    /** Id des Lieblingsmodells; leer = das Modell aus den Einstellungen */
    val preferredModelId: String = "",
    /** Vorlesen: Stimmlage (0.25 sehr tief … 3.0 sehr hoch) und Tempo (0.25 langsam … 3.0 schnell) */
    val voicePitch: Float = 1.0f,
    val voiceRate: Float = 1.0f,
    /** Name der Android-Stimme (leer = Standardstimme für Deutsch) */
    val voiceName: String = "",
    val gender: Gender = Gender.UNKNOWN,
    // ---------- Bilder (ComfyUI) ----------
    /** Aussehen als Bild-Tags, z. B. "long hair, blonde hair, green eyes, glasses" (ohne Kleidung) */
    val appearance: String = "",
    /** Vorschaubild des Aussehens (Dateiname im Fundus-Ordner) */
    val appearancePreview: String = "",
    /** Kleidung aus dem Fundus, die diese Figur im Schrank hat (Ids) */
    val wardrobe: List<String> = emptyList(),
    /** Standard-Outfit (Id aus dem Fundus, leer = keins) */
    val defaultOutfitId: String = "",
    /** Fester Startwert, damit die Figur auf allen Bildern ähnlich aussieht */
    val imageSeed: Long = 0,
    /** Eigener Stil (Id aus dem Fundus, leer = Standard-Stil) */
    val styleId: String = "",
) {
    /** Geschlecht als Bild-Tag */
    val genderTag: String get() = when (gender) {
        Gender.MALE -> "1boy"
        Gender.FEMALE -> "1girl"
        Gender.DIVERSE -> "1other"
        Gender.UNKNOWN -> ""
    }

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
        .put("scenario", scenario)
        .put("replyLength", replyLength.name)
        .put("preferredModel", preferredModelId)
        .put("voicePitch", voicePitch.toDouble())
        .put("voiceRate", voiceRate.toDouble())
        .put("voiceName", voiceName)
        .put("geschlecht", gender.name)
        .put("aussehen", appearance)
        .put("aussehenVorschau", appearancePreview)
        .put("schrank", JSONArray(wardrobe))
        .put("standardOutfit", defaultOutfitId)
        .put("bildStartwert", imageSeed)
        .put("stil", styleId)

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
            scenario = o.optString("scenario"),
            replyLength = runCatching { ReplyLength.valueOf(o.optString("replyLength")) }.getOrDefault(ReplyLength.MITTEL),
            preferredModelId = o.optString("preferredModel"),
            voicePitch = o.optDouble("voicePitch", 1.0).toFloat(),
            voiceRate = o.optDouble("voiceRate", 1.0).toFloat(),
            voiceName = o.optString("voiceName"),
            gender = Gender.of(o.optString("geschlecht")),
            appearance = o.optString("aussehen"),
            appearancePreview = o.optString("aussehenVorschau"),
            wardrobe = o.optJSONArray("schrank")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
            defaultOutfitId = o.optString("standardOutfit"),
            imageSeed = o.optLong("bildStartwert"),
            styleId = o.optString("stil"),
        )
    }
}
