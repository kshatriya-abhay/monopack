package dev.abhay.monopack.library

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.export.DownloadsSaver
import dev.abhay.monopack.export.ExportKind
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
}
