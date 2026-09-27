package dev.abhay.hypericon.palette

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.materialkolor.hct.Hct
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.IconStyle
import kotlin.math.abs
import kotlin.math.min
import org.junit.Test

class SeedsTest {
    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return min(d, 360 - d)
    }

    @Test
    fun `every preset and custom hue gives readable icons in every accent and style`() {
        val seeds = SeedPresets.AOSP + (0 until 360 step 15).map { SeedColors.custom(it.toDouble()) }
        for (seed in seeds) {
            for ((accent, styles) in seed.palettes()) {
                for ((style, palette) in styles) {
                    val ratio = Contrast.ratio(palette.background, palette.foreground)
                    assertWithMessage("${seed.name} ${seed.color.toString(16)} $accent $style").that(ratio).isAtLeast(4.5)
                }
            }
        }
    }

    @Test
    fun `custom seeds keep their hue in the primary accent`() {
        for (hue in listOf(10.0, 95.0, 180.0, 265.0, 330.0)) {
            val light = SeedColors.custom(hue).palettes().getValue(Accent.PRIMARY).getValue(IconStyle.LIGHT)
            assertThat(hueDistance(Hct.fromInt(light.foreground).hue, hue)).isLessThan(15.0)
        }
    }

    @Test
    fun `light icons are light plates with dark glyphs and dark icons the reverse`() {
        val palettes = SeedPresets.DEFAULT.palettes().getValue(Accent.PRIMARY)
        val light = palettes.getValue(IconStyle.LIGHT)
        val dark = palettes.getValue(IconStyle.DARK)
        assertThat(Hct.fromInt(light.background).tone).isWithin(1.0).of(90.0)
        assertThat(Hct.fromInt(light.foreground).tone).isWithin(1.0).of(30.0)
        assertThat(Hct.fromInt(dark.background).tone).isWithin(1.0).of(20.0)
        assertThat(Hct.fromInt(dark.foreground).tone).isWithin(1.0).of(80.0)
    }

    @Test
    fun `monochromatic preset is grey`() {
        val mono = SeedPresets.AOSP.single { it.style == SeedStyle.MONOCHROME }
        for (styles in mono.palettes().values) for (palette in styles.values) {
            assertThat(Hct.fromInt(palette.background).chroma).isLessThan(4.0)
            assertThat(Hct.fromInt(palette.foreground).chroma).isLessThan(4.0)
        }
    }

    @Test
    fun `hex parsing accepts common forms and rejects junk`() {
        assertThat(SeedColors.parseHex("#96CBFF")).isEqualTo(0xFF96CBFF.toInt())
        assertThat(SeedColors.parseHex("96cbff")).isEqualTo(0xFF96CBFF.toInt())
        assertThat(SeedColors.parseHex(" #abc ")).isEqualTo(0xFFAABBCC.toInt())
        assertThat(SeedColors.parseHex("#12345")).isNull()
        assertThat(SeedColors.parseHex("zzzzzz")).isNull()
        assertThat(SeedColors.toHex(0xFF96CBFF.toInt())).isEqualTo("#96CBFF")
    }

    @Test
    fun `near greys are flagged, colourful seeds are not`() {
        assertThat(SeedColors.isNearGrey(0xFF808080.toInt())).isTrue()
        assertThat(SeedColors.isNearGrey(0xFF7F8285.toInt())).isTrue()
        assertThat(SeedColors.isNearGrey(0xFF96CBFF.toInt())).isFalse()
        assertThat(SeedPresets.AOSP.filter { it.style != SeedStyle.MONOCHROME }.none { SeedColors.isNearGrey(it.color) }).isTrue()
    }

    @Test
    fun `table tones map onto system shades`() {
        for (styles in AccentPalettes.TABLE.values) for (pair in styles.values) {
            PaletteProvider.systemColor(pair.background)
            PaletteProvider.systemColor(pair.foreground)
        }
    }
}
