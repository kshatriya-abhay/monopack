/*
 * Portions of this file are ported from AOSP Launcher3's iconloaderlib
 * (MonochromeIconFactory, IconNormalizer, BaseIconFactory):
 *   Copyright (C) The Android Open Source Project
 *   Licensed under the Apache License, Version 2.0
 *   (https://www.apache.org/licenses/LICENSE-2.0); modified for Monopack.
 * Monopack as a whole is licensed under the GNU General Public License v3 (see LICENSE).
 */
package dev.abhay.monopack.glyph

import kotlin.math.roundToInt

/**
 * Pure pixel math for glyph extraction, kept free of Android types so it can be unit-tested.
 * Buffers are square `size × size`, row-major.
 */
object MonoLevels {

    /** Gray level per pixel: the average of R, G and B (as AOSP does). Alpha is ignored. */
    fun luminance(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val c = argb[i]
        (((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3).toByte()
    }

    /**
     * AOSP Launcher3's forced-monochrome levels (`MonochromeIconFactory.generateMono`, Apache 2.0):
     * stretch the gray range of the whole layer to 0..255, and flip it when the top and bottom
     * edge bands ([edgeRows] rows each, the background) would come out more than half opaque.
     * The result is used directly as the glyph's alpha.
     */
    fun aospMono(gray: ByteArray, size: Int, edgeRows: Int): ByteArray {
        var min = 0xFF
        var max = 0
        for (b in gray) {
            val v = b.toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
        }
        if (min >= max) return gray.copyOf()

        val range = (max - min).toFloat()
        val edgeLength = edgeRows * size
        var sum = 0L
        for (i in 0 until edgeLength) {
            sum += gray[i].toInt() and 0xFF
            sum += gray[gray.size - 1 - i].toInt() and 0xFF
        }
        val edgeAverage = sum / (edgeLength * 2f)
        val flip = (edgeAverage - min) / range > 0.5f

        return ByteArray(gray.size) { i ->
            val p = (((gray[i].toInt() and 0xFF) - min) * 255f / range).roundToInt()
            (if (flip) 255 - p else p).toByte()
        }
    }
}
