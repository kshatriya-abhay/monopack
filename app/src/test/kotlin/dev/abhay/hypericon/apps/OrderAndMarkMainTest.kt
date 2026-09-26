package dev.abhay.hypericon.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.Collator
import java.util.Locale

class OrderAndMarkMainTest {
    private val collator = Collator.getInstance(Locale.US)

    private fun order(entries: List<ScannedEntry>, launch: Map<String, String> = emptyMap()) =
        orderAndMarkMain(entries, launchClassFor = { launch[it] }, collator = collator)

    @Test
    fun `sorts by label with locale-aware collation`() {
        val result = order(
            listOf(
                ScannedEntry("p.z", "p.z.Main", "zebra"),
                ScannedEntry("p.e", "p.e.Main", "Éclair"),
                ScannedEntry("p.a", "p.a.Main", "apple"),
            ),
        )
        assertThat(result.map { it.entry.label }).containsExactly("apple", "Éclair", "zebra").inOrder()
    }

    @Test
    fun `single activity package is main`() {
        val result = order(listOf(ScannedEntry("p", "p.Main", "App")))
        assertThat(result.single().isMain).isTrue()
    }

    @Test
    fun `multi activity package marks the launch intent activity as main`() {
        val result = order(
            entries = listOf(
                ScannedEntry("p", "p.Alpha", "Alpha"),
                ScannedEntry("p", "p.Beta", "Beta"),
            ),
            launch = mapOf("p" to "p.Beta"),
        )
        assertThat(result.filter { it.isMain }.map { it.entry.className }).containsExactly("p.Beta")
    }

    @Test
    fun `multi activity package without launch intent falls back to first in drawer order`() {
        val result = order(
            listOf(
                ScannedEntry("p", "p.Second", "Second"),
                ScannedEntry("p", "p.First", "First"),
            ),
        )
        assertThat(result.filter { it.isMain }.map { it.entry.className }).containsExactly("p.First")
    }

    @Test
    fun `duplicate components are removed and equal labels sort stably`() {
        val result = order(
            listOf(
                ScannedEntry("p.b", "p.b.Main", "Same"),
                ScannedEntry("p.a", "p.a.Main", "Same"),
                ScannedEntry("p.a", "p.a.Main", "Same"),
            ),
        )
        assertThat(result.map { it.entry.packageName }).containsExactly("p.a", "p.b").inOrder()
    }
}
