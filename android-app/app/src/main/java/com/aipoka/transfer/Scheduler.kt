package com.aipoka.transfer

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.work.Constraints
import androidx.work.WorkInfo
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "aipoka_periodic_sync"
private const val ONE_TIME_WORK_NAME = "aipoka_triggered_sync"
private const val CONTENT_JOB_ID = 5001

object Scheduler {

    fun start(context: Context) {
        schedulePeriodicSync(context)
        scheduleContentTriggerJob(context)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(CONTENT_JOB_ID)
    }

    /**
     * [fromUser] true = explicit "Sync Now" press, which also clears a pause.
     * Background triggers (content observer) leave the pause in place and are
     * dropped while paused.
     */
    fun triggerNow(context: Context, fromUser: Boolean = false) {
        if (fromUser) Prefs.setSyncPaused(context, false)
        else if (Prefs.isSyncPaused(context)) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Cancels any sync currently running (or queued), without unpairing.
     * The periodic schedule is re-armed so background sync resumes on its
     * normal cadence; the watermark is untouched, so nothing is lost.
     */
    fun stopSync(context: Context) {
        Prefs.setSyncPaused(context, true)
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        // A freshly enqueued periodic job runs immediately once constraints are met,
        // which would undo the stop — delay the first run by one full period.
        schedulePeriodicSync(context, delayFirstRun = true)
    }

    /** True while a SyncWorker instance (manual or periodic) is actively running. */
    fun syncRunning(context: Context): LiveData<Boolean> {
        val workManager = WorkManager.getInstance(context)
        val result = MediatorLiveData<Boolean>()
        var oneTimeRunning = false
        var periodicRunning = false
        result.addSource(workManager.getWorkInfosForUniqueWorkLiveData(ONE_TIME_WORK_NAME)) { infos ->
            oneTimeRunning = infos.any { it.state == WorkInfo.State.RUNNING }
            result.value = oneTimeRunning || periodicRunning
        }
        result.addSource(workManager.getWorkInfosForUniqueWorkLiveData(PERIODIC_WORK_NAME)) { infos ->
            periodicRunning = infos.any { it.state == WorkInfo.State.RUNNING }
            result.value = oneTimeRunning || periodicRunning
        }
        return result
    }

    private fun schedulePeriodicSync(context: Context, delayFirstRun: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .apply { if (delayFirstRun) setInitialDelay(15, TimeUnit.MINUTES) }
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleContentTriggerJob(context: Context) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val componentName = ComponentName(context, SyncTriggerJobService::class.java)

        val jobInfo = JobInfo.Builder(CONTENT_JOB_ID, componentName)
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            .setTriggerContentMaxDelay(0)
            .build()

        jobScheduler.schedule(jobInfo)
    }
}
