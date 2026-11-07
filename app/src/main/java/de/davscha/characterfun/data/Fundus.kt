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

/** Ein Bildstil = eine LoRA. Das Trigger-Tag ist der Dateiname ohne Ordner und Endung. */
data class StyleItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** So, wie ComfyUI die LoRA nennt, z. B. NoobAi\PandorasBoxStyle.safetensors */
    val lora: String = "",
    val strength: Float = 1f,
    val preview: String = "",
) {
    val trigger: String get() = lora.substringAfterLast('\\').substringAfterLast('/').substringBeforeLast('.')

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("lora", lora)
        .put("staerke", strength.toDouble()).put("vorschau", preview)

    companion object {
        fun fromJson(o: JSONObject) = StyleItem(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            o.optString("name"), o.optString("lora"), o.optDouble("staerke", 1.0).toFloat(), o.optString("vorschau"),
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

        /** Startausstattung beim ersten Öffnen */
        val starter: Fundus get() {
            val pandora = StyleItem(id = "stil-pandora", name = "Pandora's Box", lora = "NoobAi\\PandorasBoxStyle.safetensors", strength = 1f)
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
                styles = listOf(pandora),
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

    fun locationPreview(item: FundusItem) = join(item.tags, "scenery, no humans")
    /** Eine Person als Model, eine einzige Ansicht von vorn */
    fun clothingPreview(item: FundusItem) = join("solo", item.model, item.tags, "standing, full body, front view, looking at viewer, simple background, white background")
    /** Zusätzlich verboten bei Kleidungs-Vorschauen: Mehrfachansichten */
    const val CLOTHING_PREVIEW_NEGATIVE = "multiple views, reference sheet, character sheet, multiple girls, multiple boys, mannequin"
    const val STYLE_PREVIEW = "1girl, smile, upper body, flower field, outdoors, looking at viewer"

    /** Vorschau des Aussehens: Oberkörper, Gesicht gut zu sehen */
    fun appearancePreview(c: Character, outfit: FundusItem?) =
        join("solo", c.genderTag, c.appearance, outfit?.tags, "upper body, looking at viewer, smile, simple background")

    /** Anprobieren: die Figur in einem Outfit, ganzer Körper, eine Ansicht */
    fun tryOn(c: Character, outfit: FundusItem) =
        join("solo", c.genderTag, c.appearance, outfit.tags, "standing, full body, front view, looking at viewer, simple background, white background")
}

/** Welche Liste im Fundus */
enum class FundusKind { LOCATION, CLOTHING }
