package dev.abhay.monopack.glyph

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

    @Test
    fun `close levels separate evenly along the slider`() {
        // Two similarly bright colours (Eatsure's purple and green) come out at 238 and 253.
        val m = mask(238, 253)
        val half = MaskContrast.apply(m, side, 0.5f)
        assertThat(half.at(0, 0)).isIn(110..130) // halfway to transparent
        assertThat(half.at(18, 18)).isAtLeast(250)
        val full = MaskContrast.apply(m, side, 1f)
        assertThat(full.at(0, 0)).isEqualTo(0)
        assertThat(full.at(18, 18)).isEqualTo(255)
    }

    @Test
    fun `a thin rim at the viewport edge doesn't hide two close levels`() {
        // 100 px layer: visible 16..83. A 1 px near-transparent rim at the viewport edge, then two
        // close levels (238 around, 253 in the middle), like Eatsure at some sizes.
        val n = 100
        val m = ByteArray(n * n) { i ->
            val x = i % n
            val y = i / n
            when {
                x == 16 || y == 16 || x == 83 || y == 83 -> 20
                x in 40 until 60 && y in 40 until 60 -> 253
                else -> 238
            }.toByte()
        }
        val out = MaskContrast.apply(m, n, 1f)
        assertThat(out[30 * n + 30].toInt() and 0xFF).isEqualTo(0)
        assertThat(out[50 * n + 50].toInt() and 0xFF).isEqualTo(255)
    }
}
