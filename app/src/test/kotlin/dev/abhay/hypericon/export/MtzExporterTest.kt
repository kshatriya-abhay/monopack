package dev.abhay.hypericon.export

import android.content.ComponentName
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.apps.IconSourceLoader
import dev.abhay.hypericon.model.IconPalette
import dev.abhay.hypericon.model.LauncherApp
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MtzExporterTest {
    @Test
    fun `exports layered icons, plates and a preview for real app icons`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        // Our own app: its launcher icon is adaptive with a monochrome layer.
        val self = LauncherApp(
            component = ComponentName(context.packageName, "${context.packageName}.MainActivity"),
            label = "HyperIcon",
            iconRes = context.applicationInfo.icon,
            appInfo = context.applicationInfo,
            isMainActivity = true,
            lastUpdateTime = 0,
        )
        val light = IconPalette(0xFFD3E3FD.toInt(), 0xFF0842A0.toInt())
        val progress = mutableListOf<Pair<Int, Int>>()

        val file = MtzExporter(context, IconSourceLoader(context)).export(
            ExportRequest("HyperIcon · Primary · Light", "test", "test.mtz", listOf(ExportApp(self, light, listOf(context.packageName, "${context.packageName}.MainActivity"))), darkPreview = false),
        ) { done, total -> progress += done to total }

        assertThat(progress.last()).isEqualTo(1 to 1)
        ZipFile(file).use { mtz ->
            assertThat(mtz.entries().toList().map { it.name })
                .containsExactly("description.xml", "icons", "preview/preview_icons_0.png")
            val icons = mtz.getInputStream(mtz.getEntry("icons")).readBytes()
            val inner = buildMap {
                ZipInputStream(ByteArrayInputStream(icons)).use { z ->
                    while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes()) }
                }
            }
            val dir = "res/drawable-xxhdpi/${context.packageName}"
            val activityDir = "res/drawable-xxhdpi/${context.packageName}.MainActivity"
            assertThat(inner.keys).containsExactly(
                "transform_config.xml", "$dir/0.png", "$dir/1.png", "$activityDir/0.png", "$activityDir/1.png",
            )

            val plate = BitmapFactory.decodeByteArray(inner.getValue("$dir/0.png"), 0, inner.getValue("$dir/0.png").size)
            assertThat(plate.width).isEqualTo(432)
            assertThat(plate.getPixel(216, 216)).isEqualTo(light.background)
            val glyph = BitmapFactory.decodeByteArray(inner.getValue("$dir/1.png"), 0, inner.getValue("$dir/1.png").size)
            assertThat(glyph.width).isEqualTo(432)
            // Transparent corner (outside the glyph), glyph pixels in the foreground colour.
            assertThat(glyph.getPixel(2, 2) ushr 24).isEqualTo(0)
            val opaque = (0 until 432 * 432).map { glyph.getPixel(it % 432, it / 432) }.filter { (it ushr 24) == 255 }
            assertThat(opaque).isNotEmpty()
            assertThat(opaque.all { it == light.foreground }).isTrue()
        }
    }
}
