package dev.abhay.monopack.hyperos

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.ui.theme.MonopackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApplyThemeFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private var dontShowAgain = false

    private fun show(dismissed: Boolean) = compose.setContent {
        MonopackTheme {
            val apply = rememberApplyThemeFlow("Download/Monopack", dismissed) { dontShowAgain = true }
            Button(onClick = { apply(ThemeToApply("/storage/emulated/0/Download/Monopack/t.mtz", "Monopack-Primary-Dark-20260927-1015.mtz")) }) {
                Text("Apply")
            }
        }
    }

    @Test
    fun firstApplyExplainsWhichFileToPick() {
        show(dismissed = false)
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Download/Monopack/Monopack-Primary-Dark-20260927-1015.mtz").assertIsDisplayed()
        compose.onNodeWithText("Don't show again").performClick()
        compose.onNodeWithText("Proceed").performClick()
        assertThat(dontShowAgain).isTrue()
        // Theme Manager isn't installed under Robolectric, so the apply itself reports that.
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Theme Manager isn't available")
    }

    @Test
    fun afterDontShowAgainTheDialogIsSkipped() {
        show(dismissed = true)
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Pick the theme file").assertDoesNotExist()
        assertThat(dontShowAgain).isFalse()
    }
}
