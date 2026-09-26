package dev.abhay.hypericon.debug

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a real test: runs [IconDumper] inside the app process without any UI.
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.abhay.hypericon.debug.IconDumpTest
 * Optional: -Pandroid.testInstrumentationRunnerArguments.packages=a.b,c.d
 */
@RunWith(AndroidJUnit4::class)
class IconDumpTest {
    @Test
    fun dumpIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val only = InstrumentationRegistry.getArguments().getString("packages")
            ?.split(',')?.map { it.trim() }?.toSet()
        IconDumper.dump(context, only)
    }
}
