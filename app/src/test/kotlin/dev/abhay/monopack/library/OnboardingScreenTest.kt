package dev.abhay.monopack.library

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val folder get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Monopack")

    private fun show() = compose.setContent { MonopackTheme { OnboardingScreen(lostAccess = false, error = null, onFolderPicked = {}) } }

    @Test
    fun withoutAFolderItGuidesMakingOne() {
        folder.deleteRecursively()
        show()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Make a Monopack folder").assertIsDisplayed()
        compose.onNodeWithText("name it Monopack", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open folder picker").assertIsDisplayed()
        snapshot("onboarding-make-folder")
    }

    @Test
    fun anExistingFolderIsJustPicked() {
        folder.mkdirs()
        show()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Choose the Monopack folder").assertIsDisplayed()
        compose.onNodeWithText("It opens in Download/Monopack.", substring = true).assertIsDisplayed()
        folder.deleteRecursively()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
