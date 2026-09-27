package dev.abhay.hypericon.mtz

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class MtzWriterTest {
    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())).documentElement

    private fun entries(zip: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                put(e.name, z.readBytes())
            }
        }
    }

    @Test
    fun `description is well formed and survives CDATA terminators in text`() {
        val xml = ThemeXml.description("HyperIcon · Blue ]]> Dark", "Line <1> & more", "HyperIcon")
        val root = parse(xml)
        assertThat(root.tagName).isEqualTo("theme")
        assertThat(root.getElementsByTagName("title").item(0).textContent).isEqualTo("HyperIcon · Blue ]]> Dark")
        assertThat(root.getElementsByTagName("description").item(0).textContent).isEqualTo("Line <1> & more")
        assertThat(root.getElementsByTagName("uiVersion").item(0).textContent).isEqualTo("17")
        val localized = root.getElementsByTagName("titles").item(0) as Element
        assertThat((localized.getElementsByTagName("title").item(0) as Element).getAttribute("locale")).isEqualTo("en_US")
    }

    @Test
    fun `transform config declares layered icons and the mask path`() {
        val root = parse(ThemeXml.transformConfig("M0 0H100V100H0Z"))
        val configs = root.getElementsByTagName("Config")
        val values = (0 until configs.length).associate {
            val e = configs.item(it) as Element
            e.getAttribute("name") to e.getAttribute("value")
        }
        assertThat(values).containsExactly(
            "SupportLayerIcon", "true",
            "UseDynamicIcon", "true",
            "ConfigIconMask", "M0 0H100V100H0Z",
        )
    }

    @Test
    fun `mtz contains description, nested icons bundle and preview`() {
        val bundle = ByteArrayOutputStream().also { out ->
            IconsBundleWriter(out).use { w ->
                w.writeTransformConfig("<IconTransform/>")
                assertThat(w.writeLayered("com.example.one", byteArrayOf(1), byteArrayOf(2))).isTrue()
                assertThat(w.writeLayered("com.example.two", byteArrayOf(1), byteArrayOf(3))).isTrue()
                assertThat(w.writeLayered("com.example.one", byteArrayOf(9), byteArrayOf(9))).isFalse()
            }
        }.toByteArray()
        val mtz = ByteArrayOutputStream().also { out ->
            MtzWriter(out).use { m ->
                m.writeDescription("<theme/>")
                m.writeIconsBundle(ByteArrayInputStream(bundle))
                m.writePreview("preview_icons_0.png", byteArrayOf(7))
            }
        }.toByteArray()

        val outer = entries(mtz)
        assertThat(outer.keys).containsExactly("description.xml", "icons", "preview/preview_icons_0.png")
        val inner = entries(outer.getValue("icons"))
        assertThat(inner.keys).containsExactly(
            "transform_config.xml",
            "res/drawable-xxhdpi/com.example.one/0.png",
            "res/drawable-xxhdpi/com.example.one/1.png",
            "res/drawable-xxhdpi/com.example.two/0.png",
            "res/drawable-xxhdpi/com.example.two/1.png",
        )
        assertThat(inner.getValue("res/drawable-xxhdpi/com.example.two/1.png").toList()).containsExactly(3.toByte())
        assertThat(inner.keys.none { it.startsWith("/") || it.startsWith(".") }).isTrue()
    }

    @Test
    fun `launcher entries are named like MIUI themes`() {
        assertThat(MtzNaming.activityFolder("com.android.contacts", "com.android.contacts.activities.TwelveKeyDialer"))
            .isEqualTo("com.android.contacts.activities.TwelveKeyDialer")
        assertThat(MtzNaming.activityFolder("com.example.app", "org.other.Launcher"))
            .isEqualTo("com.example.app#org.other.Launcher")
        assertThat(MtzNaming.folders("com.flipkart.android", "com.flipkart.android.BBDIconAlias", isMainActivity = true))
            .containsExactly("com.flipkart.android.BBDIconAlias", "com.flipkart.android").inOrder()
        assertThat(MtzNaming.folders("com.android.contacts", "com.android.contacts.activities.TwelveKeyDialer", isMainActivity = false))
            .containsExactly("com.android.contacts.activities.TwelveKeyDialer")
        assertThat(IconsBundleWriter.isSafeName("com.example.app#org.other.Launcher")).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `path-like package names are rejected`() {
        IconsBundleWriter(ByteArrayOutputStream()).use { it.writeLayered("../evil", byteArrayOf(), byteArrayOf()) }
    }
}
