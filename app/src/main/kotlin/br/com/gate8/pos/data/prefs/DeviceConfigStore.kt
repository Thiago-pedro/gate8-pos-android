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

    /** Nome do estabelecimento configurado pelo produtor (vem de /login e /catalog). */
    fun getMerchantName(): String? =
        prefs.getString(KEY_MERCHANT_NAME, null)?.takeIf { it.isNotBlank() }

    fun setMerchantName(name: String?) {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_MERCHANT_NAME).apply()
        } else {
            prefs.edit().putString(KEY_MERCHANT_NAME, trimmed).apply()
        }
    }

    /**
     * Nome do estabelecimento para exibir/imprimir: usa o `merchant_name` quando
     * configurado e cai para o `producer_name` como fallback final.
     */
    fun getEstablishmentName(): String? = getMerchantName() ?: getProducerName()

    fun getDeviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun setDeviceId(id: String) {
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
    }

    fun getOperatorName(): String = prefs.getString(KEY_OPERATOR, "")?.trim().orEmpty()

    /** Há um operador definido neste terminal? Usado para exigir o nome antes de operar. */
    fun hasOperatorName(): Boolean = getOperatorName().isNotBlank()

    fun setOperatorName(name: String) {
        prefs.edit().putString(KEY_OPERATOR, name).apply()
    }

    fun getStoneCode(): String? = prefs.getString(KEY_STONE_CODE, null)

    fun setStoneCode(code: String) {
        prefs.edit().putString(KEY_STONE_CODE, code.trim()).apply()
    }

    /**
     * Modo ficha na conveniência: quando ligado, cada unidade de item sai em uma ficha
     * separada (ex.: 2 copões = 2 fichas). Desligado (padrão), tudo sai em um recibo só.
     */
    fun isConvenienceTicketMode(): Boolean = prefs.getBoolean(KEY_CONVENIENCE_TICKET_MODE, false)

    fun setConvenienceTicketMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONVENIENCE_TICKET_MODE, enabled).apply()
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
            .remove(KEY_MERCHANT_NAME)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_OPERATOR)
            // Zera a identidade da maquininha: o próximo login gera um novo
            // fingerprint e o dispositivo precisa ser liberado de novo no painel.
            .remove(KEY_FINGERPRINT)
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_FINGERPRINT = "device_fingerprint"
        private const val KEY_PRODUCER_TOKEN = "producer_token"
        private const val KEY_PRODUCER_NAME = "producer_name"
        private const val KEY_MERCHANT_NAME = "pos_merchant_name"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_OPERATOR = "operator_name"
        private const val KEY_STONE_CODE = "stone_code"
        private const val KEY_SHORT_ID = "device_short_id"
        private const val KEY_CONVENIENCE_TICKET_MODE = "convenience_ticket_mode"
    }
}
