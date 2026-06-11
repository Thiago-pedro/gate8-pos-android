package br.com.gate8.pos.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.gate8.pos.BuildConfig

class DeviceConfigStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gate8_pos_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun ensureDefaultBaseUrl() {
        if (getBaseUrl().isNullOrBlank()) {
            setBaseUrl(BuildConfig.DEFAULT_BASE_URL)
        }
    }

    fun isLoggedIn(): Boolean = !getDeviceToken().isNullOrBlank()

    @Deprecated("Use isLoggedIn()", ReplaceWith("isLoggedIn()"))
    fun isConfigured(): Boolean = isLoggedIn()

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun getDeviceToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun setDeviceToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getFingerprint(): String? = prefs.getString(KEY_FINGERPRINT, null)

    fun setFingerprint(fingerprint: String) {
        prefs.edit().putString(KEY_FINGERPRINT, fingerprint).apply()
    }

    fun getProducerToken(): String? = prefs.getString(KEY_PRODUCER_TOKEN, null)

    fun setProducerToken(token: String) {
        prefs.edit().putString(KEY_PRODUCER_TOKEN, token.uppercase()).apply()
    }

    fun getProducerName(): String? = prefs.getString(KEY_PRODUCER_NAME, null)

    fun setProducerName(name: String) {
        prefs.edit().putString(KEY_PRODUCER_NAME, name).apply()
    }

    fun getDeviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun setDeviceId(id: String) {
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    }

    fun getOperatorName(): String =
        prefs.getString(KEY_OPERATOR, "Operador POS") ?: "Operador POS"

    fun setOperatorName(name: String) {
        prefs.edit().putString(KEY_OPERATOR, name).apply()
    }

    fun getDeviceShortId(): String {
        val fromName = getDeviceName()?.takeIf { it.isNotBlank() }
        if (fromName != null) return fromName.take(12)
        return prefs.getString(KEY_SHORT_ID, "POS01") ?: "POS01"
    }

    fun setDeviceShortId(id: String) {
        prefs.edit().putString(KEY_SHORT_ID, id).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DEVICE_ID)
            .apply()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_PRODUCER_NAME)
            .remove(KEY_DEVICE_NAME)
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_FINGERPRINT = "device_fingerprint"
        private const val KEY_PRODUCER_TOKEN = "producer_token"
        private const val KEY_PRODUCER_NAME = "producer_name"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_OPERATOR = "operator_name"
        private const val KEY_SHORT_ID = "device_short_id"
    }
}
