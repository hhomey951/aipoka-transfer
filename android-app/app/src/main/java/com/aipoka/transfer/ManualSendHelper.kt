package com.aipoka.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

/**
 * Business logic behind the "Select photos & send" feature, extracted unchanged from
 * the old MainActivity so the new Compose UI (and the Sync Mode sheet's "Selected
 * photos" shortcut, which reuses this same one-shot flow) can call into it.
 */
object ManualSendHelper {

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    /** Copies each picked URI to a temp file (blocking I/O — call from Dispatchers.IO). */
    fun copyToTempFiles(context: Context, uris: List<Uri>): Pair<List<String>, List<String>> {
        val paths = mutableListOf<String>()
        val names = mutableListOf<String>()
        uris.forEach { uri ->
            try {
                val displayName = queryDisplayName(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    val temp = File.createTempFile("aipoka_manual_", null, context.cacheDir)
                    temp.outputStream().use { out -> inp.copyTo(out) }
                    paths.add(temp.absolutePath)
                    names.add(displayName)
                }
            } catch (e: Exception) {
                // Skip unreadable entries; the rest still go through.
            }
        }
        return paths to names
    }

    fun enqueueSend(context: Context, paths: List<String>, names: List<String>, folderName: String) {
        val request = OneTimeWorkRequestBuilder<ManualSendWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInputData(
                Data.Builder()
                    .putStringArray(ManualSendWorker.KEY_FILE_PATHS, paths.toTypedArray())
                    .putStringArray(ManualSendWorker.KEY_DISPLAY_NAMES, names.toTypedArray())
                    .putString(ManualSendWorker.KEY_TARGET_FOLDER, folderName)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
