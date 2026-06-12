package br.com.gate8.pos.data.prefs

import android.content.SharedPreferences
import br.com.gate8.pos.domain.model.LastSaleRecord
import kotlinx.serialization.json.Json

class LastSaleStore(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    fun save(record: LastSaleRecord) {
        prefs.edit()
            .putString(KEY_LAST_SALE, json.encodeToString(LastSaleRecord.serializer(), record))
            .apply()
    }

    fun get(): LastSaleRecord? {
        val raw = prefs.getString(KEY_LAST_SALE, null) ?: return null
        return runCatching {
            json.decodeFromString(LastSaleRecord.serializer(), raw)
        }.getOrNull()
    }

    fun markVoided() {
        val current = get() ?: return
        save(current.copy(voided = true))
    }

    fun clear() {
        prefs.edit().remove(KEY_LAST_SALE).apply()
    }

    companion object {
        private const val KEY_LAST_SALE = "last_sale_record"
    }
}
