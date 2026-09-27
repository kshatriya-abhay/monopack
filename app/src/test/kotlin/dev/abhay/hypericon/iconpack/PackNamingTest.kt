package dev.abhay.hypericon.iconpack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackNamingTest {
    @Test
    fun `the same name gives the same package, a new name a new one`() {
        val a = PackNaming.packageFor("HyperIcon · Primary")
        // Case and extra whitespace don't make a different pack.
        assertThat(PackNaming.packageFor("  hypericon   ·  PRIMARY ")).isEqualTo(a)
        assertThat(PackNaming.packageFor("HyperIcon · Tertiary")).isNotEqualTo(a)
        assertThat(a).matches("dev\\.abhay\\.hypericon\\.pack\\.p[0-9a-f]{10}")
    }

    @Test
    fun `drawable names are valid resource names`() {
        assertThat(PackNaming.drawableName("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail", true))
            .isEqualTo("com_google_android_gm")
        val alias = PackNaming.drawableName("com.flipkart.android", "com.flipkart.android.BBDIconAlias", false)
        assertThat(alias).matches("com_flipkart_android_bbdiconalias_[0-9a-f]{4}")
        assertThat(PackNaming.drawableName("1weird.pkg", "X", true)).matches("[a-z][a-z0-9_]*")
    }

    @Test
    fun `duplicates get a suffix`() {
        assertThat(PackNaming.dedupe(listOf("a", "b", "a", "a"))).containsExactly("a", "b", "a_2", "a_3").inOrder()
    }
}
