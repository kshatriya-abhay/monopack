package dev.abhay.hypericon.library

import com.google.common.truth.Truth.assertThat
import dev.abhay.hypericon.export.ExportKind
import dev.abhay.hypericon.model.IconStyle
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class LibraryTest {
    private fun file(name: String, modified: Long = 1L) = FolderFile(name, "content://t/$name", "primary:Download/HyperIcon/$name", 10, modified)

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `untracked theme and pack names are read back`() {
        val theme = Library.untracked(file("HyperIcon-Blue-Primary-Dark-20260927-1015.mtz"))!!
        assertThat(theme.kind).isEqualTo(ExportKind.THEME)
        assertThat(theme.title).isEqualTo("HyperIcon · Blue · Primary · Dark")
        assertThat(theme.style).isEqualTo(IconStyle.DARK)
        assertThat(theme.createdAt).isEqualTo(millis(2026, 9, 27, 10, 15))
        assertThat(theme.tracked).isFalse()

        val pack = Library.untracked(file("Mypack-Light-pack-20260927-0930.apk"))!!
        assertThat(pack.kind).isEqualTo(ExportKind.ICON_PACK)
        assertThat(pack.title).isEqualTo("Mypack · Light")
        assertThat(pack.style).isEqualTo(IconStyle.LIGHT)
    }

    @Test
    fun `other files are ignored and odd names fall back to the modification time`() {
        assertThat(Library.untracked(file("notes.txt"))).isNull()
        val odd = Library.untracked(file("something.mtz", modified = 42L))!!
        assertThat(odd.title).isEqualTo("something")
        assertThat(odd.createdAt).isEqualTo(42L)
        assertThat(odd.style).isNull()
    }

    @Test
    fun `records merge with the folder, missing and untracked, newest first`() {
        val kept = ExportRecord("a.mtz", ExportKind.THEME, "A · Light", IconStyle.LIGHT, millis(2026, 9, 27, 12, 0), 200)
        val gone = ExportRecord("b.apk", ExportKind.ICON_PACK, "B · Dark", IconStyle.DARK, millis(2026, 9, 27, 11, 0), 200, packageName = "p.b")
        val items = Library.merge(
            listOf(kept, gone),
            listOf(file("a.mtz"), file("HyperIcon-C-Dark-20000101-0000.mtz", modified = 5), file("readme.md")),
        )
        assertThat(items.map { it.fileName }).containsExactly("a.mtz", "b.apk", "HyperIcon-C-Dark-20000101-0000.mtz").inOrder()
        assertThat(items[0].missing).isFalse()
        assertThat(items[1].missing).isTrue()
        assertThat(items[1].packageName).isEqualTo("p.b")
        assertThat(items[2].tracked).isFalse()
    }

    @Test
    fun `internal storage documents map to paths`() {
        assertThat(Library.pathFor("primary:Download/HyperIcon/a.mtz")).isEqualTo("/storage/emulated/0/Download/HyperIcon/a.mtz")
        assertThat(Library.pathFor("1234-ABCD:Themes/a.mtz")).isNull()
        assertThat(Library.labelFor("primary:Download/HyperIcon")).isEqualTo("Download/HyperIcon")
    }
}
