package dev.abhay.monopack.data

import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.Seed
import dev.abhay.monopack.palette.SeedStyle
import org.json.JSONObject

/** A colour selection as JSON (for library records); null when it can't be read. */
object SelectionJson {
    fun encode(selection: Selection): JSONObject = JSONObject()
        .put("style", selection.style.name)
        .put("accent", selection.accent.name)
        .put("source", selection.source.name)
        .put("seedColor", selection.seed.color)
        .put("seedStyle", selection.seed.style.name)
        .put("seedName", selection.seed.name)

    fun decode(json: JSONObject?): Selection? {
        json ?: return null
        val style = IconStyle.entries.firstOrNull { it.name == json.optString("style") } ?: return null
        val accent = Accent.entries.firstOrNull { it.name == json.optString("accent") } ?: return null
        val source = ColorSource.entries.firstOrNull { it.name == json.optString("source") } ?: ColorSource.WALLPAPER
        val seedStyle = SeedStyle.entries.firstOrNull { it.name == json.optString("seedStyle") }
        val base = Selection(style, accent, source)
        return if (seedStyle != null && json.has("seedColor") && json.has("seedName")) {
            base.copy(seed = Seed(json.optInt("seedColor"), seedStyle, json.optString("seedName")))
        } else {
            base
        }
    }
}
