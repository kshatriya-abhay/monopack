package dev.abhay.monopack.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import dev.abhay.monopack.R

/** Whether a pack's package is installed, and by which signer. */
enum class InstalledPack { NOT_INSTALLED, SAME_SIGNER, OTHER_SIGNER }

/** Looks up installed packs (visible through the icon-pack `<queries>` intent). */
fun interface PackInstalls {
    fun status(packageName: String): InstalledPack
}

class AndroidPackInstalls(private val context: Context, private val signer: PackSigner) : PackInstalls {
    /** Monopack's signing certificate (PackSigner keeps it once read); a failed read is retried next time. */
    private fun ourCertificate(): ByteArray? =
        runCatching { signer.certificate().encoded }.onFailure { Log.w("Monopack", "No signing key", it) }.getOrNull()

    override fun status(packageName: String): InstalledPack {
        val info = try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: PackageManager.NameNotFoundException) {
            return InstalledPack.NOT_INSTALLED
        }
        val ours = ourCertificate()
        val theirs = info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        return if (ours != null && theirs.any { it.contentEquals(ours) }) InstalledPack.SAME_SIGNER else InstalledPack.OTHER_SIGNER
    }
}

/** A one-line hint for applying an icon pack in the default launcher. */
object LauncherHints {
    private val HINTS = mapOf(
        "app.lawnchair" to R.string.hint_lawnchair,
        "app.lawnchair.play" to R.string.hint_lawnchair,
        "com.teslacoilsw.launcher" to R.string.hint_nova,
        "com.mi.android.globallauncher" to R.string.hint_poco,
        "com.miui.home" to R.string.hint_hyperos,
    )

    /** How to apply a pack in [packageName]'s launcher. */
    @StringRes
    fun forLauncher(packageName: String?): Int = HINTS[packageName] ?: R.string.hint_generic

    /**
     * How to see an updated pack's new icons. Lawnchair loads a pack once per process and keeps it
     * (its `IconPackProvider` never drops a loaded pack), so picking the pack again doesn't help.
     */
    @StringRes
    fun afterUpdate(packageName: String?): Int = when (packageName) {
        "app.lawnchair", "app.lawnchair.play" -> R.string.hint_update_lawnchair
        else -> R.string.hint_update_generic
    }

    /** The default launcher's package, or null. */
    fun defaultLauncher(context: Context): String? =
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
}
