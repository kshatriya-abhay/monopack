package dev.abhay.monopack.newapps

import android.content.res.Resources
import dev.abhay.monopack.R

/** A launcher entry, reduced to what the new-app check needs. */
data class InstalledApp(
    /** `package/class`, as in an icon pack's `appfilter.xml`. */
    val component: String,
    val label: String,
    val firstInstallTime: Long,
)

/** An installed Monopack icon pack the user can pick to watch. */
data class WatchablePack(val packageName: String, val label: String)

/** The watched icon pack: its name, when it was installed or updated, and what it covers. */
data class CoveringPack(val label: String, val updatedAt: Long, val components: Set<String>, val packageName: String = "")

/**
 * Finding apps installed after the current icon pack was made, which the pack has no icon for
 * (so the launcher shows their own icon).
 */
object NewApps {
    /**
     * The apps to tell the user about: not covered by [pack], installed after the pack was, and not
     * already in a notification ([notified]). Sorted by label.
     */
    fun toNotify(apps: List<InstalledApp>, pack: CoveringPack, notified: Set<String>): List<InstalledApp> =
        uncovered(apps, pack).filter { it.component !in notified }.sortedBy { it.label.lowercase() }

    /** New apps the pack doesn't cover (notified or not). */
    fun uncovered(apps: List<InstalledApp>, pack: CoveringPack): List<InstalledApp> =
        apps.filter { it.component !in pack.components && it.firstInstallTime > pack.updatedAt }

    /** The components in an icon pack's `appfilter.xml` (`ComponentInfo{package/class}` → `package/class`). */
    fun components(appFilterXml: String): Set<String> =
        COMPONENT.findAll(appFilterXml).map { unescape(it.groupValues[1]) }.toSet()

    /** The apps' names for the banner and notification: "Swiggy", "Swiggy and Zepto", "Swiggy, Zepto and 3 more". */
    fun names(apps: List<InstalledApp>, resources: Resources): String {
        val labels = apps.map { it.label }
        return when (labels.size) {
            0 -> ""
            1 -> labels[0]
            2 -> resources.getString(R.string.new_apps_two, labels[0], labels[1])
            else -> resources.getQuantityString(R.plurals.new_apps_more, labels.size - 2, labels[0], labels[1], labels.size - 2)
        }
    }

    private fun unescape(value: String) = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private val COMPONENT = Regex("""component="ComponentInfo\{([^}]*)\}"""")
}
