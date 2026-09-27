package dev.abhay.hypericon.palette

import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.IconStyle
import org.junit.Test

class DefaultPaletteTest {
    private val frameworkDefaults = mapOf(
        Accent.PRIMARY to mapOf(
            IconStyle.LIGHT to IconPalette(0xFFD9E2FF.toInt(), 0xFF2F4578.toInt()),
            IconStyle.DARK to IconPalette(0xFF2A3042.toInt(), 0xFFB0C6FF.toInt()),
        ),
        Accent.TERTIARY to mapOf(IconStyle.LIGHT to IconPalette(0xFFFDD7FA.toInt(), 0xFF593D59.toInt())),
    )

    @Test
    fun `framework default palettes are detected`() {
        assertThat(DefaultPalette.looksDefault(frameworkDefaults)).isTrue()
    }

    @Test
    fun `wallpaper-derived palettes are not flagged`() {
        assertThat(DefaultPalette.looksDefault(SeedPresets.AOSP[0].palettes())).isFalse()
        assertThat(DefaultPalette.looksDefault(SeedColors.custom(250.0).palettes())).isFalse()
        assertThat(DefaultPalette.looksDefault(emptyMap())).isFalse()
    }
}
