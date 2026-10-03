package dev.abhay.monopack.export

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.model.IconPalette
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.LauncherApp
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeFoldersTest {
    private fun app(pkg: String, cls: String, main: Boolean) = LauncherApp(ComponentName(pkg, cls), cls, 0, ApplicationInfo(), main, 0)

    @Test
    fun unusualComponentNamesSkipOnlyThatApp() {
        val pair = IconPalette(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val apps = listOf(
            app("com.example.ok", "com.example.ok.Main", main = true),
            // An aapt1-era activity-alias with a dash: its own folder can't be written.
            app("com.example.alias", "com.example.alias.Icon-Dark", main = true),
            app("com.example.extra", "com.example.extra.Second-Entry", main = false),
        )
        val job = ExportJobs.themes(
            "Monopack", setOf(IconStyle.LIGHT), apps, emptyMap(),
            mapOf(IconStyle.LIGHT to pair, IconStyle.DARK to pair), IconStyle.LIGHT, LocalDateTime.of(2026, 1, 1, 0, 0),
        )
        val exported = job.requests.single().second.apps
        assertThat(exported.map { it.app.packageName }).containsExactly("com.example.ok", "com.example.alias").inOrder()
        // The main entry keeps its package-level folder.
        assertThat(exported[1].folders).containsExactly("com.example.alias")
    }
}
