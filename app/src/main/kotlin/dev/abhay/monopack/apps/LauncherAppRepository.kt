package dev.abhay.monopack.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.abhay.monopack.model.LauncherApp
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Source of launcher apps (an interface so the ViewModel can be tested with fakes). */
interface AppSource {
    suspend fun scan(): List<LauncherApp>
}

/** Lists every launcher activity visible to the app, sorted like an app drawer. */
class LauncherAppRepository(private val context: Context) : AppSource {

    override suspend fun scan(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))

        val entries = resolved.map { ri ->
            val ai = ri.activityInfo
            ScannedEntry(
                packageName = ai.packageName,
                className = ai.name,
                label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName },
            )
        }
        val byKey = resolved.associateBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val lastUpdate = mutableMapOf<String, Long>()

        orderAndMarkMain(
            entries = entries,
            launchClassFor = { pkg -> pm.getLaunchIntentForPackage(pkg)?.component?.className },
        ).map { e ->
            val component = ComponentName(e.entry.packageName, e.entry.className)
            val ai = byKey.getValue(component).activityInfo
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
        }
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
