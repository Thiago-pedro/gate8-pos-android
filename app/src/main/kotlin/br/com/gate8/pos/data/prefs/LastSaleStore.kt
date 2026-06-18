package br.com.gate8.pos.data.prefs

import android.content.SharedPreferences
import br.com.gate8.pos.domain.model.LastSaleRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Guarda as últimas vendas do terminal (não só a última) para permitir estorno
 * de qualquer uma da lista. Mais recente primeiro.
 */
class LastSaleStore(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    private val listSerializer = ListSerializer(LastSaleRecord.serializer())

    /** Adiciona/atualiza uma venda no topo da lista (dedup por client_reference). */
    fun save(record: LastSaleRecord) {
        val rest = list().filterNot { it.clientReference == record.clientReference }
        persist((listOf(record) + rest).take(MAX_RECENT))
    }

    /** Última venda (mais recente) — usada na reimpressão. */
    fun get(): LastSaleRecord? = list().firstOrNull()

    /** Todas as vendas recentes, mais recente primeiro. */
    fun list(): List<LastSaleRecord> {
        prefs.getString(KEY_RECENT_SALES, null)?.let { raw ->
            return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
        }
        // Migração do formato antigo (uma única venda).
        val legacy = prefs.getString(KEY_LAST_SALE, null) ?: return emptyList()
        return runCatching { json.decodeFromString(LastSaleRecord.serializer(), legacy) }
            .getOrNull()
            ?.let { listOf(it) }
            ?: emptyList()
    }

    fun find(clientReference: String): LastSaleRecord? =
        list().firstOrNull { it.clientReference == clientReference }

    /** Vincula o sale_id do backend a uma venda já registrada (após sync). */
    fun updateSaleId(clientReference: String, saleId: String) {
        val current = list()
        if (current.none { it.clientReference == clientReference && it.saleId.isNullOrBlank() }) return
        persist(
            current.map {
                if (it.clientReference == clientReference && it.saleId.isNullOrBlank()) {
                    it.copy(saleId = saleId)
                } else {
                    it
                }
            },
        )
    }

    fun markVoided(clientReference: String) {
        persist(
            list().map {
                if (it.clientReference == clientReference) it.copy(voided = true) else it
            },
        )
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENT_SALES).remove(KEY_LAST_SALE).apply()
    }

    private fun persist(records: List<LastSaleRecord>) {
        prefs.edit()
            .putString(KEY_RECENT_SALES, json.encodeToString(listSerializer, records))
            .remove(KEY_LAST_SALE)
            .apply()
    }

    companion object {
        private const val KEY_LAST_SALE = "last_sale_record"
        private const val KEY_RECENT_SALES = "recent_sales"
        private const val MAX_RECENT = 50
    }
}
