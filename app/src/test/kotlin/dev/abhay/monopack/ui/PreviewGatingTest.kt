package dev.abhay.monopack.ui

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.model.IconPalette
import org.junit.Test

class PreviewGatingTest {
    private val primaryLight = IconPalette(0xFFD3E3FD.toInt(), 0xFF0842A0.toInt())
    private val primaryDark = IconPalette(0xFF003355.toInt(), 0xFFA8C7FA.toInt())

    @Test
    fun `disabled while icons are still loading`() {
        assertThat(isPreviewEnabled(iconsReady = false, pending = primaryLight, committed = null)).isFalse()
    }

    @Test
    fun `enabled once ready when nothing is committed`() {
        assertThat(isPreviewEnabled(iconsReady = true, pending = primaryLight, committed = null)).isTrue()
    }

    @Test
    fun `disabled when the pending colors are already shown`() {
        assertThat(isPreviewEnabled(iconsReady = true, pending = primaryLight, committed = primaryLight)).isFalse()
    }

    @Test
    fun `enabled when style or accent resolves to different colors`() {
        assertThat(isPreviewEnabled(iconsReady = true, pending = primaryDark, committed = primaryLight)).isTrue()
    }

    @Test
    fun `enabled when the wallpaper changed the colors of the same selection`() {
        val afterWallpaperChange = primaryLight.copy(background = 0xFFFFE0D0.toInt())
        assertThat(isPreviewEnabled(iconsReady = true, pending = afterWallpaperChange, committed = primaryLight)).isTrue()
    }

    @Test
    fun `disabled when no palette is available`() {
        assertThat(isPreviewEnabled(iconsReady = true, pending = null, committed = null)).isFalse()
    }
}
