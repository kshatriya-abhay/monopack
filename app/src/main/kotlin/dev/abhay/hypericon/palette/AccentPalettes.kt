package dev.abhay.hypericon.palette

import android.R.color as sys
import android.content.Context
import androidx.annotation.ColorRes
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle

/** The five Material You tonal palettes (Android's `system_accent1..3` / `system_neutral1..2`). */
enum class PaletteSlot { ACCENT1, ACCENT2, ACCENT3, NEUTRAL1, NEUTRAL2 }

/** One colour: a palette and a tone (L*, 0 = black … 100 = white). */
data class ToneRef(val slot: PaletteSlot, val tone: Int)

/** Plate and glyph colours of a themed icon. */
data class TonePair(val background: ToneRef, val foreground: ToneRef)

/**
 * Which tones each accent uses, per icon style. Primary matches AOSP's themed icons
 * (iconloaderlib values-v31 / values-night-v31: accent1_100/700 light, accent2_800/accent1_200
 * dark); the others follow the same tone steps. Shade `_100` is tone 90, `_200` 80, `_700` 30 and
 * `_800` 20. Keep all tuning in this table.
 */
object AccentPalettes {
    private fun ref(slot: PaletteSlot, tone: Int) = ToneRef(slot, tone)

    val TABLE: Map<Accent, Map<IconStyle, TonePair>> = mapOf(
        Accent.PRIMARY to mapOf(
            IconStyle.LIGHT to TonePair(ref(PaletteSlot.ACCENT1, 90), ref(PaletteSlot.ACCENT1, 30)),
            IconStyle.DARK to TonePair(ref(PaletteSlot.ACCENT2, 20), ref(PaletteSlot.ACCENT1, 80)),
        ),
        Accent.SECONDARY to mapOf(
            IconStyle.LIGHT to TonePair(ref(PaletteSlot.ACCENT2, 90), ref(PaletteSlot.ACCENT2, 30)),
            IconStyle.DARK to TonePair(ref(PaletteSlot.NEUTRAL2, 20), ref(PaletteSlot.ACCENT2, 80)),
        ),
        Accent.TERTIARY to mapOf(
            IconStyle.LIGHT to TonePair(ref(PaletteSlot.ACCENT3, 90), ref(PaletteSlot.ACCENT3, 30)),
            IconStyle.DARK to TonePair(ref(PaletteSlot.ACCENT3, 20), ref(PaletteSlot.ACCENT3, 80)),
        ),
    )

    /** Resolves the whole table against a colour source. */
    fun resolve(tones: (ToneRef) -> Int): Map<Accent, Map<IconStyle, IconPalette>> =
        TABLE.mapValues { (_, styles) ->
            styles.mapValues { (_, pair) -> IconPalette(tones(pair.background), tones(pair.foreground)) }
        }
}

/**
 * Reads the current wallpaper-derived colours from the system's tonal palettes. The `system_*`
 * resources resolve to the same value in day and night, so the icon style never depends on the
 * app's own theme.
 */
class PaletteProvider(private val context: Context) {
    fun load(): Map<Accent, Map<IconStyle, IconPalette>> =
        AccentPalettes.resolve { context.getColor(systemColor(it)) }

    companion object {
        @ColorRes
        fun systemColor(ref: ToneRef): Int {
            val shades = SYSTEM_COLORS.getValue(ref.slot)
            return shades[ref.tone] ?: error("No system shade for tone ${ref.tone}")
        }

        /** Only the tones the table uses; shade `_N` is tone `100 - N / 10`. */
        private val SYSTEM_COLORS: Map<PaletteSlot, Map<Int, Int>> = mapOf(
            PaletteSlot.ACCENT1 to mapOf(
                90 to sys.system_accent1_100, 80 to sys.system_accent1_200,
                30 to sys.system_accent1_700, 20 to sys.system_accent1_800,
            ),
            PaletteSlot.ACCENT2 to mapOf(
                90 to sys.system_accent2_100, 80 to sys.system_accent2_200,
                30 to sys.system_accent2_700, 20 to sys.system_accent2_800,
            ),
            PaletteSlot.ACCENT3 to mapOf(
                90 to sys.system_accent3_100, 80 to sys.system_accent3_200,
                30 to sys.system_accent3_700, 20 to sys.system_accent3_800,
            ),
            PaletteSlot.NEUTRAL1 to mapOf(
                90 to sys.system_neutral1_100, 80 to sys.system_neutral1_200,
                30 to sys.system_neutral1_700, 20 to sys.system_neutral1_800,
            ),
            PaletteSlot.NEUTRAL2 to mapOf(
                90 to sys.system_neutral2_100, 80 to sys.system_neutral2_200,
                30 to sys.system_neutral2_700, 20 to sys.system_neutral2_800,
            ),
        )
    }
}
