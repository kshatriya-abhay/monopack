package dev.abhay.hypericon.palette

import com.materialkolor.hct.Hct
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import kotlin.math.roundToInt

/** Resolves per-app icon edits against the committed palettes. Pure, so it's unit-tested directly. */
object IconEdits {
    const val MIN_OFFSET = -20
    const val MAX_OFFSET = 20

    /** Plate and glyph must keep at least this contrast (WCAG non-text) after an edit. */
    const val MIN_CONTRAST = 3.0

    /**
     * The colours an app is drawn with.
     *
     * @param pairs the committed selection's plate/glyph pair for each icon style.
     * @param style the icon style being shown (the global style, or the style being exported).
     */
    fun resolve(pairs: Map<IconStyle, IconPalette>, style: IconStyle, edit: IconEdit?): IconPalette? {
        val pair = pairs[edit?.base ?: style] ?: return null
        return if (edit == null) pair else apply(pair, edit)
    }

    /** Applies [edit]'s tone offset and inversion to its base [pair]. */
    fun apply(pair: IconPalette, edit: IconEdit): IconPalette {
        val shifted = withDarkToneOffset(pair, edit.darkToneOffset)
        return if (edit.inverted) IconPalette(background = shifted.foreground, foreground = shifted.background) else shifted
    }

    /** Shifts the tone of the darker colour of [pair] by [offset], keeping its hue and chroma. */
    fun withDarkToneOffset(pair: IconPalette, offset: Int): IconPalette {
        if (offset == 0) return pair
        val plateIsDark = tone(pair.background) < tone(pair.foreground)
        val dark = if (plateIsDark) pair.background else pair.foreground
        val hct = Hct.fromInt(dark)
        val shifted = Hct.from(hct.hue, hct.chroma, (hct.tone + offset).coerceIn(0.0, 100.0)).toInt()
        return if (plateIsDark) pair.copy(background = shifted) else pair.copy(foreground = shifted)
    }

    /**
     * Offsets the slider may use for [pair]: within [MIN_OFFSET]..[MAX_OFFSET], keeping at least
     * [MIN_CONTRAST] between plate and glyph and the dark colour darker than the light one.
     * Always contains 0.
     */
    fun allowedOffsets(pair: IconPalette): IntRange {
        fun ok(offset: Int): Boolean {
            val edited = withDarkToneOffset(pair, offset)
            val darkTone = minOf(tone(edited.background), tone(edited.foreground))
            val lightTone = maxOf(tone(edited.background), tone(edited.foreground))
            return darkTone < lightTone && Contrast.ratio(edited.background, edited.foreground) >= MIN_CONTRAST
        }
        var low = 0
        while (low - 1 >= MIN_OFFSET && ok(low - 1)) low--
        var high = 0
        while (high + 1 <= MAX_OFFSET && ok(high + 1)) high++
        return low..high
    }

    /** HCT tone (L*, 0..100) of a colour, rounded for display. */
    fun displayTone(color: Int): Int = tone(color).roundToInt()

    private fun tone(color: Int): Double = Hct.fromInt(color).tone
}
