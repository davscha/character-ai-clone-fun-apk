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
    /** Ort für MOSS Image (Englisch): was man sieht und wie das Licht ist */
    val background: String = "",
    val lights: String = "",
    /** Kleidung für MOSS Image: Teil (z. B. jacket) → Beschreibung auf Englisch */
    val pieces: List<Pair<String, String>> = emptyList(),
) {
    /** Als Text für die KI (Chat-Hinweise) */
    val text: String get() = listOf(background, lights).filter { it.isNotBlank() }.joinToString(", ")
        .ifBlank { pieces.joinToString(", ") { it.second } }.ifBlank { tags }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("tags", tags).put("vorschau", preview).put("model", model)
        .put("hintergrund", background).put("licht", lights)
        .put("teile", JSONArray(pieces.map { (k, v) -> JSONObject().put("teil", k).put("text", v) }))

    companion object {
        /** Alte Tags: bei Orten zum Hintergrund, bei Kleidung zu einem Teil „outfit“ (passiert beim Laden des Fundus) */
        fun fromJson(o: JSONObject) = FundusItem(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            o.optString("name"), o.optString("tags"), o.optString("vorschau"),
            o.optString("model").ifEmpty { "1girl" },
            o.optString("hintergrund"), o.optString("licht"),
            o.optJSONArray("teile")?.let { a -> List(a.length()) { a.getJSONObject(it).let { t -> t.optString("teil") to t.optString("text") } } }.orEmpty(),
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
            o.optString("prompt"),
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
            val places = items("orte").map { if (it.background.isBlank() && it.tags.isNotBlank()) it.copy(background = it.tags) else it }
            val clothes = items("kleidung").map { if (it.pieces.isEmpty() && it.tags.isNotBlank()) it.copy(pieces = listOf("outfit" to it.tags)) else it }
            // Neuer Stil „Kino-Foto“ auch für alte Fundus-Dateien
            val allStyles = if (styles.none { it.id == "stil-kino" }) listOf(mossStyles.first()) + styles else styles
            return Fundus(places, clothes, allStyles, o.optString("standardStil"))
        }

        /** Stile für MOSS Image (in Worten) */
        val mossStyles = listOf(
            StyleItem(id = "stil-kino", name = "Kino-Foto", prompt = "cinematic photography, faded glow theme, vignette, film grain"),
            StyleItem(id = "stil-disney", name = "Disney-Zeichentrick", prompt = "classic Disney animation style, hand-drawn, soft colors, expressive faces"),
            StyleItem(id = "stil-anime", name = "Anime", prompt = "anime illustration, clean lineart, vibrant colors"),
            StyleItem(id = "stil-comic", name = "Comic", prompt = "western comic book art, bold ink outlines, flat colors"),
            StyleItem(id = "stil-aquarell", name = "Aquarell", prompt = "soft watercolor painting, storybook illustration, gentle colors"),
            StyleItem(id = "stil-foto", name = "Foto (realistisch)", prompt = "realistic photo, cinematic lighting, 35mm lens"),
        )

        /** Startausstattung beim ersten Öffnen */
        val starter: Fundus get() {
            val pandora = mossStyles.first()
            return fromJson(Fundus(
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
            ).toJson())
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

    /** Was eine Figur auf einem Bild macht: eigene Haltung und Gesichtsausdruck */
    data class Moment(val pose: String = "", val expression: String = "")

    private fun JSONObject.putText(k: String, v: String?) = apply { if (!v.isNullOrBlank()) put(k, v.trim()) }

    fun clothing(o: FundusItem?): JSONObject? {
        val parts = o?.pieces?.filter { it.second.isNotBlank() }.orEmpty()
        if (parts.isEmpty()) return null
        return JSONObject().apply { parts.forEach { (k, v) -> put(k.trim().ifBlank { "outfit" }, v.trim()) } }
    }

    /** Eine Figur so, wie MOSS Image sie versteht */
    fun figure(c: Character, outfit: FundusItem?, m: Moment = Moment()): JSONObject {
        val o = JSONObject().putText("name", c.name)
        c.age.trim().toIntOrNull()?.let { o.put("age", it) } ?: o.putText("age", c.age)
        when (c.gender) { Gender.MALE -> o.put("gender", "male"); Gender.FEMALE -> o.put("gender", "female"); Gender.DIVERSE -> o.put("gender", "non-binary"); else -> {} }
        val shapes = JSONObject().putText("face", c.face).putText("eyes", c.eyes).putText("body", c.body)
        if (shapes.length() > 0) o.put("shapes", shapes)
        o.putText("skin", c.skin).putText("hair", c.hair).putText("details", c.appearance)
        clothing(outfit)?.let { o.put("clothing", it) }
        o.putText("pose", m.pose).putText("face_expression", m.expression)
        return o
    }

    /** Das ganze Bild als JSON-Text */
    fun build(
        style: String, figures: List<JSONObject> = emptyList(), action: String = "", background: String = "", lights: String = "", camera: String = "",
    ): String {
        val o = JSONObject().putText("style", style)
        if (figures.isNotEmpty()) o.put("figures", JSONArray(figures))
        o.putText("action", action).putText("background", background).putText("lights", lights).putText("camera", camera)
        return o.toString(2)
    }

    fun locationPreview(style: String, item: FundusItem) =
        build(style, action = "empty scene, no people", background = item.background.ifBlank { item.tags }, lights = item.lights, camera = "wide establishing shot")

    /** Eine Person als Model, eine einzige Ansicht von vorn */
    fun clothingPreview(style: String, item: FundusItem): String {
        val f = JSONObject().put("gender", if (item.model == "1boy") "male" else "female").put("age", 25)
        clothing(item)?.let { f.put("clothing", it) }
        f.put("pose", "standing straight, arms relaxed at the sides")
        return build(style, listOf(f), background = "plain white studio background", lights = "soft even studio light", camera = "full body shot, front view, single person")
    }

    fun stylePreview(style: String) = build(
        style, listOf(JSONObject().put("gender", "female").put("age", 25).put("hair", "long wavy hair").put("pose", "standing in a flower field").put("face_expression", "warm smile")),
        background = "meadow full of flowers", lights = "golden afternoon sun", camera = "upper body shot",
    )

    /** Vorschau des Aussehens: Oberkörper, Gesicht gut zu sehen */
    fun appearancePreview(style: String, c: Character, outfit: FundusItem?) = build(
        style, listOf(figure(c, outfit, Moment("standing, relaxed, looking at the camera", "friendly smile"))),
        background = "plain neutral studio background", lights = "soft even studio light", camera = "upper body portrait, front view, single person",
    )

    /** Anprobieren: die Figur in einem Outfit, ganzer Körper, eine Ansicht */
    fun tryOn(style: String, c: Character, outfit: FundusItem) = build(
        style, listOf(figure(c, outfit, Moment("standing straight, looking at the camera", "calm"))),
        background = "plain white studio background", lights = "soft even studio light", camera = "full body shot, front view, single person",
    )

    /** Aussehen in einer Zeile – für die KI */
    fun looks(c: Character, outfit: FundusItem?) = join(
        c.genderWord, c.age.takeIf { it.isNotBlank() }?.let { "age $it" }, c.face, c.eyes, c.body, c.skin, c.hair, c.appearance,
        outfit?.pieces?.joinToString(", ") { it.second }?.takeIf { it.isNotBlank() }?.let { "wearing $it" },
    )

    const val CLOTHING_PREVIEW_NEGATIVE = "multiple views, reference sheet, character sheet, mannequin"
}

/** Welche Liste im Fundus */
enum class FundusKind { LOCATION, CLOTHING }
