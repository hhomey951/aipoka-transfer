package com.aipoka.transfer

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared upload client used by both the auto-sync worker and manual folder sends. */
object Uploader {

    fun buildClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        val fingerprint = Prefs.getCertFingerprint(context)
        if (fingerprint != null) {
            val (socketFactory, trustManager) = TrustedCert.pinnedSocketFactory(fingerprint)
            builder.sslSocketFactory(socketFactory, trustManager)
            builder.hostnameVerifier(TrustedCert.NO_OP_HOSTNAME_VERIFIER)
        }
        // No stored fingerprint (pairing predates cert pinning) falls through to default
        // CA validation, which will reject the self-signed cert — upload fails until re-pair.
        return builder.build()
    }

    /**
     * Uploads one file. [targetFolder] non-null routes the photo into
     * <base>/<targetFolder> on the PC instead of the dated folder.
     */
    // The PC server only accepts concrete image MIME types (its fileFilter rejects
    // a literal "image/*"), so derive the real type from the file extension.
    private fun mimeTypeFor(displayName: String): String {
        return when (displayName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    fun uploadFile(
        client: OkHttpClient,
        serverUrl: String,
        token: String,
        file: File,
        displayName: String,
        targetFolder: String? = null
    ): Boolean {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", displayName, file.asRequestBody(mimeTypeFor(displayName).toMediaTypeOrNull()))
                .build()

            val requestBuilder = Request.Builder()
                .url("$serverUrl/upload")
                .header("X-Device-Token", token)
                .header("X-File-Size", file.length().toString())
                .post(body)

            if (!targetFolder.isNullOrBlank()) {
                // Percent-encoded: HTTP header values must be ASCII, folder names may not be.
                requestBuilder.header("X-Target-Folder", android.net.Uri.encode(targetFolder))
            }

            client.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
