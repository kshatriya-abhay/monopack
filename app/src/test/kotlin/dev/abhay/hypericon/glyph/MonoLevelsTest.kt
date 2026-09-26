package dev.abhay.hypericon.glyph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MonoLevelsTest {
    private val side = 12
    private val inset = 2 // viewport = [2, 10)
    private val ring = 1

    /** A viewport filled with [bg] and a 2×2 block of [fg] in the middle. */
    private fun image(bg: Int, fg: Int): ByteArray = ByteArray(side * side) { i ->
        val x = i % side
        val y = i / side
        (if (x in 5..6 && y in 5..6) fg else bg).toByte()
    }

    private fun ByteArray.at(x: Int, y: Int) = this[y * side + x].toInt() and 0xFF

    @Test
    fun `luminance averages rgb and ignores alpha`() {
        val gray = MonoLevels.luminance(intArrayOf(0x00FF0000, 0xFF00FF00.toInt(), 0xFF3060C0.toInt()))
        assertThat(gray.map { it.toInt() and 0xFF }).containsExactly(85, 85, (0x30 + 0x60 + 0xC0) / 3).inOrder()
    }

    @Test
    fun `dark logo on light background is flipped so the background is transparent`() {
        val mask = MonoLevels.toGlyphMask(image(bg = 230, fg = 20), side, inset, ring)
        assertThat(mask.at(3, 3)).isEqualTo(0)
        assertThat(mask.at(5, 5)).isEqualTo(255)
    }

    @Test
    fun `light logo on dark background is kept as is`() {
        val mask = MonoLevels.toGlyphMask(image(bg = 10, fg = 200), side, inset, ring)
        assertThat(mask.at(3, 3)).isEqualTo(0)
        assertThat(mask.at(6, 6)).isEqualTo(255)
    }

    @Test
    fun `mid tones are stretched linearly`() {
        val img = image(bg = 100, fg = 200)
        img[4 * side + 4] = 150.toByte()
        val mask = MonoLevels.toGlyphMask(img, side, inset, ring)
        assertThat(mask.at(4, 4)).isEqualTo(128)
    }

    @Test
    fun `pixels outside the viewport are cleared`() {
        val img = image(bg = 10, fg = 200)
        img[0] = 255.toByte()
        val mask = MonoLevels.toGlyphMask(img, side, inset, ring)
        assertThat(mask.at(0, 0)).isEqualTo(0)
        assertThat(mask.at(1, 5)).isEqualTo(0)
    }

    @Test
    fun `denoise clears faint levels and keeps full opacity`() {
        val out = MonoLevels.denoise(byteArrayOf(0, 20, 24, 25, 140.toByte(), 255.toByte()), floor = 24)
        assertThat(out.map { it.toInt() and 0xFF }).containsExactly(0, 0, 0, 1, 128, 255).inOrder()
    }

    @Test
    fun `flat viewport yields an empty mask`() {
        val mask = MonoLevels.toGlyphMask(ByteArray(side * side) { 77 }, side, inset, ring)
        assertThat(mask.all { it.toInt() == 0 }).isTrue()
    }

    @Test
    fun `bounding box covers pixels at or above the threshold`() {
        val box = MonoLevels.boundingBox(image(bg = 0, fg = 200), side, threshold = 100)
        assertThat(box).isEqualTo(MonoLevels.Box(5, 5, 7, 7))
        assertThat(MonoLevels.boundingBox(ByteArray(side * side), side, 1)).isNull()
    }

    @Test
    fun `fit centers the glyph and scales its longer side to the target`() {
        val box = MonoLevels.Box(left = 10, top = 20, right = 30, bottom = 30) // 20 × 10, center (20, 25)
        val fit = MonoLevels.fitToTarget(box, size = 120, inset = 20, targetFraction = 0.5f, minScale = 0.1f, maxScale = 10f)
        assertThat(fit.scale).isWithin(1e-4f).of(2f) // 0.5 × 80 / 20
        assertThat(20 * fit.scale + fit.dx).isWithin(1e-4f).of(60f)
        assertThat(25 * fit.scale + fit.dy).isWithin(1e-4f).of(60f)
    }

    @Test
    fun `fit scale is clamped`() {
        val tiny = MonoLevels.Box(0, 0, 1, 1)
        val fit = MonoLevels.fitToTarget(tiny, size = 120, inset = 20, targetFraction = 0.6f, minScale = 0.5f, maxScale = 1.6f)
        assertThat(fit.scale).isEqualTo(1.6f)
    }

    @Test
    fun `coverage counts viewport pixels at or above the threshold`() {
        val cov = MonoLevels.coverage(image(bg = 0, fg = 255), side, inset, threshold = 250)
        assertThat(cov).isWithin(1e-6f).of(4f / 64f)
    }
}
