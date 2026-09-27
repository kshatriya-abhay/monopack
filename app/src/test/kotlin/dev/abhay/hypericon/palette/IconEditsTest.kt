package dev.abhay.hypericon.palette

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.materialkolor.hct.Hct
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import kotlin.math.abs
import kotlin.math.min
import org.junit.Test

class IconEditsTest {
    private val palettes = SeedPresets.DEFAULT.palettes()
    private val pairs = palettes.getValue(Accent.PRIMARY)
    private val light = pairs.getValue(IconStyle.LIGHT)
    private val dark = pairs.getValue(IconStyle.DARK)

    private fun hueDistance(a: Double, b: Double) = abs(a - b).let { min(it, 360 - it) }

    @Test
    fun `no edit follows the shown style`() {
        assertThat(IconEdits.resolve(pairs, IconStyle.LIGHT, null)).isEqualTo(light)
        assertThat(IconEdits.resolve(pairs, IconStyle.DARK, null)).isEqualTo(dark)
    }

    @Test
    fun `base style is absolute`() {
        val edit = IconEdit(base = IconStyle.DARK)
        assertThat(IconEdits.resolve(pairs, IconStyle.LIGHT, edit)).isEqualTo(dark)
        assertThat(IconEdits.resolve(pairs, IconStyle.DARK, edit)).isEqualTo(dark)
    }

    @Test
    fun `offset changes only the darker colour and keeps its hue`() {
        // Light icon: the glyph is the dark colour.
        val editedLight = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.LIGHT, -6))!!
        assertThat(editedLight.background).isEqualTo(light.background)
        assertThat(Hct.fromInt(editedLight.foreground).tone).isWithin(0.6).of(Hct.fromInt(light.foreground).tone - 6)
        assertThat(hueDistance(Hct.fromInt(editedLight.foreground).hue, Hct.fromInt(light.foreground).hue)).isLessThan(2.0)

        // Dark icon: the plate is the dark colour.
        val editedDark = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.DARK, 5))!!
        assertThat(editedDark.foreground).isEqualTo(dark.foreground)
        assertThat(Hct.fromInt(editedDark.background).tone).isWithin(0.6).of(Hct.fromInt(dark.background).tone + 5)
    }

    @Test
    fun `tone is clamped at black`() {
        val edited = IconEdits.withDarkToneOffset(dark, -100)
        assertThat(Hct.fromInt(edited.background).tone).isWithin(0.6).of(0.0)
    }

    @Test
    fun `allowed offsets always keep enough contrast`() {
        val seeds = SeedPresets.AOSP + (0 until 360 step 15).map { SeedColors.custom(it.toDouble()) }
        for (seed in seeds) for ((accent, styles) in seed.palettes()) for ((style, pair) in styles) {
            val range = IconEdits.allowedOffsets(pair)
            assertWithMessage("${seed.name} $accent $style").that(range.contains(0)).isTrue()
            for (offset in listOf(range.first, range.last)) {
                val edited = IconEdits.withDarkToneOffset(pair, offset)
                assertWithMessage("${seed.name} $accent $style $offset")
                    .that(Contrast.ratio(edited.background, edited.foreground)).isAtLeast(IconEdits.MIN_CONTRAST)
            }
            assertThat(range.first).isAtLeast(IconEdits.MIN_OFFSET)
            assertThat(range.last).isAtMost(IconEdits.MAX_OFFSET)
        }
    }

    @Test
    fun `lightening the dark colour is limited by contrast`() {
        // Material's own pairs allow the full range (tone 30 → 50 on tone 90 still has > 3:1).
        assertThat(IconEdits.allowedOffsets(light)).isEqualTo(IconEdits.MIN_OFFSET..IconEdits.MAX_OFFSET)
        // A closer pair (glyph tone 45 on plate tone 90) is capped well before +20.
        val close = IconPalette(Hct.from(260.0, 16.0, 90.0).toInt(), Hct.from(260.0, 36.0, 45.0).toInt())
        val range = IconEdits.allowedOffsets(close)
        assertThat(range.last).isIn(1..10)
        val atLimit = IconEdits.withDarkToneOffset(close, range.last + 1)
        assertThat(Contrast.ratio(atLimit.background, atLimit.foreground)).isLessThan(IconEdits.MIN_CONTRAST)
    }

    @Test
    fun `a new palette recolours an edited icon but keeps base and offset`() {
        val other = SeedPresets.AOSP[0].palettes().getValue(Accent.TERTIARY)
        val edit = IconEdit(IconStyle.DARK, -4)
        val resolved = IconEdits.resolve(other, IconStyle.LIGHT, edit)!!
        val base = other.getValue(IconStyle.DARK)
        assertThat(resolved.foreground).isEqualTo(base.foreground)
        assertThat(Hct.fromInt(resolved.background).tone).isWithin(0.6).of(Hct.fromInt(base.background).tone - 4)
    }

    @Test
    fun `inverting swaps plate and glyph after the tone offset`() {
        val pair = pairs.getValue(IconStyle.DARK)
        val shifted = IconEdits.withDarkToneOffset(pair, -5)
        val inverted = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.DARK, -5, inverted = true))!!
        assertThat(inverted.background).isEqualTo(shifted.foreground)
        assertThat(inverted.foreground).isEqualTo(shifted.background)
        assertThat(IconEdits.allowedOffsets(inverted)).isEqualTo(IconEdits.allowedOffsets(shifted))
    }

    @Test
    fun `missing pairs resolve to null`() {
        assertThat(IconEdits.resolve(emptyMap(), IconStyle.LIGHT, null)).isNull()
    }
}
