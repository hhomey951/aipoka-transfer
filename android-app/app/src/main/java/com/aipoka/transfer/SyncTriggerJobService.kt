package com.aipoka.transfer

import android.app.job.JobParameters
import android.app.job.JobService

class SyncTriggerJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Scheduler.triggerNow(applicationContext)
        Scheduler.start(applicationContext)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
