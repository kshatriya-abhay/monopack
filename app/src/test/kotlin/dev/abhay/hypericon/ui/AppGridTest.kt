package dev.abhay.hypericon.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.createBitmap
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.palette.SeedPresets
import dev.abhay.hypericon.ui.theme.HyperIconTheme
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
        HyperIconTheme {
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
    fun editedAppsGetTheMarkerAndTheEditedChip() {
        show(edited = setOf(items[1].app.key))
        compose.onAllNodesWithContentDescription("Edited icon").assertCountEquals(1)
        compose.onNodeWithText("Edited 1").assertExists()
        snapshot("grid-edited")
    }

    @Test
    fun noEditsMeansNoMarkerAndNoChip() {
        show(edited = emptySet())
        compose.onAllNodesWithContentDescription("Edited icon").assertCountEquals(0)
        compose.onNodeWithText("Edited 0").assertDoesNotExist()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
