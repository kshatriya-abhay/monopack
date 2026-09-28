package dev.abhay.monopack.newapps

import com.google.common.truth.Truth.assertThat
import dev.abhay.monopack.iconpack.AppFilterItem
import dev.abhay.monopack.iconpack.AppFilterXml
import org.junit.Test

class NewAppsTest {
    private val pack = CoveringPack("Monopack", updatedAt = 1_000, components = setOf("a.pkg/a.Main", "b.pkg/b.Main"))

    private fun app(component: String, label: String, installed: Long) = InstalledApp(component, label, installed)

    @Test
    fun onlyAppsInstalledAfterThePackAndNotInItAreNew() {
        val apps = listOf(
            app("a.pkg/a.Main", "A", 2_000), // covered (updated after the pack: still covered)
            app("c.pkg/c.Main", "Old", 500), // not covered, but was there when the pack was made
            app("z.pkg/z.Main", "zepto", 1_500),
            app("s.pkg/s.Main", "Swiggy", 1_200),
        )
        assertThat(NewApps.toNotify(apps, pack, emptySet()).map { it.label }).containsExactly("Swiggy", "zepto").inOrder()
    }

    @Test
    fun appsAlreadyNotifiedAreSkipped() {
        val apps = listOf(app("s.pkg/s.Main", "Swiggy", 1_200), app("z.pkg/z.Main", "Zepto", 1_500))
        assertThat(NewApps.toNotify(apps, pack, setOf("s.pkg/s.Main")).map { it.label }).containsExactly("Zepto")
        assertThat(NewApps.uncovered(apps, pack)).hasSize(2)
    }

    @Test
    fun readsComponentsFromTheAppFilterMonopackWrites() {
        val xml = AppFilterXml.appFilter(
            listOf(AppFilterItem("a.pkg", "a.Main", "a_pkg"), AppFilterItem("b&c.pkg", "b.Main\$Inner", "b_pkg")),
        )
        assertThat(NewApps.components(xml)).containsExactly("a.pkg/a.Main", "b&c.pkg/b.Main\$Inner")
    }

    @Test
    fun namesAreShortened() {
        val apps = listOf("A", "B", "C", "D").map { app("$it/x", it, 0) }
        assertThat(NewApps.names(apps.take(1))).isEqualTo("A")
        assertThat(NewApps.names(apps.take(2))).isEqualTo("A and B")
        assertThat(NewApps.names(apps)).isEqualTo("A, B and 2 more")
    }
}
