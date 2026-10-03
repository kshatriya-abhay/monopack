package dev.abhay.monopack.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.createBitmap
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.Glyph
import dev.abhay.monopack.model.GlyphSource
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.LauncherApp
import dev.abhay.monopack.palette.SeedPresets
import dev.abhay.monopack.ui.theme.MonopackTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppGridTest {
    @get:Rule
    val compose = createComposeRule()

    private val colors = SeedPresets.DEFAULT.palettes().getValue(Accent.PRIMARY).getValue(IconStyle.LIGHT).toIconColors()

    private fun item(pkg: String, label: String, source: GlyphSource): DrawerItem {
        val mask = createBitmap(120, 120, Bitmap.Config.ALPHA_8).also {
            Canvas(it).drawCircle(60f, 60f, 24f, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        val app = LauncherApp(ComponentName(pkg, "$pkg.Main"), label, 0, ApplicationInfo(), true, 0)
        return DrawerItem(app, glyph = Glyph(mask, source))
    }

    private val items = listOf(
        item("com.a", "Alpha", GlyphSource.NATIVE_MONO),
        item("com.b", "Beta", GlyphSource.FORCED_MONO),
        item("com.c", "Gamma", GlyphSource.FORCED_MONO),
    )

    private fun show(edited: Set<String>, previewed: Boolean = true) = compose.setContent {
        MonopackTheme {
            AppGrid(
                items = items,
                colorsFor = { if (previewed) colors else null },
                edited = edited,
                header = GridHeader(
                    previewed = previewed,
                    filter = GridFilter.ALL,
                    counts = mapOf(GridFilter.ALL to 3, GridFilter.NATIVE to 1, GridFilter.GENERATED to 2, GridFilter.EDITED to edited.size),
                    showCountsReady = true,
                    showDefaultPaletteBanner = false,
                    onFilterChange = {},
                    onUseCustomColours = {},
                ),
                contentPadding = PaddingValues(),
                selected = emptySet(),
                onItemClick = {},
                onItemLongClick = {},
            )
        }
    }

    @Test
    fun editedAppsGetTheEditedChip() {
        show(edited = setOf(items[1].app.key))
        compose.onNodeWithText("Edited 1").assertExists()
        snapshot("grid-edited")
    }

    @Test
    fun noEditsMeansNoChip() {
        show(edited = emptySet())
        compose.onNodeWithText("Edited 0").assertDoesNotExist()
    }

    @Test
    fun theFilterChipsStayInViewWhenTheyAppear() {
        val many = (1..60).map { item("com.app$it", "App $it", GlyphSource.FORCED_MONO) }
        // Opening Create rescans apps: the chips go while it runs and come back after.
        var ready by mutableStateOf(true)
        compose.setContent {
            MonopackTheme {
                AppGrid(
                    items = many,
                    colorsFor = { colors },
                    header = GridHeader(
                        previewed = true,
                        filter = GridFilter.ALL,
                        counts = mapOf(GridFilter.ALL to 60, GridFilter.NATIVE to 0, GridFilter.GENERATED to 60, GridFilter.EDITED to 0),
                        showCountsReady = ready,
                        showDefaultPaletteBanner = false,
                        onFilterChange = {},
                        onUseCustomColours = {},
                    ),
                    contentPadding = PaddingValues(),
                    selected = emptySet(),
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
        compose.waitForIdle()
        ready = false
        compose.waitForIdle()
        ready = true
        compose.waitForIdle()
        compose.onNodeWithText("All 60").assertIsDisplayed()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
