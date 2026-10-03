package dev.abhay.monopack.data

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconStyle
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupFilesTest {
    private val dispatcher = StandardTestDispatcher()
    private val files = BackupFiles(RuntimeEnvironment.getApplication().contentResolver, dispatcher)
    private val dir = createTempDirectory().toFile()

    @Test
    fun aWrittenBackupReadsBack() = runTest(dispatcher) {
        val uri = Uri.fromFile(File(dir, "backup.json")).toString()
        val backup = Backup(mapOf("a.pkg/.Main" to IconEdit(IconStyle.DARK, contrast = 40)), selection = null, iconShape = "CIRCLE")
        files.write(uri, backup)
        assertThat(files.read(uri)).isEqualTo(backup)
    }

    @Test
    fun otherFilesReadAsNull() = runTest(dispatcher) {
        val file = File(dir, "notes.txt").apply { writeText("hello") }
        assertThat(files.read(Uri.fromFile(file).toString())).isNull()
    }
}
