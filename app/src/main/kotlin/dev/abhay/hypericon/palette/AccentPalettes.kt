package dev.abhay.hypericon.palette

import android.R.color as sys
import android.content.Context
import androidx.annotation.ColorRes
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle

/** A background/glyph pair of Material You system color resources. */
data class TonePair(@param:ColorRes val background: Int, @param:ColorRes val foreground: Int)

/**
 * Which system tones each accent uses, per icon style. Primary matches AOSP's themed icons
 * (iconloaderlib values-v31 / values-night-v31); the others follow the same tone steps.
 * Keep all tuning in this table.
 */
object AccentPalettes {
    val TABLE: Map<Accent, Map<IconStyle, TonePair>> = mapOf(
        Accent.PRIMARY to mapOf(
            IconStyle.LIGHT to TonePair(sys.system_accent1_100, sys.system_accent1_700),
            IconStyle.DARK to TonePair(sys.system_accent2_800, sys.system_accent1_200),
        ),
        Accent.SECONDARY to mapOf(
            IconStyle.LIGHT to TonePair(sys.system_accent2_100, sys.system_accent2_700),
            IconStyle.DARK to TonePair(sys.system_neutral2_800, sys.system_accent2_200),
        ),
        Accent.TERTIARY to mapOf(
            IconStyle.LIGHT to TonePair(sys.system_accent3_100, sys.system_accent3_700),
            IconStyle.DARK to TonePair(sys.system_accent3_800, sys.system_accent3_200),
        ),
    )
}

/**
 * Reads the current wallpaper-derived colors. The `system_*` tone resources resolve to the same
 * value in day and night, so the icon style never depends on the app's own theme.
 */
class PaletteProvider(private val context: Context) {
    fun load(): Map<Accent, Map<IconStyle, IconPalette>> =
        AccentPalettes.TABLE.mapValues { (_, styles) ->
            styles.mapValues { (_, pair) ->
                IconPalette(context.getColor(pair.background), context.getColor(pair.foreground))
            }
        }
}
