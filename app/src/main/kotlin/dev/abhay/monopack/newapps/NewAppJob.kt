package dev.abhay.monopack.newapps

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.abhay.monopack.appContainer
import dev.abhay.monopack.util.catching
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs [NewAppCheck] a few times a day while the setting is on. A periodic job, because Android
 * no longer tells a closed app about new installs; it survives reboots (persisted).
 */
class NewAppJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            catching { applicationContext.appContainer.newAppCheck.run() }.onFailure { Log.w("Monopack", "New-app check failed", it) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val JOB_ID = 100
        private val INTERVAL = TimeUnit.HOURS.toMillis(6)

        /** Schedules the periodic check (kept if already scheduled), or cancels it. */
        fun sync(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, NewAppJob::class.java))
                    .setPeriodic(INTERVAL, TimeUnit.HOURS.toMillis(1))
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
