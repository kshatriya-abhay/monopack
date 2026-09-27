package dev.abhay.hypericon.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.graphics.createBitmap
import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.Glyph
import dev.abhay.hypericon.model.GlyphSource
import dev.abhay.hypericon.model.IconEdit
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.LauncherApp
import dev.abhay.hypericon.palette.SeedPresets
import dev.abhay.hypericon.ui.theme.HyperIconTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IconEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val pairs = SeedPresets.DEFAULT.palettes().getValue(Accent.PRIMARY)

    private fun item(pkg: String, label: String, source: GlyphSource, circle: Float): DrawerItem {
        val mask = createBitmap(240, 240, Bitmap.Config.ALPHA_8).also {
            Canvas(it).drawCircle(120f, 120f, circle, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        val app = LauncherApp(ComponentName(pkg, "$pkg.Main"), label, 0, ApplicationInfo(), true, 0)
        return DrawerItem(app, glyph = Glyph(mask, source))
    }

    /** A low-contrast generated glyph: a half-opaque disc on a faint background, like two similar colours. */
    private val target = run {
        val mask = createBitmap(240, 240, Bitmap.Config.ALPHA_8).also {
            val canvas = Canvas(it)
            canvas.drawColor(android.graphics.Color.argb(70, 0, 0, 0))
            canvas.drawCircle(120f, 120f, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 150 })
        }
        val app = LauncherApp(ComponentName("com.example.bb", "com.example.bb.Main"), "bigbasket", 0, ApplicationInfo(), true, 0)
        DrawerItem(app, glyph = Glyph(mask, GlyphSource.FORCED_MONO))
    }
    private val references = listOf(
        item("com.example.gmail", "Gmail", GlyphSource.NATIVE_MONO, 45f),
        item("com.example.maps", "Maps", GlyphSource.NATIVE_MONO, 40f),
        item("com.example.photos", "Photos", GlyphSource.NATIVE_MONO, 50f),
        item("com.example.drive", "Drive", GlyphSource.NATIVE_MONO, 35f),
    )

    private var saved: IconEdit? = null
    private var reset = false
    private var closed = false

    private fun show(edit: IconEdit? = null) = compose.setContent {
        HyperIconTheme {
            IconEditorScreen(
                item = target,
                pairs = pairs,
                globalStyle = IconStyle.LIGHT,
                edit = edit,
                references = references,
                loadGlyph = { null },
                onSave = { saved = it },
                onReset = { reset = true },
                onClose = { closed = true },
            )
        }
    }

    @Test
    fun savesTheChosenBaseAndTone() {
        show()
        compose.onNodeWithText("Compare with native icons").assertIsDisplayed()
        compose.onNodeWithText("Reset to default").assertDoesNotExist()
        snapshot("editor-default")

        compose.onNodeWithText("Dark icon").performClick()
        compose.onNodeWithContentDescription("Dark colour brightness")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
        compose.onNodeWithText("−6").assertIsDisplayed()
        compose.onNodeWithText("Invert glyph").performClick()
        compose.onNodeWithContentDescription("Glyph contrast")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        compose.onNodeWithText("100%").assertIsDisplayed()
        snapshot("editor-edited")

        compose.onNodeWithText("Save").performClick()
        assertThat(saved).isEqualTo(IconEdit(IconStyle.DARK, -6, inverted = true, contrast = 100))
    }

    @Test
    fun cancellingWithChangesAsksFirst() {
        show()
        compose.onNodeWithText("Dark icon").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertThat(closed).isFalse()
        compose.onNodeWithText("Discard").performClick()
        assertThat(closed).isTrue()
    }

    @Test
    fun cancellingWithoutChangesClosesDirectly() {
        show()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(closed).isTrue()
    }

    @Test
    fun anExistingEditCanBeReset() {
        show(IconEdit(IconStyle.DARK, 4))
        compose.onNodeWithText("+4").assertIsDisplayed()
        compose.onNodeWithText("Reset to default").performClick()
        assertThat(reset).isTrue()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
