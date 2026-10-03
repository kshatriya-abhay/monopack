package dev.abhay.monopack.export

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.library.FolderFile
import dev.abhay.monopack.library.Library
import org.junit.Test

class PackFileNameTest {
    @Test
    fun namesThatReadBackExactlyKeepPlainFileNames() {
        assertThat(ExportJobs.packFileName("Monopack")).isEqualTo("Monopack.apk")
        assertThat(ExportJobs.packFileName("Monopack · Blue · Dark")).isEqualTo("Monopack-Blue-Dark.apk")
    }

    @Test
    fun differentPacksNeverShareAFile() {
        val names = listOf("Monopack", "Monopack!", "Monopack 🌙", "★", "Blue Night", "BlueNight", "Monopack · Blue", "Monopack Blue")
        val byFile = names.groupBy { ExportJobs.packFileName(it) }
        for ((file, sameFile) in byFile) {
            // Names sharing a file must be the same pack (same package).
            assertThat(sameFile.map { PackNaming.packageFor(it) }.distinct()).hasSize(1)
            assertThat(file).endsWith(".apk")
        }
        assertThat(byFile).hasSize(names.size)
    }

    @Test
    fun theLibraryReadsTitlesBackWithoutTheTag() {
        fun title(name: String) = Library.untracked(FolderFile(ExportJobs.packFileName(name), "u", "d", 1, 1))!!.title
        assertThat(title("Monopack · Blue · Dark")).isEqualTo("Monopack · Blue · Dark")
        assertThat(title("Blue Night")).isEqualTo("BlueNight")
    }
}
