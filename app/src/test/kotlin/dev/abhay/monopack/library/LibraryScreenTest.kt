package dev.abhay.monopack.library

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.model.IconStyle
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
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun file(name: String) = FolderFile(name, "content://t/$name", "primary:Download/Monopack/$name", 1, 1)

    private val theme = LibraryItem("t.mtz", ExportKind.THEME, "Monopack · Primary · Dark", IconStyle.DARK, 3000, 208, 0xFF1B2B48.toInt(), 0xFFB0C6FF.toInt(), null, file("t.mtz"), true)
    private val pack = LibraryItem("p.apk", ExportKind.ICON_PACK, "Monopack · Primary · Light", IconStyle.LIGHT, 2000, 208, 0xFFD9E2FF.toInt(), 0xFF2B4678.toInt(), "p", file("p.apk"), true)
    private val missing = LibraryItem("m.mtz", ExportKind.THEME, "Monopack · Blue · Dark", IconStyle.DARK, 1000, 200, null, null, null, null, true)

    private var removed: LibraryItem? = null
    private var deleted = false
    private var toggled: LibraryItem? = null

    private fun show(state: LibraryState) = compose.setContent {
        MonopackTheme {
            LibraryScreen(
                state = state,
                onCreate = {},
                onToggle = { toggled = it },
                onClearSelection = {},
                onDeleteSelected = { deleted = true },
                onRemoveMissing = { removed = it },
                onApplied = {},
                onOpenFolder = { true },
                onChangeFolder = {},
            )
        }
    }

    private val base = LibraryState(loading = false, tree = "t", hasAccess = true, folderLabel = "Download/Monopack")

    @Test
    fun missingFilesAreMarkedAndCanBeRemoved() {
        show(base.copy(items = listOf(theme, pack, missing)))
        compose.onNodeWithText("File missing").assertIsDisplayed()
        compose.onNodeWithText("3 items").assertIsDisplayed()
        snapshot("library")
        compose.onNodeWithContentDescription("Remove Monopack · Blue · Dark").performClick()
        assertThat(removed).isEqualTo(missing)
    }

    @Test
    fun longPressSelectsAndDeleteAsksFirst() {
        show(base.copy(items = listOf(theme, pack), selected = setOf("t.mtz")))
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Its file is deleted too.").assertIsDisplayed()
        assertThat(deleted).isFalse()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        assertThat(deleted).isTrue()
    }

    @Test
    fun longPressOnARowTogglesIt() {
        show(base.copy(items = listOf(theme)))
        compose.onNodeWithText("Monopack · Primary · Dark").performTouchInput { longClick() }
        assertThat(toggled).isEqualTo(theme)
    }

    @Test
    fun emptyLibraryInvitesToCreate() {
        show(base)
        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
        compose.onNodeWithText("Create", useUnmergedTree = true).assertExists()
    }

    private fun snapshot(name: String) {
        val dir = System.getenv("SNAPSHOT_DIR") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
