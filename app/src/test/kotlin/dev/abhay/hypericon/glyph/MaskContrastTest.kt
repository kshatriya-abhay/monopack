package dev.abhay.hypericon.glyph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskContrastTest {
    private val side = 36

    /** A [side]² mask: [background] alpha everywhere, [glyph] alpha in a centred square. */
    private fun mask(background: Int, glyph: Int) = ByteArray(side * side) { i ->
        val x = i % side
        val y = i / side
        (if (x in 12 until 24 && y in 12 until 24) glyph else background).toByte()
    }

    private fun ByteArray.at(x: Int, y: Int) = this[y * side + x].toInt() and 0xFF

    @Test
    fun `full strength makes both clusters solid`() {
        val out = MaskContrast.apply(mask(70, 150), side, 1f)
        assertThat(out.at(0, 0)).isEqualTo(0)
        assertThat(out.at(18, 18)).isEqualTo(255)
    }

    @Test
    fun `half strength pushes the clusters apart without clipping them`() {
        val out = MaskContrast.apply(mask(70, 150), side, 0.5f)
        assertThat(out.at(18, 18) - out.at(0, 0)).isGreaterThan(80)
        assertThat(out.at(0, 0)).isIn(1..69)
        assertThat(out.at(18, 18)).isIn(151..254)
    }

    @Test
    fun `off, flat and nearly flat masks are unchanged`() {
        val m = mask(70, 150)
        assertThat(MaskContrast.apply(m, side, 0f)).isEqualTo(m)
        val flat = mask(90, 90)
        assertThat(MaskContrast.apply(flat, side, 1f)).isEqualTo(flat)
        val noise = mask(90, 94)
        assertThat(MaskContrast.apply(noise, side, 1f)).isEqualTo(noise)
    }

    @Test
    fun `levels are measured on the visible area only`() {
        // Only the hidden overscan border differs; the visible area is one flat level.
        val m = ByteArray(side * side) { i ->
            val x = i % side
            val y = i / side
            (if (x < 6 || y < 6 || x >= 30 || y >= 30) 0 else 120).toByte()
        }
        assertThat(MaskContrast.levels(m, side, 1f)).isNull()
    }

    @Test
    fun `otsu splits two clusters between them`() {
        val histogram = LongArray(256).apply {
            this[60] = 500
            this[180] = 300
        }
        assertThat(MaskContrast.otsu(histogram)).isIn(60 until 180)
    }
}
