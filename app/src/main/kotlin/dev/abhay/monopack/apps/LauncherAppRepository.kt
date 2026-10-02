package dev.abhay.monopack.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import dev.abhay.monopack.model.LauncherApp
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Source of launcher apps (an interface so the ViewModel can be tested with fakes). */
interface AppSource {
    suspend fun scan(): List<LauncherApp>
}

/**
 * Lists every launcher activity visible to the app, sorted like an app drawer: the main profile's,
 * plus those only in other profiles (a work profile, e.g. Island), through [LauncherApps]. An app
 * in both profiles has one component, so one icon covers both.
 */
class LauncherAppRepository(private val context: Context) : AppSource {

    override suspend fun scan(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        val mainKeys = resolved.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toSet()
        val others = otherProfileActivities().filter { it.componentName !in mainKeys }.distinctBy { it.componentName }

        val entries = resolved.map { ri ->
            val ai = ri.activityInfo
            ScannedEntry(
                packageName = ai.packageName,
                className = ai.name,
                label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName },
            )
        } + others.map { ScannedEntry(it.componentName.packageName, it.componentName.className, it.label.toString().trim().ifEmpty { it.componentName.packageName }) }
        val byKey = resolved.associateBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val otherByKey = others.associateBy { it.componentName }
        val lastUpdate = mutableMapOf<String, Long>()

        orderAndMarkMain(
            entries = entries,
            launchClassFor = { pkg -> pm.getLaunchIntentForPackage(pkg)?.component?.className },
        ).map { e ->
            val component = ComponentName(e.entry.packageName, e.entry.className)
            val main = byKey[component]
            if (main != null) {
                val ai = main.activityInfo
                LauncherApp(
                    component = component,
                    label = e.entry.label,
                    iconRes = ai.iconResource,
                    appInfo = ai.applicationInfo,
                    isMainActivity = e.isMain,
                    lastUpdateTime = lastUpdate.getOrPut(e.entry.packageName) {
                        runCatching { pm.getPackageInfo(e.entry.packageName, 0).lastUpdateTime }.getOrDefault(0L)
                    },
                )
            } else {
                val other = otherByKey.getValue(component)
                LauncherApp(
                    component = component,
                    label = e.entry.label,
                    iconRes = other.activityInfo.iconResource,
                    appInfo = other.applicationInfo,
                    isMainActivity = e.isMain,
                    // Not installed in this profile, so its install time stands in.
                    lastUpdateTime = other.firstInstallTime,
                    user = other.user,
                )
            }
        }
    }

    /** Launcher activities in the user's other profiles (empty when there are none or they're locked). */
    private fun otherProfileActivities(): List<LauncherActivityInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val me = Process.myUserHandle()
        return runCatching {
            launcherApps.profiles.filter { it != me }.flatMap { user ->
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            }
        }.getOrDefault(emptyList())
    }
}

/** Android-free view of a launcher entry, so ordering logic is unit-testable. */
data class ScannedEntry(val packageName: String, val className: String, val label: String)

data class OrderedEntry(val entry: ScannedEntry, val isMain: Boolean)

/**
 * Sorts entries by label (locale-aware, then by component for stability) and marks one "main"
 * activity per package: the package's launch-intent activity when it is a launcher entry,
 * otherwise the first entry of that package in drawer order.
 */
fun orderAndMarkMain(
    entries: List<ScannedEntry>,
    launchClassFor: (String) -> String?,
    collator: Collator = Collator.getInstance(),
): List<OrderedEntry> {
    val sorted = entries
        .distinctBy { it.packageName to it.className }
        .sortedWith(
            compareBy<ScannedEntry, String>(collator) { it.label }
                .thenBy { it.packageName }
                .thenBy { it.className },
        )

    val mainClass = sorted.groupBy { it.packageName }.mapValues { (pkg, group) ->
        if (group.size == 1) {
            group.single().className
        } else {
            val launch = launchClassFor(pkg)
            group.firstOrNull { it.className == launch }?.className ?: group.first().className
        }
    }
    return sorted.map { OrderedEntry(it, isMain = mainClass[it.packageName] == it.className) }
}
