package dev.abhay.monopack.export

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class VersionCodeTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun aLaterExportGetsAHigherCodeAcrossTheDstFallBack() {
        // 01:50 EDT, then 40 minutes later 01:30 EST (the clock went back an hour at 02:00).
        val first = ZonedDateTime.of(2026, 11, 1, 1, 50, 0, 0, newYork).withEarlierOffsetAtOverlap()
        val second = first.plusMinutes(40)
        assertThat(second.toLocalTime().toString()).isEqualTo("01:30")
        val a = ExportJobs.versionCode(first.toInstant().toEpochMilli(), installedVersion = null)
        val b = ExportJobs.versionCode(second.toInstant().toEpochMilli(), installedVersion = a.toLong())
        assertThat(b).isGreaterThan(a)
    }

    @Test
    fun aClockSetBackStillUpdatesTheInstalledPack() {
        val installed = 30_000_000L
        val code = ExportJobs.versionCode(epochMillis = 1_000L * 60 * 1_000, installedVersion = installed)
        assertThat(code).isEqualTo(installed + 1)
    }
}
