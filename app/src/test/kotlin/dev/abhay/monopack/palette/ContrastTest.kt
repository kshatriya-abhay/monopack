package dev.abhay.monopack.palette

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContrastTest {
    @Test
    fun `black on white is 21 to 1`() {
        assertThat(Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())).isWithin(0.01).of(21.0)
    }

    @Test
    fun `ratio is symmetric and identical colors give 1`() {
        val a = 0xFF0842A0.toInt()
        val b = 0xFFD3E3FD.toInt()
        assertThat(Contrast.ratio(a, b)).isWithin(1e-9).of(Contrast.ratio(b, a))
        assertThat(Contrast.ratio(a, a)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `AOSP fallback themed icon colors meet 4_5 to 1`() {
        // iconloaderlib values/ and values-night/ fallbacks (pre-Monet devices).
        assertThat(Contrast.ratio(0xFF0842A0.toInt(), 0xFFD3E3FD.toInt())).isAtLeast(4.5)
        assertThat(Contrast.ratio(0xFFA8C7FA.toInt(), 0xFF003355.toInt())).isAtLeast(4.5)
    }
}
