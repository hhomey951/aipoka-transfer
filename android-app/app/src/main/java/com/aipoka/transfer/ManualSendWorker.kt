package com.aipoka.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CHANNEL_ID = "aipoka_sync"
private const val NOTIFICATION_ID = 1002

/**
 * Uploads photos the user hand-picked from the gallery into a named folder on
 * the PC. Input: temp-file paths (already copied to cacheDir by MainActivity),
 * matching display names, and the target folder name.
 */
class ManualSendWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_PATHS = "file_paths"
        const val KEY_DISPLAY_NAMES = "display_names"
        const val KEY_TARGET_FOLDER = "target_folder"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverUrl = Prefs.getServerUrl(applicationContext) ?: return@withContext Result.failure()
        val token = Prefs.getToken(applicationContext) ?: return@withContext Result.failure()

        val paths = inputData.getStringArray(KEY_FILE_PATHS) ?: return@withContext Result.failure()
        val names = inputData.getStringArray(KEY_DISPLAY_NAMES) ?: return@withContext Result.failure()
        val folder = inputData.getString(KEY_TARGET_FOLDER) ?: return@withContext Result.failure()

        try {
            setForeground(makeForegroundInfo(folder))
        } catch (e: Exception) {
            // Foreground promotion can be refused by the OS — proceed without notification.
        }

        val client = Uploader.buildClient(applicationContext)
        var allOk = true

        paths.forEachIndexed { i, path ->
            val file = File(path)
            // Temp files are deleted after a successful upload, so a missing
            // file on a retry attempt means it already went through — skip it.
            if (!file.exists()) return@forEachIndexed
            val displayName = names.getOrNull(i) ?: file.name
            val ok = Uploader.uploadFile(client, serverUrl, token, file, displayName, folder)
            if (ok) {
                Prefs.addUploadHistory(applicationContext, "$folder/$displayName", System.currentTimeMillis())
                file.delete()
            } else {
                allOk = false
            }
        }

        if (allOk) Result.success()
        else if (runAttemptCount < 3) Result.retry()
        else {
            paths.forEach { File(it).delete() }
            Result.failure()
        }
    }

    private fun makeForegroundInfo(folder: String): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Photo sync", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Aipoka Transfer")
            .setContentText("Sending photos to \"$folder\"…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
