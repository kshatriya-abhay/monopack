package dev.abhay.hypericon.glyph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorKeyTest {
    private val side = 12
    private val inset = 2

    @Test
    fun `smooth ramps from background to glyph`() {
        assertThat(ColorKey.smooth(ColorKey.LO)).isEqualTo(0f)
        assertThat(ColorKey.smooth(0f)).isEqualTo(0f)
        assertThat(ColorKey.smooth(ColorKey.HI)).isEqualTo(1f)
        assertThat(ColorKey.smooth(200f)).isEqualTo(1f)
        assertThat(ColorKey.smooth((ColorKey.LO + ColorKey.HI) / 2)).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `dominant colours ignore rare ones`() {
        val pixels = IntArray(100) { if (it < 97) 0x3DDC84 else 0xFF0000 }
        val colors = ColorKey.dominantColors(pixels, minShare = 0.05f)
        assertThat(colors.toList()).containsExactly(0x3DDC84)
    }

    @Test
    fun `edge palette keeps colours that dominate the ring, not a logo touching it`() {
        // 90 background pixels in a gentle gradient and 10 logo pixels.
        val ring = IntArray(100) { if (it < 90) 0x0050FF + ((it / 3) shl 8) else 0xFFFFFF }
        val palette = ColorKey.edgePalette(ring, ring.size)
        assertThat(palette).isNotEmpty()
        assertThat(palette.none { ColorKey.dist(it, 0xFFFFFF) < ColorKey.HI }).isTrue()
    }

    @Test
    fun `key alpha keeps any colour far from the palette at full opacity`() {
        // Red logo on green (similar luminance) stays fully opaque; green is removed.
        val argb = IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            if (x in 5..6 && y in 5..6) 0xFFED1C24.toInt() else 0xFF8DC63F.toInt()
        }
        val alpha = ColorKey.keyAlpha(argb, side, inset, intArrayOf(0x8DC63F))
        assertThat(alpha[5 * side + 5]).isEqualTo(1f)
        assertThat(alpha[3 * side + 3]).isEqualTo(0f)
        assertThat(alpha[0]).isEqualTo(0f) // outside the viewport
    }

    @Test
    fun `key alpha respects source transparency`() {
        val argb = IntArray(side * side) { 0x80FFFFFF.toInt() }
        val alpha = ColorKey.keyAlpha(argb, side, inset, intArrayOf(0x000000))
        assertThat(alpha[5 * side + 5]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `open removes one pixel lines but keeps solid shapes`() {
        val alpha = FloatArray(side * side) { i ->
            val x = i % side
            val y = i / side
            if (y == 2 || (x in 5..8 && y in 5..8)) 1f else 0f
        }
        val opened = ColorKey.open(alpha, side, 1)
        assertThat(opened[2 * side + 6]).isEqualTo(0f)
        assertThat(opened[6 * side + 6]).isEqualTo(1f)
    }

    @Test
    fun `dominant colour picks the most common glyph colour`() {
        val argb = IntArray(side * side) { if (it < 100) 0xF7941D else 0x111111 }
        val alpha = FloatArray(side * side) { 1f }
        assertThat(ColorKey.dominantColor(argb, alpha)).isEqualTo(0xF7941D)
    }
}
