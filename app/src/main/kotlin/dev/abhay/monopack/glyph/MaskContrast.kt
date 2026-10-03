package dev.abhay.monopack.glyph

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Glyph contrast (an icon-editor edit), as pure pixel math on alpha masks.
 *
 * Forced-mono glyphs turn brightness into opacity, so an icon made of two saturated colours of
 * similar brightness comes out with both areas at almost the same opacity, and the plate and glyph
 * colours blend into a low-contrast icon. This splits the visible pixels into two opacity clusters
 * (Otsu) and stretches the levels so the lower cluster fades toward transparent (plate) and the
 * upper toward opaque (glyph), evenly along the slider; at full strength each cluster is solid and
 * edges stay anti-aliased.
 *
 * Masks are square `size × size`, row-major, covering the full adaptive layer.
 */
object MaskContrast {
    const val MAX = 100

    /** Levels mapping: alpha ≤ [black] becomes 0, alpha ≥ [white] becomes 255, linear in between. */
    data class Levels(val black: Int, val white: Int)

    /**
     * The levels for [amount] (0..1), measured on the visible 72/108 of the layer. Null when the
     * mask has no two distinct clusters (nothing to separate).
     */
    fun levels(alpha: ByteArray, size: Int, amount: Float): Levels? {
        if (amount <= 0f) return null
        val (low, high) = clusters(alpha, size) ?: return null
        // Move the clusters' *output* levels evenly: at [amount] the lower mean is shown at
        // low × (1 − a) and the upper at high + (255 − high) × a, so the slider responds evenly even
        // when the two levels are close (e.g. 238 and 253 for two similarly bright colours).
        val a = amount.coerceAtMost(1f)
        val lowOut = low * (1 - a)
        val highOut = high + (255 - high) * a
        val slope = (highOut - lowOut) / (high - low)
        val black = (low - lowOut / slope).roundToInt()
        val white = (low + (255 - lowOut) / slope).roundToInt()
        return if (white > black) Levels(black, white) else null
    }

    /** [alpha] with the contrast [amount] (0..1) applied; an unchanged copy when there's nothing to do. */
    fun apply(alpha: ByteArray, size: Int, amount: Float): ByteArray {
        val levels = levels(alpha, size, amount) ?: return alpha.copyOf()
        val range = (levels.white - levels.black).toFloat()
        val lut = ByteArray(256) { v -> ((v - levels.black) * 255f / range).roundToInt().coerceIn(0, 255).toByte() }
        return ByteArray(alpha.size) { i -> lut[alpha[i].toInt() and 0xFF] }
    }

    /** The means of the two opacity clusters (Otsu) in the visible 72/108, or null if there's one. */
    private fun clusters(alpha: ByteArray, size: Int): Pair<Float, Float>? {
        val histogram = visibleHistogram(alpha, size)
        val threshold = otsu(histogram) ?: return null
        var lowCount = 0L
        var lowSum = 0L
        var highCount = 0L
        var highSum = 0L
        for (v in 0..255) {
            if (v <= threshold) {
                lowCount += histogram[v]
                lowSum += v * histogram[v]
            } else {
                highCount += histogram[v]
                highSum += v * histogram[v]
            }
        }
        if (lowCount == 0L || highCount == 0L) return null
        val low = lowSum.toFloat() / lowCount
        val high = highSum.toFloat() / highCount
        return if (high - low < MIN_SEPARATION) null else low to high
    }

    /**
     * Opacity levels of the visible 72/108, minus a thin edge band. At some sizes a full-bleed icon's
     * edge lands a fraction of a pixel inside the viewport, leaving a 1 px semi-transparent rim; a
     * few thousand rim pixels at ~0 outweigh the split between the icon's own two colours when those
     * are close (Eatsure: 238 vs 253 at 464 px), so the slider would stretch the wrong pair.
     */
    private fun visibleHistogram(alpha: ByteArray, size: Int): LongArray {
        val histogram = LongArray(256)
        val inset = size / 6 + ceil(size * EDGE_MARGIN).toInt()
        for (y in inset until size - inset) {
            val row = y * size
            for (x in inset until size - inset) histogram[alpha[row + x].toInt() and 0xFF]++
        }
        return histogram
    }

    /** Otsu's threshold: the level that best splits [histogram] into two classes; null if flat. */
    fun otsu(histogram: LongArray): Int? {
        val total = histogram.sum()
        if (total == 0L) return null
        var sumAll = 0.0
        for (v in 0..255) sumAll += v * histogram[v].toDouble()
        var sumLow = 0.0
        var countLow = 0L
        var best = -1.0
        var threshold: Int? = null
        for (t in 0..254) {
            countLow += histogram[t]
            if (countLow == 0L) continue
            val countHigh = total - countLow
            if (countHigh == 0L) break
            sumLow += t * histogram[t].toDouble()
            val meanLow = sumLow / countLow
            val meanHigh = (sumAll - sumLow) / countHigh
            val between = countLow.toDouble() * countHigh * (meanLow - meanHigh) * (meanLow - meanHigh)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        return threshold
    }

    /** The edge band left out of the measurement, as a fraction of the layer (≈ 2 px at 100 px). */
    private const val EDGE_MARGIN = 0.02f

    /** Clusters closer than this (in alpha levels) are treated as one: noise, not two colours. */
    private const val MIN_SEPARATION = 8f
}
