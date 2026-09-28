package dev.abhay.monopack.palette

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast helpers for ARGB ints (alpha ignored). */
object Contrast {
    fun relativeLuminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(argb shr 16 and 0xFF) +
            0.7152 * channel(argb shr 8 and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
