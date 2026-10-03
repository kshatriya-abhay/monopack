package dev.abhay.monopack.export

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import dev.abhay.monopack.R
import java.io.File
import java.util.Collections
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.iconpack.PackNaming
import dev.abhay.monopack.util.catching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** A saved icon pack to install: its file (a content URI) and label. */
data class PackToInstall(val uri: String, val title: String, val packageName: String?)

/** Where an install is. */
sealed interface InstallState {
    data object Idle : InstallState

    /**
     * Writing or committing [sessionId] (null until the session exists), or waiting for the user on
     * Android's confirmation screen ([awaitingUser]), which may never report back if they leave it.
     */
    data class Installing(
        val pack: PackToInstall,
        val update: Boolean,
        val sessionId: Int? = null,
        val awaitingUser: Boolean = false,
        val startedAt: Long = 0,
        /** The session asked to install without a prompt (it may be refused, then retried asking). */
        val silent: Boolean = true,
    ) : InstallState

    /** Android wants the user to confirm (first install, or a pack another app installed). */
    data class NeedsConfirmation(val pack: PackToInstall, val update: Boolean, val intent: Intent, val sessionId: Int?) : InstallState

    data class Done(val pack: PackToInstall, val update: Boolean, val success: Boolean, val message: String?) : InstallState
}

/**
 * Installs icon packs with [PackageInstaller] (needs `REQUEST_INSTALL_PACKAGES` and the user's
 * "Install unknown apps" allowance). Packs Monopack installed update without a prompt on Android
 * 12+ (`USER_ACTION_NOT_REQUIRED`); otherwise Android asks, and [state] carries its screen.
 */
