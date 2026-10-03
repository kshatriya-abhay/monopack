package dev.abhay.monopack.data

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.model.Accent
import dev.abhay.monopack.model.ColorSource
import dev.abhay.monopack.model.IconEdit
import dev.abhay.monopack.model.IconStyle
import dev.abhay.monopack.model.Selection
import dev.abhay.monopack.palette.SeedPresets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json needs Android's implementation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupJsonTest {
    @Test
    fun aBackupRoundTrips() {
        val backup = Backup(
            edits = mapOf(
                "a.pkg/.Main" to IconEdit(IconStyle.DARK, glyphToneOffset = 3, plateToneOffset = -2, inverted = true, contrast = 60, autoNight = false),
                "b.pkg/.Main" to IconEdit(IconStyle.LIGHT),
            ),
            selection = Selection(IconStyle.LIGHT, Accent.TERTIARY, ColorSource.CUSTOM, SeedPresets.AOSP[2]),
            iconShape = "CIRCLE",
        )
        assertThat(BackupJson.decode(BackupJson.encode(backup, createdAt = 1))).isEqualTo(backup)
    }

    @Test
    fun otherFilesAreRejected() {
        assertThat(BackupJson.decode("not json")).isNull()
        assertThat(BackupJson.decode("""{"edits":{}}""")).isNull()
        assertThat(BackupJson.decode("""{"app":"Monopack","version":99,"edits":{}}""")).isNull()
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val json = """{"app":"Monopack","version":1,"edits":{"a/.M":{"base":"DARK","glyph":5000,"plate":-5000,"contrast":99999}}}"""
        val edit = BackupJson.decode(json)!!.edits.getValue("a/.M")
        assertThat(edit.glyphToneOffset).isEqualTo(100)
        assertThat(edit.plateToneOffset).isEqualTo(-100)
        assertThat(edit.contrast).isEqualTo(100)
    }

    @Test
    fun aMinimalBackupLoads() {
        val backup = BackupJson.decode("""{"app":"Monopack","version":1}""")!!
        assertThat(backup.edits).isEmpty()
        assertThat(backup.selection).isNull()
        assertThat(backup.iconShape).isNull()
    }
}
