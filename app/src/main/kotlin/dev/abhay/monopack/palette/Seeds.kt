package dev.abhay.monopack.palette

import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle

/** How a seed colour is expanded into the five tonal palettes (Material scheme variants). */
enum class SeedStyle {
    /** Android's default for wallpaper colours: muted, seed hue at a fixed chroma. */
    TONAL_SPOT,

    /** Used by AOSP's preset "basic colours": more saturated accents, grey neutrals. */
    RAINBOW,

    /** Greys only. */
    MONOCHROME,
}

/** A user-chosen colour source for the icon palettes. */
data class Seed(val color: Int, val style: SeedStyle, val name: String) {
    /** The five palettes' colours for the icon table. */
    fun palettes(): Map<Accent, Map<IconStyle, IconPalette>> {
        val scheme = SeedColors.scheme(this)
        return AccentPalettes.resolve { ref -> scheme.palette(ref.slot).tone(ref.tone) }
    }
}

/**
 * AOSP's preset colours: the ThemePicker stub's "basic colours" (Rainbow style) plus
 * Monochromatic, as offered by Pixel's Wallpaper & style.
 */
object SeedPresets {
    val AOSP: List<Seed> = listOf(
        Seed(0xFFFFB2B5.toInt(), SeedStyle.RAINBOW, "Red"),
        Seed(0xFFFFB868.toInt(), SeedStyle.RAINBOW, "Orange"),
        Seed(0xFFE9C44A.toInt(), SeedStyle.RAINBOW, "Yellow"),
        Seed(0xFFBAF293.toInt(), SeedStyle.RAINBOW, "Green"),
        Seed(0xFF96CBFF.toInt(), SeedStyle.RAINBOW, "Blue"),
        Seed(0xFFCBBFFF.toInt(), SeedStyle.RAINBOW, "Purple"),
        Seed(0xFFF5ACFB.toInt(), SeedStyle.RAINBOW, "Magenta"),
        Seed(0xFFFFFF00.toInt(), SeedStyle.MONOCHROME, "Monochromatic"),
    )

    val DEFAULT: Seed = AOSP[4]
}

/**
 * Seed-colour helpers. In Material You a seed only contributes its **hue**; each palette's chroma
 * and every tone are fixed by the scheme. So any hue is a valid seed, and a hue picker keeps the
 * user in range by construction. Only near-greys are rejected, because their hue is meaningless.
 */
object SeedColors {
    /** Chroma and tone used to display a hue (and to build a custom seed from it). */
    const val DISPLAY_CHROMA = 48.0
    const val DISPLAY_TONE = 65.0

    /** Below this chroma a colour is too grey for its hue to mean anything. */
    const val MIN_CHROMA = 8.0

    fun fromHue(hue: Double): Int = Hct.from(normalizeHue(hue), DISPLAY_CHROMA, DISPLAY_TONE).toInt()

    fun hueOf(color: Int): Double = Hct.fromInt(color).hue

    fun isNearGrey(color: Int): Boolean = Hct.fromInt(color).chroma < MIN_CHROMA

    /** A custom seed built from a hue (Tonal Spot, like wallpaper colours). */
    fun custom(hue: Double): Seed = Seed(fromHue(hue), SeedStyle.TONAL_SPOT, "Custom")

    /** Parses `#RRGGBB`, `RRGGBB`, `#RGB` or `RGB` (case-insensitive) to an opaque colour. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            else -> return null
        }
        return full.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    }

    fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun scheme(seed: Seed): DynamicScheme {
        val source = Hct.fromInt(seed.color)
        // Spec 2021 matches the palettes Android 13–15 generate.
        val spec = ColorSpec.SpecVersion.SPEC_2021
        val platform = DynamicScheme.Platform.PHONE
        return when (seed.style) {
            SeedStyle.TONAL_SPOT -> SchemeTonalSpot(source, false, 0.0, spec, platform)
            SeedStyle.RAINBOW -> SchemeRainbow(source, false, 0.0, spec, platform)
            SeedStyle.MONOCHROME -> SchemeMonochrome(source, false, 0.0, spec, platform)
        }
    }

    private fun normalizeHue(hue: Double) = ((hue % 360.0) + 360.0) % 360.0
}

private fun DynamicScheme.palette(slot: PaletteSlot): TonalPalette = when (slot) {
    PaletteSlot.ACCENT1 -> primaryPalette
    PaletteSlot.ACCENT2 -> secondaryPalette
    PaletteSlot.ACCENT3 -> tertiaryPalette
    PaletteSlot.NEUTRAL1 -> neutralPalette
    PaletteSlot.NEUTRAL2 -> neutralVariantPalette
}
