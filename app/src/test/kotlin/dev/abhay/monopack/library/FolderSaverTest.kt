package dev.abhay.monopack.library

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportKind
import dev.abhay.monopack.export.ExportSaver
import dev.abhay.monopack.export.SavedExport
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderSaverTest {
    private val tree = "content://tree/t"
    private val store = FakeLibraryStore(tree)
    private val folder = FakeLibraryFolder().apply { granted += tree }
    private val saver = FolderSaver(store, folder, DownloadsSaver(RuntimeEnvironment.getApplication()))

    private fun record(name: String, pkg: String) = ExportRecord(name, ExportKind.ICON_PACK, "Monopack", null, 1, 200, packageName = pkg)

    @Test
    fun aPackReplacesItsFileAndItsOlderDateStampedExports() = runTest {
        folder.add("Monopack.apk")
        folder.add("Monopack-pack-20260927-1015.apk")
        folder.add("Other-pack-20260927-1015.apk")
        store.records = listOf(
            record("Monopack.apk", "p.one"),
            record("Monopack-pack-20260927-1015.apk", "p.one"),
            record("Other-pack-20260927-1015.apk", "p.other"),
        ).associateBy { it.fileName }

        val file = File("build/Monopack.apk") // the fake folder only uses the name
        val saved = saver.save(file, packageName = "p.one")

        assertThat(saved.name).isEqualTo("Monopack.apk")
        assertThat(folder.files.map { it.name }).containsExactly("Other-pack-20260927-1015.apk", "Monopack.apk")
        assertThat(store.records.keys).containsExactly("Monopack.apk", "Other-pack-20260927-1015.apk")
    }

    @Test
    fun aFolderThatIsGoneFallsBackToDownloads() = runTest {
        val fallback = object : ExportSaver {
            var saved: File? = null
            override suspend fun save(file: File, packageName: String?): SavedExport {
                saved = file
                return SavedExport("content://downloads/1", "Download/Monopack/${file.name}", "")
            }
            override suspend fun copyTo(file: File, uri: String) = Unit
        }
        // The folder was deleted in a file manager: the grant may remain, but it isn't usable.
        folder.granted -= tree
        val file = File("build/Monopack.apk")
        val saved = FolderSaver(store, folder, fallback).save(file, packageName = "p.one")
        assertThat(fallback.saved).isEqualTo(file)
        assertThat(saved.displayPath).isEqualTo("Download/Monopack/Monopack.apk")
        assertThat(folder.files).isEmpty()
    }
}
