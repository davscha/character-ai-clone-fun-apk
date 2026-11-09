package de.davscha.characterfun.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ort oder Kleidungsstück aus dem Fundus – gilt für alle Figuren. [tags] im Danbooru-Stil (Englisch). */
data class FundusItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val tags: String = "",
    /** Dateiname des Vorschaubilds im Ordner Fundus (oder leer) */
    val preview: String = "",
    /** Nur bei Kleidung: wer die Vorschau trägt – "1girl" oder "1boy" */
    val model: String = "1girl",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("tags", tags).put("vorschau", preview).put("model", model)

    companion object {
        fun fromJson(o: JSONObject) = FundusItem(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            o.optString("name"), o.optString("tags"), o.optString("vorschau"),
            o.optString("model").ifEmpty { "1girl" },
        )
    }
}

/** Ein Bildstil in Worten (MOSS Image versteht Sätze), z. B. „classic Disney animation style, soft colors“ */
data class StyleItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Alt (NoobAI-LoRA) – wird nicht mehr benutzt */
    val lora: String = "",
    val strength: Float = 1f,
    val preview: String = "",
    /** Stil auf Englisch, steht vorne in jedem Bild-Prompt */
    val prompt: String = "",
) {
    val trigger: String get() = prompt

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("lora", lora)
        .put("staerke", strength.toDouble()).put("vorschau", preview).put("prompt", prompt)

    companion object {
        fun fromJson(o: JSONObject) = StyleItem(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            o.optString("name"), o.optString("lora"), o.optDouble("staerke", 1.0).toFloat(), o.optString("vorschau"),
            o.optString("prompt").ifBlank { if (o.optString("lora").isNotBlank()) "anime illustration, clean lineart, soft colors" else "" },
        )
    }
}

/** Der Fundus: Orte, Kleidung und Stile für alle Figuren. Liegt in CharacterFun/fundus.json. */
data class Fundus(
    val locations: List<FundusItem> = emptyList(),
    val clothing: List<FundusItem> = emptyList(),
    val styles: List<StyleItem> = emptyList(),
    val defaultStyleId: String = "",
) {
    val defaultStyle: StyleItem? get() = styles.firstOrNull { it.id == defaultStyleId } ?: styles.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("orte", JSONArray(locations.map { it.toJson() }))
        .put("kleidung", JSONArray(clothing.map { it.toJson() }))
        .put("stile", JSONArray(styles.map { it.toJson() }))
        .put("standardStil", defaultStyleId)

    companion object {
        fun fromJson(o: JSONObject): Fundus {
            fun items(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { FundusItem.fromJson(a.getJSONObject(it)) } }.orEmpty()
            val styles = o.optJSONArray("stile")?.let { a -> List(a.length()) { StyleItem.fromJson(a.getJSONObject(it)) } }.orEmpty()
            return Fundus(items("orte"), items("kleidung"), styles, o.optString("standardStil"))
        }

        /** Stile für MOSS Image (in Worten) */
        val mossStyles = listOf(
            StyleItem(id = "stil-disney", name = "Disney-Zeichentrick", prompt = "classic Disney animation style, hand-drawn, soft colors, expressive faces"),
            StyleItem(id = "stil-anime", name = "Anime", prompt = "anime illustration, clean lineart, vibrant colors"),
            StyleItem(id = "stil-comic", name = "Comic", prompt = "western comic book art, bold ink outlines, flat colors"),
            StyleItem(id = "stil-aquarell", name = "Aquarell", prompt = "soft watercolor painting, storybook illustration, gentle colors"),
            StyleItem(id = "stil-foto", name = "Foto (realistisch)", prompt = "realistic photo, cinematic lighting, 35mm lens"),
        )

        /** Startausstattung beim ersten Öffnen */
        val starter: Fundus get() {
            val pandora = mossStyles.first()
            return Fundus(
                locations = listOf(
                    FundusItem(name = "Gemütliches Café", tags = "cafe, indoors, wooden table, coffee cup, warm lighting, window, cozy"),
                    FundusItem(name = "Stadt am Abend", tags = "city, street, evening, street lamps, buildings, crowd, outdoors"),
                    FundusItem(name = "Strand bei Sonnenuntergang", tags = "beach, ocean, sunset, orange sky, sand, waves, outdoors"),
                    FundusItem(name = "Verwunschener Wald", tags = "forest, trees, sunbeam, moss, mushrooms, fantasy, outdoors"),
                    FundusItem(name = "Wohnzimmer", tags = "living room, indoors, sofa, bookshelf, lamp, plant, cozy"),
                    FundusItem(name = "Hafen bei Nacht", tags = "harbor, night, pier, ships, lanterns, moonlight, fog"),
                ),
                clothing = listOf(
                    FundusItem(name = "Alltag mit Mantel", tags = "brown coat, white sweater, turtleneck, black pants"),
                    FundusItem(name = "Abendkleid", tags = "black dress, evening gown, long dress, earrings"),
                    FundusItem(name = "Regenmantel", tags = "yellow raincoat, hood, rubber boots"),
                    FundusItem(name = "Piratenmantel", tags = "pirate coat, red coat, tricorne, boots, belt", model = "1boy"),
                    FundusItem(name = "Pyjama", tags = "pajamas, striped pajamas, slippers"),
                    FundusItem(name = "Sportlich", tags = "hoodie, track pants, sneakers"),
                ),
                styles = mossStyles,
                defaultStyleId = pandora.id,
            )
        }
    }
}

/** Baut die Prompts für ComfyUI: Basis, Stil-Trigger, dann die Bausteine. */
object ImagePrompt {
    fun join(vararg parts: String?): String = parts.filterNotNull()
        .flatMap { it.split(',', '\n') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")

    /** Figur in Worten: „a woman, long red hair, green eyes, wearing brown coat“ (MOSS Image versteht Sätze) */
    fun looks(c: Character, outfit: FundusItem?) = join(c.genderWord, c.appearance, outfit?.tags?.takeIf { it.isNotBlank() }?.let { "wearing $it" })

    fun locationPreview(item: FundusItem) = "Scenery without people: ${join(item.tags)}."
    /** Eine Person als Model, eine einzige Ansicht von vorn */
    fun clothingPreview(item: FundusItem) =
        "Full body front view of a single ${if (item.model == "1boy") "man" else "young woman"} wearing ${join(item.tags)}, standing, plain white background."
    /** MOSS hat keinen Negativ-Prompt mehr – bleibt für eigene Workflows */
    const val CLOTHING_PREVIEW_NEGATIVE = "multiple views, reference sheet, character sheet, mannequin"
    const val STYLE_PREVIEW = "A smiling young woman in a flower field, upper body, looking at the viewer."

    /** Vorschau des Aussehens: Oberkörper, Gesicht gut zu sehen */
    fun appearancePreview(c: Character, outfit: FundusItem?) =
        "Portrait of a single character: ${looks(c, outfit)}. Upper body, smiling, looking at the viewer, plain background."

    /** Anprobieren: die Figur in einem Outfit, ganzer Körper, eine Ansicht */
    fun tryOn(c: Character, outfit: FundusItem) =
        "Full body front view of a single character: ${looks(c, outfit)}. Standing, looking at the viewer, plain white background."
}

/** Welche Liste im Fundus */
enum class FundusKind { LOCATION, CLOTHING }
