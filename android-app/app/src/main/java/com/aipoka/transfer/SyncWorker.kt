package com.aipoka.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CHANNEL_ID = "aipoka_sync"
private const val NOTIFICATION_ID = 1001

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val client by lazy { Uploader.buildClient(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverUrl = Prefs.getServerUrl(applicationContext) ?: return@withContext Result.failure()
        val token = Prefs.getToken(applicationContext) ?: return@withContext Result.failure()

        // User pressed "Stop Sync": background runs (periodic/content-trigger) stay
        // no-ops until Sync Now or a sync-folder change clears the pause.
        if (Prefs.isSyncPaused(applicationContext)) return@withContext Result.success()

        try {
            setForeground(makeForegroundInfo())
        } catch (e: Exception) {
            // Foreground promotion can be refused by the OS (e.g. background execution
            // limits) — sync can still proceed without it, just without the notification.
        }

        val lastSync = Prefs.getLastSync(applicationContext)
        var newestSeen = lastSync
        var uploadedCount = 0

        val photos = try {
            queryNewPhotos(lastSync)
        } catch (e: SecurityException) {
            return@withContext Result.failure()
        }

        for (photo in photos) {
            // Cooperative cancel: uploads are blocking OkHttp calls, so check between
            // photos. Progress below still persists — the watermark keeps what's done.
            if (isStopped) break
            val ok = uploadPhoto(serverUrl, token, photo)
            if (ok) {
                uploadedCount++
                Prefs.addUploadHistory(applicationContext, photo.displayName, System.currentTimeMillis())
                if (photo.dateAddedMillis > newestSeen) newestSeen = photo.dateAddedMillis
            }
        }

        if (newestSeen > lastSync) {
            Prefs.setLastSync(applicationContext, newestSeen)
        }

        Result.success()
    }

    /** Lower bound in epoch millis for the given sync type, recomputed every run
     * so TODAY/LAST_30_DAYS windows roll forward on their own without needing a
     * watermark reset. ALL has no floor. */
    private fun syncTypeFloorMillis(type: Prefs.SyncType): Long {
        val now = System.currentTimeMillis()
        return when (type) {
            Prefs.SyncType.ALL -> 0L
            Prefs.SyncType.LAST_30_DAYS -> now - 30L * 24 * 60 * 60 * 1000
            Prefs.SyncType.TODAY -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }

    private data class PhotoEntry(val uri: Uri, val displayName: String, val dateAddedMillis: Long, val size: Long)

    private fun queryNewPhotos(sinceMillis: Long): List<PhotoEntry> {
        val results = mutableListOf<PhotoEntry>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val syncFolder = Prefs.getSyncFolder(applicationContext)
        val effectiveSince = maxOf(sinceMillis, syncTypeFloorMillis(Prefs.getSyncType(applicationContext)))
        val selection: String
        val selectionArgs: Array<String>
        if (syncFolder != null) {
            selection = "${MediaStore.Images.Media.DATE_ADDED} > ? AND ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            selectionArgs = arrayOf((effectiveSince / 1000).toString(), syncFolder)
        } else {
            selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            selectionArgs = arrayOf((effectiveSince / 1000).toString())
        }
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        applicationContext.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                results.add(
                    PhotoEntry(
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "photo_$id.jpg",
                        dateAddedMillis = cursor.getLong(dateCol) * 1000,
                        size = cursor.getLong(sizeCol)
                    )
                )
            }
        }
        return results
    }

    private fun uploadPhoto(serverUrl: String, token: String, photo: PhotoEntry): Boolean {
        return try {
            applicationContext.contentResolver.openInputStream(photo.uri)?.use { input ->
                val tempFile = File.createTempFile("aipoka_", "_${photo.displayName}", applicationContext.cacheDir)
                tempFile.outputStream().use { out -> input.copyTo(out) }
                val success = Uploader.uploadFile(client, serverUrl, token, tempFile, photo.displayName)
                tempFile.delete()
                success
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun makeForegroundInfo(): ForegroundInfo {
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
            .setContentText("Sending photos to PC…")
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
