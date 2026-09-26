package dev.abhay.hypericon.glyph

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure pixel math for glyph extraction, kept free of Android types so it can be unit-tested.
 *
 * All buffers are square `size × size`, row-major. The "viewport" is the visible part of an
 * adaptive-icon layer: the central 2/3, i.e. `inset = size / 6` pixels in from every side.
 *
 * The contrast-stretch-and-flip approach follows AOSP Launcher3's MonochromeIconFactory
 * (Apache License 2.0).
 */
object MonoLevels {

    /** Gray level per pixel: the average of R, G and B (as AOSP does). Alpha is ignored. */
    fun luminance(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val c = argb[i]
        (((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3).toByte()
    }

    /** The alpha channel of each pixel. */
    fun alpha(argb: IntArray): ByteArray = ByteArray(argb.size) { i -> (argb[i] ushr 24).toByte() }

    /**
     * Turns a flattened gray image into a glyph mask:
     * 1. stretches the viewport's gray range to 0..255,
     * 2. flips it if the ring just inside the viewport edge (the visible background) would be
     *    more opaque than 50%, so the background always ends up transparent,
     * 3. clears everything outside the viewport.
     *
     * A flat (single-level) viewport yields an empty mask.
     */
    fun toGlyphMask(gray: ByteArray, size: Int, inset: Int, ringWidth: Int): ByteArray {
        val out = ByteArray(size * size)
        val end = size - inset
        var min = 255
        var max = 0
        for (y in inset until end) {
            for (x in inset until end) {
                val v = gray[y * size + x].toInt() and 0xFF
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        if (min >= max) return out

        val range = (max - min).toFloat()
        var sum = 0L
        var count = 0
        for (y in inset until end) {
            for (x in inset until end) {
                if (inRing(x, y, inset, end, ringWidth)) {
                    sum += gray[y * size + x].toInt() and 0xFF
                    count++
                }
            }
        }
        val edgeMapped = (sum.toFloat() / count - min) / range
        val flip = edgeMapped > 0.5f

        for (y in inset until end) {
            for (x in inset until end) {
                val i = y * size + x
                val stretched = (((gray[i].toInt() and 0xFF) - min) * 255f / range).roundToInt()
                out[i] = (if (flip) 255 - stretched else stretched).toByte()
            }
        }
        return out
    }

    /**
     * Clears faint residue (anti-aliased edges of a removed plate, soft gradients): levels at or
     * below [floor] become 0 and the rest are rescaled so full opacity stays 255.
     */
    fun denoise(mask: ByteArray, floor: Int): ByteArray = ByteArray(mask.size) { i ->
        val v = mask[i].toInt() and 0xFF
        if (v <= floor) 0 else ((v - floor) * 255f / (255 - floor)).roundToInt().toByte()
    }

    /** Fraction of viewport pixels whose value is ≥ [threshold]. */
    fun coverage(mask: ByteArray, size: Int, inset: Int, threshold: Int): Float {
        val end = size - inset
        var hits = 0
        for (y in inset until end) {
            for (x in inset until end) {
                if ((mask[y * size + x].toInt() and 0xFF) >= threshold) hits++
            }
        }
        val side = end - inset
        return hits.toFloat() / (side * side)
    }

    /** Half-open pixel bounds [left, right) × [top, bottom). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    /** Bounds of all pixels with value ≥ [threshold], or null if there are none. */
    fun boundingBox(mask: ByteArray, size: Int, threshold: Int): Box? {
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if ((mask[y * size + x].toInt() and 0xFF) >= threshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else Box(left, top, right + 1, bottom + 1)
    }

    /** Scale about the glyph center, then move that center to the middle of the layer. */
    data class Fit(val scale: Float, val dx: Float, val dy: Float)

    /**
     * Computes the transform that centers [box] in the layer and scales it so its longer side is
     * [targetFraction] of the viewport, with the scale clamped to [minScale]..[maxScale].
     * Apply as: translate(-box.center) → scale(scale) → translate(size / 2).
     */
    fun fitToTarget(
        box: Box,
        size: Int,
        inset: Int,
        targetFraction: Float,
        minScale: Float,
        maxScale: Float,
    ): Fit {
        val viewport = size - 2 * inset
        val longest = max(box.width, box.height).coerceAtLeast(1)
        val scale = (targetFraction * viewport / longest).coerceIn(minScale, maxScale)
        return Fit(scale, dx = size / 2f - box.centerX * scale, dy = size / 2f - box.centerY * scale)
    }

    private fun inRing(x: Int, y: Int, start: Int, end: Int, ring: Int): Boolean =
        x < start + ring || x >= end - ring || y < start + ring || y >= end - ring
}
