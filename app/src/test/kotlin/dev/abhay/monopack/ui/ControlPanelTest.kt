package dev.abhay.monopack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.SeedPresets
import dev.abhay.monopack.render.IconShape
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
class ControlPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val seed = SeedPresets.DEFAULT
    private val state = UiState(
        scanning = false,
        palettes = seed.palettes(),
        customPalettes = seed.palettes(),
        pending = Selection(IconStyle.DARK, Accent.PRIMARY, ColorSource.CUSTOM, seed),
    )

    @Test
    fun collapsesAndExpandsFromTheHeader() {
        compose.setContent {
            MonopackTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ControlPanel(state, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Preview mode").assertIsDisplayed()
        snapshot("expanded")

        compose.onNodeWithText("Not previewed yet").performClick()
        compose.onNodeWithText("Preview mode").assertDoesNotExist()
        compose.onNodeWithText("Preview").assertDoesNotExist()
        snapshot("collapsed")

        compose.onNodeWithText("Not previewed yet").performClick()
        compose.onNodeWithText("Preview mode").assertIsDisplayed()
    }

    @Test
    fun theIconShapeIsPickedFromShapeTiles() {
        var shape: IconShape? = null
        compose.setContent {
            MonopackTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ControlPanel(state, {}, {}, {}, {}, {}, iconShape = IconShape.SQUIRCLE, onIconShape = { shape = it })
                }
            }
        }
        compose.onNodeWithText("Icon shape").assertIsDisplayed()
        compose.onNodeWithContentDescription("Circle").performClick()
        assertThat(shape).isEqualTo(IconShape.CIRCLE)
    }

    @Test
    fun draggingTheHeaderCollapsesAndExpands() {
        compose.setContent {
            MonopackTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ControlPanel(state, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Not previewed yet").performTouchInput { swipe(center, center + Offset(0f, 1800f), durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Preview mode").assertDoesNotExist()
        snapshot("dragged-collapsed")

        compose.onNodeWithText("Not previewed yet").performTouchInput { swipe(center, center - Offset(0f, 1800f), durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Preview mode").assertIsDisplayed()
        snapshot("dragged-expanded")
    }

    @Test
    fun collapseRequestCollapsesThePanel() {
        var requests by mutableIntStateOf(0)
        compose.setContent {
            MonopackTheme { ControlPanel(state, {}, {}, {}, {}, {}, collapseRequests = requests) }
        }
        compose.onNodeWithText("Preview mode").assertIsDisplayed()
        requests++
        compose.waitForIdle()
        compose.onNodeWithText("Preview mode").assertDoesNotExist()
        compose.onNodeWithText("Preview").assertDoesNotExist()
    }

    @Test
    fun expandRequestExpandsACollapsedPanel() {
        var expand by mutableIntStateOf(0)
        compose.setContent {
            MonopackTheme { ControlPanel(state, {}, {}, {}, {}, {}, collapseRequests = 1, expandRequests = expand) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Preview mode").assertDoesNotExist()
        expand++
        compose.waitForIdle()
        compose.onNodeWithText("Preview mode").assertIsDisplayed()
    }

    @Test
    fun previewIsDisabledWhenTheSelectionIsAlreadyShown() {
        val shown = state.copy(committed = state.pending, committedPalette = state.pendingPalette)
        compose.setContent {
            MonopackTheme { ControlPanel(shown, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Preview").assertIsNotEnabled()
    }

    @Test
    fun previewShowsProgressWhileIconsLoad() {
        compose.setContent {
            MonopackTheme { ControlPanel(state.copy(scanning = true), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Preparing icons… 0%").assertIsNotEnabled()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
