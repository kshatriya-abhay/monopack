package dev.abhay.monopack.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenSourceLicensesTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun everyLicenseTextIsShipped() {
        for (license in LicenseText.entries) {
            val text = resources.openRawResource(license.text).bufferedReader().use { it.readText() }
            assertThat(text.length).isGreaterThan(500)
        }
        val apache = resources.openRawResource(LicenseText.APACHE_2_0.text).bufferedReader().use { it.readText() }
        assertThat(apache).contains("Version 2.0, January 2004")
        val gpl = resources.openRawResource(LicenseText.GPL_3_0.text).bufferedReader().use { it.readText() }
        assertThat(gpl).contains("GNU GENERAL PUBLIC LICENSE")
    }

    @Test
    fun licenseTextsAreReflowedIntoParagraphs() {
        assertThat(reflow("   Title\n\n  one two\n  three\n\n\n  four\n")).isEqualTo("Title\n\none two three\n\nfour")
    }

    @Test
    fun theNoticesNameEveryBundledLibraryFamily() {
        val names = OPEN_SOURCE_NOTICES.joinToString { it.name }
        for (library in listOf("AndroidX", "Kotlin", "ARSCLib", "apksig", "MaterialKolor", "Protocol Buffers", "Okio", "Poko", "Guava", "JSpecify")) {
            assertThat(names).contains(library)
        }
    }
}
