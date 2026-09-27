package dev.abhay.hypericon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.abhay.hypericon.model.Accent
import dev.abhay.hypericon.model.ColorSource
import dev.abhay.hypericon.model.IconStyle
import dev.abhay.hypericon.model.Selection
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
            HyperIconTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ControlPanel(state, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Icon style").assertIsDisplayed()
        snapshot("expanded")

        compose.onNodeWithText("Not previewed yet").performClick()
        compose.onNodeWithText("Icon style").assertDoesNotExist()
        compose.onNodeWithText("Preview").assertDoesNotExist()
        snapshot("collapsed")

        compose.onNodeWithText("Not previewed yet").performClick()
        compose.onNodeWithText("Icon style").assertIsDisplayed()
    }

    @Test
    fun draggingTheHeaderCollapsesAndExpands() {
        compose.setContent {
            HyperIconTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ControlPanel(state, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Not previewed yet").performTouchInput { swipe(center, center + Offset(0f, 1200f), durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Icon style").assertDoesNotExist()
        snapshot("dragged-collapsed")

        compose.onNodeWithText("Not previewed yet").performTouchInput { swipe(center, center - Offset(0f, 1200f), durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Icon style").assertIsDisplayed()
        snapshot("dragged-expanded")
    }

    @Test
    fun collapseRequestCollapsesThePanel() {
        var requests by mutableIntStateOf(0)
        compose.setContent {
            HyperIconTheme { ControlPanel(state, {}, {}, {}, {}, {}, collapseRequests = requests) }
        }
        compose.onNodeWithText("Icon style").assertIsDisplayed()
        requests++
        compose.waitForIdle()
        compose.onNodeWithText("Icon style").assertDoesNotExist()
        compose.onNodeWithText("Preview").assertDoesNotExist()
    }

    @Test
    fun expandRequestExpandsACollapsedPanel() {
        var expand by mutableIntStateOf(0)
        compose.setContent {
            HyperIconTheme { ControlPanel(state, {}, {}, {}, {}, {}, collapseRequests = 1, expandRequests = expand) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Icon style").assertDoesNotExist()
        expand++
        compose.waitForIdle()
        compose.onNodeWithText("Icon style").assertIsDisplayed()
    }

    @Test
    fun previewIsDisabledWhenTheSelectionIsAlreadyShown() {
        val shown = state.copy(committed = state.pending, committedPalette = state.pendingPalette)
        compose.setContent {
            HyperIconTheme { ControlPanel(shown, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Preview").assertIsNotEnabled()
    }

    @Test
    fun previewShowsProgressWhileIconsLoad() {
        compose.setContent {
            HyperIconTheme { ControlPanel(state.copy(scanning = true), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Preparing icons… 0%").assertIsNotEnabled()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
