package dev.abhay.hypericon.glyph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MonoLevelsTest {
    private val side = 8
    private val edgeRows = 1

    /** A [side]² image of [bg] with a 2×2 block of [fg] in the middle. */
    private fun image(bg: Int, fg: Int): ByteArray = ByteArray(side * side) { i ->
        val x = i % side
        val y = i / side
        (if (x in 3..4 && y in 3..4) fg else bg).toByte()
    }

    private fun ByteArray.at(x: Int, y: Int) = this[y * side + x].toInt() and 0xFF

    @Test
    fun `luminance averages rgb and ignores alpha`() {
        val gray = MonoLevels.luminance(intArrayOf(0x00FF0000, 0xFF00FF00.toInt(), 0xFF3060C0.toInt()))
        assertThat(gray.map { it.toInt() and 0xFF }).containsExactly(85, 85, (0x30 + 0x60 + 0xC0) / 3).inOrder()
    }

    @Test
    fun `dark logo on a light background is flipped so the background is transparent`() {
        val mono = MonoLevels.aospMono(image(bg = 230, fg = 20), side, edgeRows)
        assertThat(mono.at(0, 0)).isEqualTo(0)
        assertThat(mono.at(3, 3)).isEqualTo(255)
    }

    @Test
    fun `light logo on a dark background is kept`() {
        val mono = MonoLevels.aospMono(image(bg = 10, fg = 200), side, edgeRows)
        assertThat(mono.at(0, 0)).isEqualTo(0)
        assertThat(mono.at(4, 4)).isEqualTo(255)
    }

    @Test
    fun `mid tones are stretched linearly`() {
        val img = image(bg = 100, fg = 200)
        img[2 * side + 2] = 150.toByte()
        assertThat(MonoLevels.aospMono(img, side, edgeRows).at(2, 2)).isEqualTo(128)
    }

    @Test
    fun `flip is decided by the top and bottom edge bands only`() {
        // Bright left/right columns but dark top/bottom rows: no flip.
        val img = ByteArray(side * side) { i ->
            val x = i % side
            val y = i / side
            (if (y == 0 || y == side - 1) 10 else if (x == 0 || x == side - 1) 250 else 120).toByte()
        }
        val mono = MonoLevels.aospMono(img, side, edgeRows)
        assertThat(mono.at(3, 0)).isEqualTo(0)
        assertThat(mono.at(0, 3)).isEqualTo(255)
    }

    @Test
    fun `flat image is returned unchanged`() {
        val flat = ByteArray(side * side) { 77 }
        assertThat(MonoLevels.aospMono(flat, side, edgeRows).toList()).isEqualTo(flat.toList())
    }
}
