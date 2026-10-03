package dev.abhay.monopack.export

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsSaverTest {
    /** Runs work on its own thread and counts how often it's used. */
    private class CountingDispatcher : CoroutineDispatcher() {
        private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        var used = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            used++
            thread.dispatch(context, block)
        }
    }

    @Test
    fun saveAsCopiesOffTheCallersThread() = runBlocking {
        val dir = createTempDirectory().toFile()
        val source = File(dir, "pack.apk").apply { writeBytes(ByteArray(64 * 1024) { it.toByte() }) }
        val target = File(dir, "copy.apk")
        val io = CountingDispatcher()
        DownloadsSaver(RuntimeEnvironment.getApplication(), io).copyTo(source, Uri.fromFile(target).toString())
        assertThat(io.used).isGreaterThan(0)
        assertThat(target.readBytes()).isEqualTo(source.readBytes())
    }

    @Test
    fun saveAsFailsInsteadOfWritingAnEmptyFileWhenTheExportIsGone() = runBlocking {
        val dir = createTempDirectory().toFile()
        val target = File(dir, "copy.apk")
        val result = runCatching { DownloadsSaver(RuntimeEnvironment.getApplication(), CountingDispatcher()).copyTo(File(dir, "gone.apk"), Uri.fromFile(target).toString()) }
        assertThat(result.isFailure).isTrue()
        assertThat(target.exists() && target.length() > 0).isFalse()
    }
}
