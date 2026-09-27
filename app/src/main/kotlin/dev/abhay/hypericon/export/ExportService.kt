package dev.abhay.hypericon.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.abhay.hypericon.MainActivity
import dev.abhay.hypericon.R
import dev.abhay.hypericon.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps HyperIcon running while an export is built (the work itself runs in [ExportRunner]), with
 * a persistent progress notification and a Cancel action. When the export ends it posts a
 * "saved" (or "failed") notification and stops.
 */
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runner = appContainer.exportRunner
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        // Must be called promptly after startForegroundService, even if the export already ended.
        startForeground(PROGRESS_ID, progress(runner.state.value as? ExportState.Running), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (watcher == null) {
            watcher = scope.launch {
                runner.state.collect { state ->
                    val manager = getSystemService(NotificationManager::class.java)
                    when (state) {
                        is ExportState.Running -> manager.notify(PROGRESS_ID, progress(state))
                        is ExportState.Done -> finish(result(state))
                        is ExportState.Failed -> finish(failed(state))
                        ExportState.Idle -> finish(null)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish(notification: Notification?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (notification != null) getSystemService(NotificationManager::class.java).notify(RESULT_ID, notification)
        stopSelf()
    }

    private fun progress(state: ExportState.Running?): Notification {
        val pack = state?.kind == ExportKind.ICON_PACK
        val title = when {
            pack -> "Building icon pack"
            state != null && state.files > 1 -> "Exporting theme ${state.file} of ${state.files}"
            else -> "Exporting theme"
        }
        val text = when {
            state == null -> "Starting…"
            state.signing -> "Building and signing the APK…"
            else -> "Rendering ${state.done} of ${state.total} icons"
        }
        return base(title, text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(state?.total ?: 0, state?.done ?: 0, state == null || state.signing)
            .addAction(0, "Cancel", PendingIntent.getService(this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun result(state: ExportState.Done): Notification {
        val first = state.files.first()
        val title = when {
            first.kind == ExportKind.ICON_PACK -> "Icon pack saved"
            state.files.size > 1 -> "Themes saved"
            else -> "Theme saved"
        }
        val text = if (first.kind == ExportKind.ICON_PACK) "${first.location} · open it in Files to install" else state.files.joinToString { it.location }
        return base(title, text).setAutoCancel(true).setStyle(NotificationCompat.BigTextStyle().bigText(text)).build()
    }

    private fun failed(state: ExportState.Failed): Notification =
        base(if (state.kind == ExportKind.ICON_PACK) "Icon pack failed" else "Export failed", state.message).setAutoCancel(true).build()

    private fun base(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

    companion object {
        private const val CHANNEL = "exports"
        private const val PROGRESS_ID = 1
        private const val RESULT_ID = 2
        private const val ACTION_CANCEL = "dev.abhay.hypericon.export.CANCEL"

        /** Starts the service; call when an export starts (the app is in the foreground then). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Exports", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progress and results of theme and icon pack exports"
                    },
                )
            }
        }
    }
}
