package dev.abhay.hypericon.glyph

import kotlin.math.roundToInt

/**
 * Glyph contrast (an icon-editor edit), as pure pixel math on alpha masks.
 *
 * Forced-mono glyphs turn brightness into opacity, so an icon made of two saturated colours of
 * similar brightness comes out with both areas half opaque, and the plate and glyph colours blend
 * into a low-contrast icon. This splits the visible pixels into two opacity clusters (Otsu) and
 * moves a levels black point toward the lower cluster's mean and the white point toward the upper
 * one's, so at full strength each cluster becomes solid and edges stay anti-aliased.
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
        val histogram = LongArray(256)
        val inset = size / 6
        for (y in inset until size - inset) {
            val row = y * size
            for (x in inset until size - inset) histogram[alpha[row + x].toInt() and 0xFF]++
        }
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
        if (high - low < MIN_SEPARATION) return null

        val a = amount.coerceAtMost(1f)
        val black = (low * a).roundToInt()
        val white = (255 - (255 - high) * a).roundToInt()
        return if (white > black) Levels(black, white) else null
    }

    /** [alpha] with the contrast [amount] (0..1) applied; an unchanged copy when there's nothing to do. */
    fun apply(alpha: ByteArray, size: Int, amount: Float): ByteArray {
        val levels = levels(alpha, size, amount) ?: return alpha.copyOf()
        val range = (levels.white - levels.black).toFloat()
        val lut = ByteArray(256) { v -> ((v - levels.black) * 255f / range).roundToInt().coerceIn(0, 255).toByte() }
        return ByteArray(alpha.size) { i -> lut[alpha[i].toInt() and 0xFF] }
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

    /** Clusters closer than this (in alpha levels) are treated as one: noise, not two colours. */
    private const val MIN_SEPARATION = 8f
}
