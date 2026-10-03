package dev.abhay.monopack.palette

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.materialkolor.hct.Hct
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
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
    fun `with auto night mode off the base is used in both modes`() {
        val edit = IconEdit(base = IconStyle.DARK, autoNight = false)
        assertThat(IconEdits.resolve(pairs, IconStyle.LIGHT, edit)).isEqualTo(dark)
        assertThat(IconEdits.resolve(pairs, IconStyle.DARK, edit)).isEqualTo(dark)
    }

    @Test
    fun `auto night mode uses the other style in dark mode with the same adjustments`() {
        val lightBase = IconEdit(base = IconStyle.LIGHT, inverted = true)
        assertThat(IconEdits.resolve(pairs, IconStyle.LIGHT, lightBase)).isEqualTo(IconEdits.shown(light, inverted = true))
        assertThat(IconEdits.resolve(pairs, IconStyle.DARK, lightBase)).isEqualTo(IconEdits.shown(dark, inverted = true))
        // A Dark base in light mode stays the "opposite" one in dark mode.
        val darkBase = IconEdit(base = IconStyle.DARK)
        assertThat(IconEdits.resolve(pairs, IconStyle.LIGHT, darkBase)).isEqualTo(dark)
        assertThat(IconEdits.resolve(pairs, IconStyle.DARK, darkBase)).isEqualTo(light)
    }

    private fun tone(color: Int) = Hct.fromInt(color).tone

    @Test
    fun `glyph and plate offsets change only their colour and keep its hue`() {
        val glyphOnly = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.LIGHT, glyphToneOffset = -6))!!
        assertThat(glyphOnly.background).isEqualTo(light.background)
        assertThat(tone(glyphOnly.foreground)).isWithin(0.6).of(tone(light.foreground) - 6)
        assertThat(hueDistance(Hct.fromInt(glyphOnly.foreground).hue, Hct.fromInt(light.foreground).hue)).isLessThan(2.0)

        val plateOnly = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.DARK, plateToneOffset = 5))!!
        assertThat(plateOnly.foreground).isEqualTo(dark.foreground)
        assertThat(tone(plateOnly.background)).isWithin(0.6).of(tone(dark.background) + 5)

        val both = IconEdits.resolve(pairs, IconStyle.LIGHT, IconEdit(IconStyle.LIGHT, glyphToneOffset = -4, plateToneOffset = -3))!!
        assertThat(tone(both.foreground)).isWithin(0.6).of(tone(light.foreground) - 4)
        assertThat(tone(both.background)).isWithin(0.6).of(tone(light.background) - 3)
    }

    @Test
    fun `tone is clamped at black`() {
        val edited = IconEdits.withToneOffsets(dark, glyph = 0, plate = -100)
        assertThat(tone(edited.background)).isWithin(0.6).of(0.0)
    }

    @Test
    fun `allowed offsets always keep enough contrast`() {
        val seeds = SeedPresets.AOSP + (0 until 360 step 15).map { SeedColors.custom(it.toDouble()) }
        for (seed in seeds) for ((accent, styles) in seed.palettes()) for ((style, pair) in styles) {
            for (layer in IconEdits.Layer.entries) {
                val range = IconEdits.allowedOffsets(pair, layer)
                assertWithMessage("${seed.name} $accent $style $layer").that(range.contains(0)).isTrue()
                for (offset in listOf(range.first, range.last)) {
                    val edited = if (layer == IconEdits.Layer.GLYPH) IconEdits.withToneOffsets(pair, offset, 0) else IconEdits.withToneOffsets(pair, 0, offset)
                    assertWithMessage("${seed.name} $accent $style $layer $offset")
                        .that(Contrast.ratio(edited.background, edited.foreground)).isAtLeast(IconEdits.MIN_CONTRAST)
                }
                assertThat(range.first).isAtLeast(IconEdits.MIN_OFFSET)
                assertThat(range.last).isAtMost(IconEdits.MAX_OFFSET)
            }
        }
    }

    @Test
    fun `moving the colours towards each other is limited by contrast`() {
        // Material's own pairs allow the full glyph range (tone 30 → 50 on tone 90 still has > 3:1).
        assertThat(IconEdits.allowedOffsets(light, IconEdits.Layer.GLYPH)).isEqualTo(IconEdits.MIN_OFFSET..IconEdits.MAX_OFFSET)
        // A closer pair (glyph tone 45 on plate tone 90) is capped well before +20 and before −20.
        val close = IconPalette(Hct.from(260.0, 16.0, 90.0).toInt(), Hct.from(260.0, 36.0, 45.0).toInt())
        val glyph = IconEdits.allowedOffsets(close, IconEdits.Layer.GLYPH)
        assertThat(glyph.last).isIn(1..10)
        val atLimit = IconEdits.withToneOffsets(close, glyph.last + 1, 0)
        assertThat(Contrast.ratio(atLimit.background, atLimit.foreground)).isLessThan(IconEdits.MIN_CONTRAST)
        val plate = IconEdits.allowedOffsets(close, IconEdits.Layer.PLATE)
        assertThat(plate.first).isGreaterThan(IconEdits.MIN_OFFSET)
    }

    @Test
    fun `each range depends on the other slider`() {
        val close = IconPalette(Hct.from(260.0, 16.0, 90.0).toInt(), Hct.from(260.0, 36.0, 45.0).toInt())
        val alone = IconEdits.allowedOffsets(close, IconEdits.Layer.GLYPH)
        val withDarkerPlate = IconEdits.allowedOffsets(close, IconEdits.Layer.GLYPH, otherOffset = -6)
        assertThat(withDarkerPlate.last).isLessThan(alone.last)
        // The current value stays inside its range even when 0 no longer is.
        val shifted = IconEdits.allowedOffsets(close, IconEdits.Layer.GLYPH, otherOffset = 0, current = -15)
        assertThat(shifted).contains(-15)
    }

    @Test
    fun `a new palette recolours an edited icon but keeps base and offsets`() {
        val other = SeedPresets.AOSP[0].palettes().getValue(Accent.TERTIARY)
        val edit = IconEdit(IconStyle.DARK, plateToneOffset = -4)
        val resolved = IconEdits.resolve(other, IconStyle.LIGHT, edit)!!
        val base = other.getValue(IconStyle.DARK)
        assertThat(resolved.foreground).isEqualTo(base.foreground)
        assertThat(tone(resolved.background)).isWithin(0.6).of(tone(base.background) - 4)
    }

    @Test
    fun `offsets apply to the colours as shown after inverting`() {
        val edit = IconEdit(IconStyle.DARK, glyphToneOffset = -5, inverted = true)
        val resolved = IconEdits.resolve(pairs, IconStyle.LIGHT, edit)!!
        // Inverted Dark icon: the plate is the Dark glyph colour, the glyph the Dark plate colour.
        assertThat(resolved.background).isEqualTo(dark.foreground)
        assertThat(tone(resolved.foreground)).isWithin(0.6).of(tone(dark.background) - 5)
    }

    @Test
    fun `missing pairs resolve to null`() {
        assertThat(IconEdits.resolve(emptyMap(), IconStyle.LIGHT, null)).isNull()
    }
}
