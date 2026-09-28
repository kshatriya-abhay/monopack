package dev.abhay.monopack.palette

import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import kotlin.math.abs

/**
 * Detects when the system isn't providing wallpaper colours: the `system_accent*` resources then
 * keep AOSP's framework defaults (frameworks/base core/res values/colors.xml).
 */
object DefaultPalette {
    private val FRAMEWORK_DEFAULTS: Map<Pair<Accent, IconStyle>, IconPalette> = mapOf(
        // accent1_100 / accent1_700
        (Accent.PRIMARY to IconStyle.LIGHT) to IconPalette(0xFFD9E2FF.toInt(), 0xFF2F4578.toInt()),
        // accent2_800 / accent1_200
        (Accent.PRIMARY to IconStyle.DARK) to IconPalette(0xFF2A3042.toInt(), 0xFFB0C6FF.toInt()),
        // accent3_100 / accent3_700
        (Accent.TERTIARY to IconStyle.LIGHT) to IconPalette(0xFFFDD7FA.toInt(), 0xFF593D59.toInt()),
    )

    /** Allowed difference per channel, for rounding. */
    private const val TOLERANCE = 4

    fun looksDefault(system: Map<Accent, Map<IconStyle, IconPalette>>): Boolean =
        FRAMEWORK_DEFAULTS.all { (key, expected) ->
            val actual = system[key.first]?.get(key.second) ?: return false
            close(actual.background, expected.background) && close(actual.foreground, expected.foreground)
        }

    private fun close(a: Int, b: Int): Boolean =
        (0..16 step 8).all { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= TOLERANCE }
}
