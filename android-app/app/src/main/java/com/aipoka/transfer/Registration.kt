package com.aipoka.transfer

import android.os.Build
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Fires the install-tracking POST /register call once we know the PC's server URL
 * (only available after a successful /pair). Deliberately best-effort: this is just
 * "who installed the app" bookkeeping on the PC side, not core functionality, so a
 * failure here must never surface an error or block the pairing success flow.
 */
object Registration {

    fun registerBestEffort(
        client: OkHttpClient,
        serverUrl: String,
        name: String?,
        email: String?
    ) {
        if (name.isNullOrBlank() && email.isNullOrBlank()) return
        try {
            val json = JSONObject().apply {
                put("name", name.orEmpty())
                put("email", email.orEmpty())
                put("deviceName", "android-${Build.MODEL}")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$serverUrl/register").post(body).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Ignored — best-effort install tracking only.
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            // Ignored — never let this break the pairing success flow.
        }
    }
}
