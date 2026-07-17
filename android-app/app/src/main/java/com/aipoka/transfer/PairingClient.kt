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
 * Shared pairing POST logic, used by both the "Enter code" and "Scan QR" tabs on the
 * Pairing screen (and the "Pair another PC" sheet) — same request body / response
 * handling as the original PairingActivity, just extracted so multiple Compose entry
 * points can call it. Not thread-confined: [onResult] fires on OkHttp's callback
 * thread, callers must hop back to the main thread themselves before touching UI state.
 */
object PairingClient {

    data class PairResult(
        val success: Boolean,
        val message: String,
        val serverUrl: String? = null,
        val token: String? = null,
        val deviceName: String? = null,
        val certFingerprint: String? = null
    )

    /** Adds an https:// scheme if missing, upgrades a typed http:// — the PC server is HTTPS-only. */
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> trimmed
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> "https://$trimmed"
        }
    }

    fun pair(serverUrlRaw: String, pin: String, onResult: (PairResult) -> Unit) {
        val serverUrl = normalizeUrl(serverUrlRaw)
        val cleanPin = pin.trim()

        if (serverUrl.isEmpty() || cleanPin.isEmpty()) {
            onResult(PairResult(false, "Enter server address and PIN."))
            return
        }

        var capturedFingerprint: String? = null
        val (socketFactory, trustManager) = TrustedCert.capturingSocketFactory { fp -> capturedFingerprint = fp }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(socketFactory, trustManager)
            .hostnameVerifier(TrustedCert.NO_OP_HOSTNAME_VERIFIER)
            .build()

        val deviceName = "android-${Build.MODEL}"
        val json = JSONObject().apply {
            put("pin", cleanPin)
            put("deviceName", deviceName)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$serverUrl/pair").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(PairResult(false, "Could not reach server: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = try {
                        JSONObject(text).optString("error", "Pairing failed")
                    } catch (e: Exception) {
                        "Pairing failed (${response.code})"
                    }
                    onResult(PairResult(false, error))
                    return
                }
                val token = try {
                    JSONObject(text).getString("token")
                } catch (e: Exception) {
                    null
                }
                if (token == null) {
                    onResult(PairResult(false, "Unexpected server response."))
                    return
                }
                onResult(PairResult(true, "Paired!", serverUrl, token, deviceName, capturedFingerprint))
            }
        })
    }
}
