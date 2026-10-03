package dev.abhay.monopack.export

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstallGateTest {
    private val pack = PackToInstall("content://x", "Monopack", "p")

    @Test
    fun nothingRunningStarts() {
        assertThat(InstallGate.decide(InstallState.Idle, now = 0)).isEqualTo(InstallGate.Decision.Start)
        assertThat(InstallGate.decide(InstallState.Done(pack, false, true, null), now = 0)).isEqualTo(InstallGate.Decision.Start)
    }

    @Test
    fun aSessionBeingWrittenIsLeftAloneForAWhile() {
        val writing = InstallState.Installing(pack, update = true, sessionId = 7, startedAt = 1_000)
        assertThat(InstallGate.decide(writing, now = 2_000)).isEqualTo(InstallGate.Decision.Busy)
        // A lost callback can't block installs forever.
        assertThat(InstallGate.decide(writing, now = 1_000 + InstallGate.MAX_BUSY_MS + 1)).isEqualTo(InstallGate.Decision.Replace(7))
    }

    @Test
    fun aConfirmationTheUserLeftIsReplaced() {
        val waiting = InstallState.Installing(pack, update = false, sessionId = 7, awaitingUser = true, startedAt = 1_000)
        assertThat(InstallGate.decide(waiting, now = 2_000)).isEqualTo(InstallGate.Decision.Replace(7))
        val asking = InstallState.NeedsConfirmation(pack, update = false, intent = Intent(), sessionId = 8)
        assertThat(InstallGate.decide(asking, now = 2_000)).isEqualTo(InstallGate.Decision.Replace(8))
    }

    @Test
    fun resultsFromAReplacedSessionAreIgnored() {
        val current = InstallState.Installing(pack, update = false, sessionId = 9)
        assertThat(InstallGate.isCurrent(current, sessionId = 9)).isTrue()
        assertThat(InstallGate.isCurrent(current, sessionId = 7)).isFalse()
        assertThat(InstallGate.isCurrent(InstallState.Idle, sessionId = 9)).isFalse()
    }
}
