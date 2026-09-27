package dev.abhay.hypericon.iconpack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFilterXmlTest {
    @Test
    fun `one item per component, escaped`() {
        val xml = AppFilterXml.appFilter(
            listOf(
                AppFilterItem("com.a", "com.a.Main", "com_a"),
                AppFilterItem("com.b", "com.b.X\$Inner&Co", "com_b"),
            ),
        )
        assertThat(xml).contains("<item component=\"ComponentInfo{com.a/com.a.Main}\" drawable=\"com_a\" />")
        assertThat(xml).contains("ComponentInfo{com.b/com.b.X\$Inner&amp;Co}")
        assertThat(Regex("<item ").findAll(xml).count()).isEqualTo(2)
    }

    @Test
    fun `drawable list has each icon once`() {
        val xml = AppFilterXml.drawables(listOf("com_a", "com_b", "com_a"))
        assertThat(Regex("<item drawable=").findAll(xml).count()).isEqualTo(2)
    }
}
