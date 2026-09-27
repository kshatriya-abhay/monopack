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

    /** Which colour of the icon a tone offset applies to. */
    enum class Layer { GLYPH, PLATE }

    /** Applies [edit] to its base [pair]: inversion first, then the glyph and plate tone offsets. */
    fun apply(pair: IconPalette, edit: IconEdit): IconPalette =
        withToneOffsets(shown(pair, edit.inverted), edit.glyphToneOffset, edit.plateToneOffset)

    /** [pair] as shown, with plate and glyph colours swapped when [inverted]. */
    fun shown(pair: IconPalette, inverted: Boolean): IconPalette =
        if (inverted) IconPalette(background = pair.foreground, foreground = pair.background) else pair

    /** Shifts the tones of the glyph and plate colours of [pair], keeping their hue and chroma. */
    fun withToneOffsets(pair: IconPalette, glyph: Int, plate: Int): IconPalette =
        IconPalette(background = shift(pair.background, plate), foreground = shift(pair.foreground, glyph))

    /**
     * Offsets the [layer] slider may use on [shown] (the pair after inversion) while the other
     * layer has [otherOffset]: within [MIN_OFFSET]..[MAX_OFFSET], keeping at least [MIN_CONTRAST]
     * between plate and glyph and the darker colour darker. The range is contiguous and contains
     * [current] when that's allowed (otherwise 0, or just [current] if neither is).
     */
    fun allowedOffsets(shown: IconPalette, layer: Layer, otherOffset: Int = 0, current: Int = 0): IntRange {
        val plateDarker = tone(shown.background) < tone(shown.foreground)
        fun ok(offset: Int): Boolean {
            val edited = if (layer == Layer.GLYPH) withToneOffsets(shown, offset, otherOffset) else withToneOffsets(shown, otherOffset, offset)
            val plate = tone(edited.background)
            val glyph = tone(edited.foreground)
            val ordered = if (plateDarker) plate < glyph else glyph < plate
            return ordered && Contrast.ratio(edited.background, edited.foreground) >= MIN_CONTRAST
        }
        val start = when {
            current in MIN_OFFSET..MAX_OFFSET && ok(current) -> current
            ok(0) -> 0
            else -> return current..current
        }
        var low = start
        while (low - 1 >= MIN_OFFSET && ok(low - 1)) low--
        var high = start
        while (high + 1 <= MAX_OFFSET && ok(high + 1)) high++
        return low..high
    }

    /** HCT tone (L*, 0..100) of a colour, rounded for display. */
    fun displayTone(color: Int): Int = tone(color).roundToInt()

    private fun shift(color: Int, offset: Int): Int {
        if (offset == 0) return color
        val hct = Hct.fromInt(color)
        return Hct.from(hct.hue, hct.chroma, (hct.tone + offset).coerceIn(0.0, 100.0)).toInt()
    }

    private fun tone(color: Int): Double = Hct.fromInt(color).tone
}
