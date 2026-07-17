package com.aipoka.transfer

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE_NAME = "aipoka_secure_prefs"

    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_CERT_FINGERPRINT = "cert_fingerprint"
    private const val KEY_LAST_SYNC = "last_sync_millis"
    private const val KEY_SYNC_FOLDER = "sync_folder"
    private const val KEY_SYNC_TYPE = "sync_type"
    private const val KEY_SYNC_PAUSED = "sync_paused"
    private const val KEY_UPLOAD_HISTORY = "upload_history"
    private const val MAX_HISTORY = 100

    private const val KEY_PROFILE_NAME = "profile_name"
    private const val KEY_PROFILE_EMAIL = "profile_email"
    // Absent = follow system dark mode; present = explicit user override from the
    // Settings > Appearance sheet.
    private const val KEY_APPEARANCE_DARK_OVERRIDE = "appearance_dark_override"

    data class UploadEntry(val displayName: String, val uploadedAtMillis: Long)

    enum class SyncType { ALL, LAST_30_DAYS, TODAY }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPaired(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString(KEY_SERVER_URL, null).isNullOrEmpty() &&
            !p.getString(KEY_TOKEN, null).isNullOrEmpty()
    }

    fun savePairing(context: Context, serverUrl: String, token: String, deviceName: String, certFingerprint: String?) {
        prefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEVICE_NAME, deviceName)
            .apply {
                if (certFingerprint != null) putString(KEY_CERT_FINGERPRINT, certFingerprint)
                else remove(KEY_CERT_FINGERPRINT)
            }
            .apply()
    }

    fun clearPairing(context: Context) {
        prefs(context).edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_TOKEN)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_CERT_FINGERPRINT)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_SYNC_PAUSED)
            .remove(KEY_UPLOAD_HISTORY)
            .apply()
    }

    fun getServerUrl(context: Context): String? = prefs(context).getString(KEY_SERVER_URL, null)
    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getDeviceName(context: Context): String? = prefs(context).getString(KEY_DEVICE_NAME, null)
    fun getCertFingerprint(context: Context): String? = prefs(context).getString(KEY_CERT_FINGERPRINT, null)

    fun getLastSync(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0L)
    fun setLastSync(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, millis).apply()
    }

    /**
     * Set when the user hits "Stop Sync": background triggers (periodic job,
     * content observer) must not restart uploading until the user resumes by
     * pressing Sync Now or picking a new sync folder.
     */
    fun isSyncPaused(context: Context): Boolean = prefs(context).getBoolean(KEY_SYNC_PAUSED, false)
    fun setSyncPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_PAUSED, paused).apply()
    }

    /** null means "all folders" (no filter). */
    fun getSyncFolder(context: Context): String? = prefs(context).getString(KEY_SYNC_FOLDER, null)

    fun setSyncFolder(context: Context, folderName: String?) {
        val editor = prefs(context).edit()
        if (folderName == null) editor.remove(KEY_SYNC_FOLDER) else editor.putString(KEY_SYNC_FOLDER, folderName)
        editor.apply()
    }

    /** Default ALL (no time-range filter). */
    fun getSyncType(context: Context): SyncType {
        val raw = prefs(context).getString(KEY_SYNC_TYPE, null) ?: return SyncType.ALL
        return try { SyncType.valueOf(raw) } catch (e: IllegalArgumentException) { SyncType.ALL }
    }

    fun setSyncType(context: Context, type: SyncType) {
        prefs(context).edit().putString(KEY_SYNC_TYPE, type.name).apply()
    }

    fun addUploadHistory(context: Context, displayName: String, uploadedAtMillis: Long) {
        val current = getUploadHistory(context).toMutableList()
        current.add(0, UploadEntry(displayName, uploadedAtMillis))
        val trimmed = current.take(MAX_HISTORY)
        val array = JSONArray()
        trimmed.forEach { entry ->
            array.put(JSONObject().apply {
                put("name", entry.displayName)
                put("at", entry.uploadedAtMillis)
            })
        }
        prefs(context).edit().putString(KEY_UPLOAD_HISTORY, array.toString()).apply()
    }

    fun getUploadHistory(context: Context): List<UploadEntry> {
        val raw = prefs(context).getString(KEY_UPLOAD_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                UploadEntry(obj.getString("name"), obj.getLong("at"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Local-only profile captured at Login. Not authentication — just a display name
     * and a value sent once to the best-effort /register endpoint after pairing. */
    fun setProfile(context: Context, name: String, email: String) {
        prefs(context).edit()
            .putString(KEY_PROFILE_NAME, name)
            .putString(KEY_PROFILE_EMAIL, email)
            .apply()
    }

    fun getProfileName(context: Context): String? = prefs(context).getString(KEY_PROFILE_NAME, null)
    fun getProfileEmail(context: Context): String? = prefs(context).getString(KEY_PROFILE_EMAIL, null)

    /** null = follow system dark mode; true/false = explicit user override. */
    fun getAppearanceOverride(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.contains(KEY_APPEARANCE_DARK_OVERRIDE)) return null
        return p.getBoolean(KEY_APPEARANCE_DARK_OVERRIDE, false)
    }

    fun setAppearanceOverride(context: Context, dark: Boolean?) {
        val editor = prefs(context).edit()
        if (dark == null) editor.remove(KEY_APPEARANCE_DARK_OVERRIDE) else editor.putBoolean(KEY_APPEARANCE_DARK_OVERRIDE, dark)
        editor.apply()
    }
}
