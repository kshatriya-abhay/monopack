package dev.abhay.monopack.newapps

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.abhay.monopack.MainActivity
import dev.abhay.monopack.R
import dev.abhay.monopack.export.InstalledPack
import dev.abhay.monopack.export.PackInstalls
import dev.abhay.monopack.iconpack.PackNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks for apps the watched icon pack doesn't cover and posts one notification listing them.
 * Apps can't tell which pack a launcher uses, so the user picks it in Settings; with a single
 * Monopack pack installed (signed by this app), that one is used until they do.
 */
class NewAppCheck(private val context: Context, private val store: NewAppStore, private val installs: PackInstalls) {
    suspend fun run() {
        if (!store.loadEnabled()) return
        val found = find() ?: return
        store.saveNotified(found.notified)
        if (found.fresh.isEmpty()) return
        Log.i(TAG, "New apps not in ${found.pack.label}: ${found.fresh.joinToString { it.component }}")
        post(found.fresh, found.pack)
    }

    /**
     * What a check found: the pack, every new app it doesn't cover ([uncovered], for the home
     * screen), the ones not notified about yet ([fresh]), and the notified set to keep.
     */
    data class Found(val pack: CoveringPack, val uncovered: List<InstalledApp>, val fresh: List<InstalledApp>, val notified: Set<String>)

    /** Runs the check without notifying or saving; null without a Monopack pack installed. */
    suspend fun find(): Found? = withContext(Dispatchers.IO) { findNow() }

    /** Installed Monopack packs, by label. */
    suspend fun packs(): List<WatchablePack> = withContext(Dispatchers.IO) {
        monopackPacks().map { WatchablePack(it.packageName, label(it)) }.sortedBy { it.label.lowercase() }
    }

    private suspend fun findNow(): Found? {
        val pack = watchedPack() ?: return null
        val apps = launcherApps()
        val notified = store.loadNotified()
        val fresh = NewApps.toNotify(apps, pack, notified)
        // Forget apps the pack covers now (re-exported) or that were uninstalled.
        val uncovered = NewApps.uncovered(apps, pack).sortedBy { it.label.lowercase() }
        val stillUncovered = uncovered.map { it.component }.toSet()
        return Found(pack, uncovered, fresh, (notified intersect stillUncovered) + fresh.map { it.component })
    }

    private fun monopackPacks(): List<PackageInfo> {
        val pm = context.packageManager
        // Packs answer the ADW action, the one in the manifest's <queries>.
        return pm.queryIntentActivities(Intent(ICON_PACK_ACTION), PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it.startsWith(PackNaming.PACKAGE_PREFIX + ".") && installs.status(it) == InstalledPack.SAME_SIGNER }
            .mapNotNull { pkg -> runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() }
    }

    /** The chosen pack if it's installed, else the only installed pack; null otherwise. */
    private suspend fun watchedPack(): CoveringPack? {
        val packs = monopackPacks()
        val chosen = store.loadWatchedPack()
        val pack = packs.firstOrNull { it.packageName == chosen } ?: packs.singleOrNull() ?: return null
        val xml = runCatching {
            context.createPackageContext(pack.packageName, 0).assets.open("appfilter.xml").use { it.readBytes().decodeToString() }
        }.onFailure { Log.w(TAG, "Couldn't read ${pack.packageName}", it) }.getOrNull() ?: return null
        return CoveringPack(label(pack), pack.lastUpdateTime, NewApps.components(xml))
    }

    private fun label(pack: PackageInfo): String =
        pack.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: pack.packageName

    private fun launcherApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = mutableMapOf<String, Long>()
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                InstalledApp(
                    component = "$pkg/${ri.activityInfo.name}",
                    label = ri.loadLabel(pm).toString().trim().ifEmpty { pkg },
                    firstInstallTime = installed.getOrPut(pkg) { runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L) },
                )
            }
    }

    private fun post(apps: List<InstalledApp>, pack: CoveringPack) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val title = if (apps.size == 1) "${apps[0].label} has no themed icon" else "${apps.size} new apps have no themed icon"
        val text = "${NewApps.names(apps)} ${if (apps.size == 1) "isn't" else "aren't"} in ${pack.label}. Tap to update the pack."
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_CREATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "Monopack"
        private const val CHANNEL = "new_apps"
        private const val NOTIFICATION_ID = 10
        private const val ICON_PACK_ACTION = "org.adw.launcher.THEMES"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "New apps", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Apps you install that your icon pack doesn't cover yet"
                    },
                )
            }
        }
    }
}
