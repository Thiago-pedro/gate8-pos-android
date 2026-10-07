package br.com.gate8.pos.data.prefs

import android.content.SharedPreferences
import br.com.gate8.pos.payment.SplitSessionRecord
import kotlinx.serialization.json.Json

/** Guarda o pagamento dividido ainda não concluído, para não cobrar a mesma parte de novo. */
class SplitPaymentStore(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    fun load(scope: String): SplitSessionRecord? {
        val raw = prefs.getString(key(scope), null) ?: return null
        return runCatching { json.decodeFromString(SplitSessionRecord.serializer(), raw) }
            .getOrNull()
    }

    fun save(session: SplitSessionRecord) {
        prefs.edit()
            .putString(key(session.scope), json.encodeToString(SplitSessionRecord.serializer(), session))
            .commit()
    }

    fun clear(scope: String) {
        prefs.edit().remove(key(scope)).commit()
    }

    private fun key(scope: String) = "split_$scope"
}
