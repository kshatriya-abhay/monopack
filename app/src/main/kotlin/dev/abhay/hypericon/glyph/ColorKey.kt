package dev.abhay.hypericon.glyph

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Colour keying for generated glyphs: pixels close to a background colour become transparent,
 * everything else is kept at full opacity (so a red logo on green keeps full contrast).
 *
 * Pure Kotlin on ARGB int arrays (`size × size`, row-major); the visible viewport starts `inset`
 * pixels in from every side.
 */
object ColorKey {
    /** Colour distance at or below which a pixel counts as background. */
    const val LO = 24f

    /** Colour distance at or above which a pixel counts as glyph (smooth ramp in between). */
    const val HI = 64f

    /** An edge colour is background when this share of the ring is within [EDGE_RADIUS] of it. */
    const val EDGE_SUPPORT = 0.15f
    const val EDGE_RADIUS = 48f

    fun dist(a: Int, b: Int): Float {
        val dr = ((a shr 16 and 0xFF) - (b shr 16 and 0xFF)).toFloat()
        val dg = ((a shr 8 and 0xFF) - (b shr 8 and 0xFF)).toFloat()
        val db = ((a and 0xFF) - (b and 0xFF)).toFloat()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /** 0 at [LO], 1 at [HI], smoothstep in between. */
    fun smooth(d: Float): Float {
        val t = ((d - LO) / (HI - LO)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Colours covering at least [minShare] of [pixels] (4-level buckets), as bucket means. */
    fun dominantColors(pixels: IntArray, minShare: Float): IntArray {
        val buckets = HashMap<Int, LongArray>() // key → [count, r, g, b]
        for (c in pixels) {
            val acc = buckets.getOrPut(c and 0xFCFCFC) { LongArray(4) }
            acc[0]++; acc[1] += (c shr 16 and 0xFF).toLong(); acc[2] += (c shr 8 and 0xFF).toLong(); acc[3] += (c and 0xFF).toLong()
        }
        val min = minShare * pixels.size
        return buckets.values.filter { it[0] >= min }.map { rgbOf(it) }.toIntArray()
    }

    /**
     * The background colours seen on the viewport edge: every opaque [ring] colour that at least
     * [EDGE_SUPPORT] of the ring ([ringLength] pixels, including transparent ones) is close to.
     * A logo that merely touches the edge covers too little of it to count.
     */
    fun edgePalette(ring: IntArray, ringLength: Int): IntArray {
        val min = EDGE_SUPPORT * ringLength
        return ring.filter { c -> ring.count { dist(it, c) < EDGE_RADIUS } >= min }
            .map { (it and 0xFCFCFC) or 0x020202 }
            .distinct()
            .toIntArray()
    }

    /**
     * Glyph alpha (0..1) for each pixel: source alpha × how far its colour is from the nearest
     * [palette] colour. Pixels outside the viewport are 0.
     */
    fun keyAlpha(argb: IntArray, size: Int, inset: Int, palette: IntArray): FloatArray {
        val out = FloatArray(size * size)
        val cache = HashMap<Int, Float>()
        for (y in inset until size - inset) {
            for (x in inset until size - inset) {
                val i = y * size + x
                val c = argb[i]
                val a = (c ushr 24) / 255f
                if (a == 0f) continue
                val rgb = c and 0xFFFFFF
                val key = cache.getOrPut(rgb) {
                    if (palette.isEmpty()) 1f else smooth(palette.minOf { dist(rgb, it) })
                }
                out[i] = a * key
            }
        }
        return out
    }

    /** Share of viewport pixels that are mostly glyph (alpha > 0.5). */
    fun coverage(alpha: FloatArray, size: Int, inset: Int): Float {
        var hits = 0
        for (y in inset until size - inset) for (x in inset until size - inset) if (alpha[y * size + x] > 0.5f) hits++
        val side = size - 2 * inset
        return hits.toFloat() / (side * side)
    }

    /** The most common colour (16-level buckets) among mostly-glyph pixels. */
    fun dominantColor(argb: IntArray, alpha: FloatArray): Int? {
        val buckets = HashMap<Int, LongArray>()
        for (i in argb.indices) {
            if (alpha[i] <= 0.5f) continue
            val c = argb[i]
            val acc = buckets.getOrPut(c and 0xF0F0F0) { LongArray(4) }
            acc[0]++; acc[1] += (c shr 16 and 0xFF).toLong(); acc[2] += (c shr 8 and 0xFF).toLong(); acc[3] += (c and 0xFF).toLong()
        }
        return buckets.values.maxByOrNull { it[0] }?.let { rgbOf(it) }
    }

    /**
     * Removes thin remnants such as anti-aliased outlines: the mostly-glyph mask is eroded by [r]
     * and dilated by `r + 1`, and alpha outside the result is cleared.
     */
    fun open(alpha: FloatArray, size: Int, r: Int): FloatArray {
        var mask = BooleanArray(alpha.size) { alpha[it] > 0.5f }
        repeat(r) { mask = erode(mask, size) }
        repeat(r + 1) { mask = dilate(mask, size) }
        return FloatArray(alpha.size) { if (mask[it]) alpha[it] else 0f }
    }

    fun erode(m: BooleanArray, size: Int) = BooleanArray(m.size) { i ->
        val x = i % size
        val y = i / size
        m[i] && (x == 0 || m[i - 1]) && (x == size - 1 || m[i + 1]) &&
            (y == 0 || m[i - size]) && (y == size - 1 || m[i + size])
    }

    fun dilate(m: BooleanArray, size: Int) = BooleanArray(m.size) { i ->
        val x = i % size
        val y = i / size
        m[i] || (x > 0 && m[i - 1]) || (x < size - 1 && m[i + 1]) ||
            (y > 0 && m[i - size]) || (y < size - 1 && m[i + size])
    }

    private fun rgbOf(acc: LongArray): Int {
        val n = max(1L, acc[0])
        return ((acc[1] / n).toInt() shl 16) or ((acc[2] / n).toInt() shl 8) or (acc[3] / n).toInt()
    }
}
