package dev.abhay.monopack.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.newapps.WatchablePack
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
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var changedFolder = false
    private var resetHint = false
    private var back = false
    private var alerts: Boolean? = null

    private fun show(hintDismissed: Boolean) = compose.setContent {
        MonopackTheme {
            SettingsScreen(
                applyHintDismissed = hintDismissed,
                onChangeFolder = { changedFolder = true },
                onResetApplyHint = { resetHint = true },
                onBack = { back = true },
                newAppAlerts = true,
                onNewAppAlerts = { alerts = it },
            )
        }
    }

    @Test
    fun actionsReachTheirCallbacks() {
        show(hintDismissed = true)
        snapshot("settings")
        compose.onNodeWithText("Notify me").performClick()
        assertThat(alerts).isFalse()
        compose.onNodeWithText("Change folder").performClick()
        assertThat(changedFolder).isTrue()
        compose.onNodeWithText("Show the pick-the-file help again").performScrollTo().performClick()
        assertThat(resetHint).isTrue()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isTrue()
    }

    @Test
    fun helpResetIsDisabledWhileTheHelpStillShows() {
        show(hintDismissed = false)
        compose.onNodeWithText("Show the pick-the-file help again").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show the pick-the-file help again").performClick()
        assertThat(resetHint).isFalse()
    }

    @Test
    fun theWatchedPackIsPickedFromTheInstalledPacks() {
        var watched: String? = null
        val packs = listOf(WatchablePack("p.one", "Monopack"), WatchablePack("p.two", "Monopack · Dark"))
        compose.setContent {
            MonopackTheme {
                SettingsScreen(
                    applyHintDismissed = false,
                    onChangeFolder = {},
                    onResetApplyHint = {},
                    onBack = {},
                    newAppAlerts = true,
                    packs = packs,
                    watchedPack = null,
                    onWatchPack = { watched = it },
                )
            }
        }
        compose.onNodeWithText("Choose the pack your launcher uses").assertIsDisplayed()
        compose.onNodeWithText("Icon pack to watch").performClick()
        compose.onNodeWithText("Monopack · Dark").performClick()
        assertThat(watched).isEqualTo("p.two")
    }

    @Test
    fun theWatchedPackIsGreyedOutWhileNotificationsAreOff() {
        compose.setContent {
            MonopackTheme {
                SettingsScreen(
                    applyHintDismissed = false,
                    onChangeFolder = {},
                    onResetApplyHint = {},
                    onBack = {},
                    newAppAlerts = false,
                    packs = listOf(WatchablePack("p.one", "Monopack")),
                    watchedPack = WatchablePack("p.one", "Monopack"),
                )
            }
        }
        compose.onNodeWithText("Icon pack to watch").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun theInstallRowShowsItsStateAndOpensTheSetting() {
        var asked = false
        compose.setContent {
            MonopackTheme {
                SettingsScreen(
                    applyHintDismissed = false,
                    onChangeFolder = {},
                    onResetApplyHint = {},
                    onBack = {},
                    canInstall = false,
                    onAllowInstalls = { asked = true },
                )
            }
        }
        compose.onNodeWithText("Not allowed. Tap to allow Monopack to install packs.").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun creditsListEveryProject() {
        show(hintDismissed = true)
        compose.onNodeWithText("Open-source credits").performScrollTo().performClick()
        compose.onNodeWithText("ARSCLib · Apache-2.0").assertIsDisplayed()
        snapshot("settings-credits")
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