class PackInstaller(
    private val context: Context,
    /** Monopack's signing certificate: only packs signed with it are installed. */
    private val trustedCertificate: () -> ByteArray?,
    /** App-wide, so an install isn't cut off when the screen that started it goes away. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)

    /**
     * Whether silent updates are allowed here. Some systems (HyperOS) reject a session that asks
     * for no prompt ("Permission denied") instead of asking the user; then installs ask.
     */
    @Volatile
    private var silentAllowed = true

    /** Sessions replaced by a newer install (their results are ignored). */
    private val abandoned = Collections.synchronizedSet(mutableSetOf<Int>())
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** Whether the user allows Monopack to install apps. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen for allowing it. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Starts installing [pack] unless an install is under way; progress and the result arrive in [state]. */
    fun install(pack: PackToInstall) {
        when (val decision = InstallGate.decide(_state.value, SystemClock.elapsedRealtime())) {
            InstallGate.Decision.Busy -> return
            is InstallGate.Decision.Replace -> decision.sessionId?.let { id ->
                // Its "aborted" result arrives later; it must not count as the new install's result.
                abandoned += id
                runCatching { context.packageManager.packageInstaller.abandonSession(id) }
            }
            InstallGate.Decision.Start -> Unit
        }
        scope.launch { installNow(pack) }
    }

    private suspend fun installNow(pack: PackToInstall) {
        val update = pack.packageName?.let { isInstalled(it) } ?: false
        val silent = silentAllowed
        _state.value = InstallState.Installing(pack, update, startedAt = SystemClock.elapsedRealtime(), silent = silent)
        catching {
            withContext(Dispatchers.IO) {
                // Install a private copy, checked first: the library folder is shared storage, so
                // another app with broad file access could have swapped the file.
                val apk = File(context.cacheDir, "install/pack.apk").apply { parentFile?.mkdirs() }
                try {
                    context.contentResolver.openInputStream(pack.uri.toUri())?.use { input -> apk.outputStream().use { input.copyTo(it) } }
                        ?: error(context.getString(R.string.install_cant_read, pack.title))
                    val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    val info = context.packageManager.getPackageArchiveInfo(apk.path, flags)
                    PackVerifier.problem(
                        packageName = info?.packageName,
                        signers = info?.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty(),
                        expectedPackage = pack.packageName,
                        trusted = trustedCertificate(),
                    )?.let { error(context.getString(it)) }
                    commit(apk, pack.packageName ?: info?.packageName, silent)
                } finally {
                    apk.delete()
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
        // A replaced session can still report (e.g. "aborted"); only the current one counts.
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId in abandoned || !InstallGate.isCurrent(current, sessionId)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status != PackageInstaller.STATUS_SUCCESS) Log.i(TAG, "Install session $sessionId: status $status ($message)")
        // A silent install refused before any prompt (HyperOS: "Permission denied"): ask the user
        // instead, now and from then on. Other aborts stand: a user's "Cancel", or Play Protect
        // turning the install down.
        if (InstallGate.silentRefused(status, message) && current is InstallState.Installing && current.silent && !current.awaitingUser) {
            Log.i(TAG, "Silent install refused; asking the user instead")
            silentAllowed = false
            scope.launch { installNow(pack) }
            return
        }
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    InstallState.Done(pack, update, false, "Android didn't ask to confirm")
                } else {
                    InstallState.NeedsConfirmation(pack, update, confirm, intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1).takeIf { it >= 0 })
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallState.Done(pack, update, success = true, message = null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Done(pack, update, success = false, message = null)
            else -> InstallState.Done(pack, update, success = false, message = message)
        }
    }

    /** The confirmation screen was opened; wait for its result. */
    fun confirmationShown() {
        val current = _state.value as? InstallState.NeedsConfirmation ?: return
        _state.value = InstallState.Installing(current.pack, current.update, current.sessionId, awaitingUser = true, startedAt = SystemClock.elapsedRealtime())
    }

    fun dismiss() {
        if (_state.value is InstallState.Done) _state.value = InstallState.Idle
    }

    private fun commit(apk: File, packageName: String?, silent: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setRequireUserAction(
                if (silent) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED else PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
            )
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            packageName?.let(::setAppPackageName)
        }
        val id = installer.createSession(params)
        _state.update { (it as? InstallState.Installing)?.copy(sessionId = id) ?: it }
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("pack.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
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

    private fun isInstalled(packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private companion object {
        const val TAG = "Monopack"
    }
}

/** Whether a new install may start, given the current one. */
object InstallGate {
    sealed interface Decision {
        data object Start : Decision

        /** Busy writing or committing a session: ignore the tap. */
        data object Busy : Decision

        /** Abandon the session waiting for the user (they may have left the confirmation) and start. */
        data class Replace(val sessionId: Int?) : Decision
    }

    /** Longest an install may hold off new ones without asking the user (a lost callback can't block forever). */
    const val MAX_BUSY_MS = 2 * 60 * 1000L

    fun decide(state: InstallState, now: Long): Decision = when (state) {
        is InstallState.NeedsConfirmation -> Decision.Replace(state.sessionId)
        is InstallState.Installing -> when {
            state.awaitingUser -> Decision.Replace(state.sessionId)
            now - state.startedAt > MAX_BUSY_MS -> Decision.Replace(state.sessionId)
            else -> Decision.Busy
        }
        else -> Decision.Start
    }

    /** Whether a result for [sessionId] belongs to the install in [state]. */
    fun isCurrent(state: InstallState, sessionId: Int): Boolean {
        val current = when (state) {
            is InstallState.Installing -> state.sessionId
            is InstallState.NeedsConfirmation -> state.sessionId
            else -> return false
        }
        return current == null || sessionId < 0 || current == sessionId
    }

    /**
     * Whether a result is the system refusing a silent install outright (HyperOS reports
     * "INSTALL_FAILED_ABORTED: Permission denied"), as opposed to the user or Play Protect saying no.
     */
    fun silentRefused(status: Int, message: String?): Boolean =
        status == PackageInstaller.STATUS_FAILURE_ABORTED && message?.contains("Permission denied", ignoreCase = true) == true
}

/** Checks that a file is one of this Monopack's icon packs before it's installed. */
object PackVerifier {
    /** Why the file shouldn't be installed (a string resource), or null if it's fine. */
    @StringRes
    fun problem(packageName: String?, signers: List<ByteArray>, expectedPackage: String?, trusted: ByteArray?): Int? = when {
        packageName == null || !packageName.startsWith(PackNaming.PACKAGE_PREFIX + ".") -> R.string.install_not_a_pack
        expectedPackage != null && packageName != expectedPackage -> R.string.install_not_a_pack
        trusted == null || signers.none { it.contentEquals(trusted) } -> R.string.install_wrong_signer
        else -> null
    }
}

/** Receives [PackageInstaller] results for [PackInstaller]. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.appContainer.packInstaller.onStatus(intent)
    }
}
