package dev.abhay.monopack.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/** Whether a pack's package is installed, and by which signer. */
enum class InstalledPack { NOT_INSTALLED, SAME_SIGNER, OTHER_SIGNER }

/** Looks up installed packs (visible through the icon-pack `<queries>` intent). */
fun interface PackInstalls {
    fun status(packageName: String): InstalledPack
}

class AndroidPackInstalls(private val context: Context, private val signer: PackSigner) : PackInstalls {
    override fun status(packageName: String): InstalledPack {
        val info = try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: PackageManager.NameNotFoundException) {
            return InstalledPack.NOT_INSTALLED
        }
        val ours = runCatching { signer.certificate().encoded }.onFailure { Log.w("Monopack", "No signing key", it) }.getOrNull()
        val theirs = info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        return if (ours != null && theirs.any { it.contentEquals(ours) }) InstalledPack.SAME_SIGNER else InstalledPack.OTHER_SIGNER
    }
}

/** A one-line hint for applying an icon pack in the default launcher. */
object LauncherHints {
    private val HINTS = mapOf(
        "app.lawnchair" to "In Lawnchair: open Settings and choose the pack under Icon pack.",
        "app.lawnchair.play" to "In Lawnchair: open Settings and choose the pack under Icon pack.",
        "com.teslacoilsw.launcher" to "In Nova: Settings → Look & feel → Icon style → Icon theme.",
        "com.mi.android.globallauncher" to "POCO Launcher doesn't support icon packs. Use the HyperOS theme, or a launcher such as Lawnchair.",
        "com.miui.home" to "The HyperOS launcher doesn't support icon packs. Use the HyperOS theme, or a launcher such as Lawnchair.",
    )
    private const val GENERIC = "Open your launcher's settings and choose the pack as its icon pack."

    fun forLauncher(packageName: String?): String = HINTS[packageName] ?: GENERIC

    /** The default launcher's package, or null. */
    fun defaultLauncher(context: Context): String? =
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
}
