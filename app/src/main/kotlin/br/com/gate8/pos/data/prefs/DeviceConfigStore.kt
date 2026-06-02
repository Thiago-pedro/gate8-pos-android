package br.com.gate8.pos.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DeviceConfigStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gate8_pos_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isConfigured(): Boolean =
        !getBaseUrl().isNullOrBlank() && !getDeviceToken().isNullOrBlank()

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun getDeviceToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun setDeviceToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getOperatorName(): String =
        prefs.getString(KEY_OPERATOR, "Operador POS") ?: "Operador POS"

    fun setOperatorName(name: String) {
        prefs.edit().putString(KEY_OPERATOR, name).apply()
    }

    fun getDeviceShortId(): String =
        prefs.getString(KEY_SHORT_ID, "POS01") ?: "POS01"

    fun setDeviceShortId(id: String) {
        prefs.edit().putString(KEY_SHORT_ID, id).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_OPERATOR = "operator_name"
        private const val KEY_SHORT_ID = "device_short_id"
    }
}
