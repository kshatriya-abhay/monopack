package dev.abhay.monopack.newapps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// The names come from string resources.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewAppsNamesTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun namesAreShortened() {
        val apps = listOf("A", "B", "C", "D").map { InstalledApp("$it/x", it, 0) }
        assertThat(NewApps.names(apps.take(1), resources)).isEqualTo("A")
        assertThat(NewApps.names(apps.take(2), resources)).isEqualTo("A and B")
        assertThat(NewApps.names(apps.take(3), resources)).isEqualTo("A, B and 1 more")
        assertThat(NewApps.names(apps, resources)).isEqualTo("A, B and 2 more")
    }
}
