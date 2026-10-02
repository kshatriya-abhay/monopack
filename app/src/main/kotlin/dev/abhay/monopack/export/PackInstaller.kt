package dev.abhay.monopack.export

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.util.catching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A saved icon pack to install: its file (a content URI) and label. */
data class PackToInstall(val uri: String, val title: String, val packageName: String?)

/** Where an install is. */
sealed interface InstallState {
    data object Idle : InstallState

    data class Installing(val pack: PackToInstall, val update: Boolean) : InstallState

    /** Android wants the user to confirm (first install, or a pack another app installed). */
    data class NeedsConfirmation(val pack: PackToInstall, val update: Boolean, val intent: Intent) : InstallState

    data class Done(val pack: PackToInstall, val update: Boolean, val success: Boolean, val message: String?) : InstallState
}

/**
 * Installs icon packs with [PackageInstaller] (needs `REQUEST_INSTALL_PACKAGES` and the user's
 * "Install unknown apps" allowance). Packs Monopack installed update without a prompt on Android
 * 12+ (`USER_ACTION_NOT_REQUIRED`); otherwise Android asks, and [state] carries its screen.
 */
class PackInstaller(
    private val context: Context,
    /** App-wide, so an install isn't cut off when the screen that started it goes away. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** Whether the user allows Monopack to install apps. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen for allowing it. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Starts installing [pack] unless an install is under way; progress and the result arrive in [state]. */
    fun install(pack: PackToInstall) {
        if (_state.value is InstallState.Installing || _state.value is InstallState.NeedsConfirmation) return
        scope.launch { installNow(pack) }
    }

    private suspend fun installNow(pack: PackToInstall) {
        val update = pack.packageName?.let { isInstalled(it) } ?: false
        _state.value = InstallState.Installing(pack, update)
        catching {
            withContext(Dispatchers.IO) {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    pack.packageName?.let(::setAppPackageName)
                }
                val id = installer.createSession(params)
                try {
                    installer.openSession(id).use { session ->
                        context.contentResolver.openInputStream(pack.uri.toUri())?.use { input ->
                            session.openWrite("pack.apk", 0, -1).use { out ->
                                input.copyTo(out)
                                session.fsync(out)
                            }
                        } ?: error("Can't read ${pack.title}")
                        val status = Intent(context, InstallStatusReceiver::class.java)
                        // Mutable: the installer adds the result extras.
                        val pending = PendingIntent.getBroadcast(context, id, status, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        session.commit(pending.intentSender)
                    }
                } catch (e: Exception) {
                    // Don't leave a half-written session behind (they count against a per-app limit).
                    runCatching { installer.abandonSession(id) }
                    throw e
                }
            }
        }.onFailure {
            Log.w(TAG, "Install failed", it)
            _state.value = InstallState.Done(pack, update, success = false, message = it.message)
        }
    }

    /** A result from the installer (through [InstallStatusReceiver]). */
    fun onStatus(intent: Intent) {
        val current = _state.value
        val (pack, update) = when (current) {
            is InstallState.Installing -> current.pack to current.update
            is InstallState.NeedsConfirmation -> current.pack to current.update
            else -> return
        }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) InstallState.Done(pack, update, false, "Android didn't ask to confirm") else InstallState.NeedsConfirmation(pack, update, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> InstallState.Done(pack, update, success = true, message = null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Done(pack, update, success = false, message = null)
            else -> InstallState.Done(pack, update, success = false, message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    /** The confirmation screen was opened; wait for its result. */
    fun confirmationShown() {
        val current = _state.value as? InstallState.NeedsConfirmation ?: return
        _state.value = InstallState.Installing(current.pack, current.update)
    }

    fun dismiss() {
        if (_state.value is InstallState.Done) _state.value = InstallState.Idle
    }

    private fun isInstalled(packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private companion object {
        const val TAG = "Monopack"
    }
}

/** Receives [PackageInstaller] results for [PackInstaller]. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.appContainer.packInstaller.onStatus(intent)
    }
}
