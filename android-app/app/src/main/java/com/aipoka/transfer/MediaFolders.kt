package com.aipoka.transfer

import android.content.Context
import android.provider.MediaStore

object MediaFolders {

    /** Distinct photo album/folder names on the device, alphabetically sorted. */
    fun list(context: Context): List<String> {
        val names = sortedSetOf<String>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(col)?.let { names.add(it) }
            }
        }
        return names.toList()
    }
}
