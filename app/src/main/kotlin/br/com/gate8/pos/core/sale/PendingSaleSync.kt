package br.com.gate8.pos.core.sale

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.repository.SaleRepository

data class PendingSyncResult(
    val synced: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
)

class PendingSaleSync(
    private val saleRepository: SaleRepository,
    private val saleAdmin: SaleAdminService,
) {
    suspend fun syncAll(): PendingSyncResult {
        val pending = saleRepository.listPending().filter {
            it.status == PendingSaleStatus.PENDING_SYNC
        }
        var synced = 0
        var failed = 0
        var lastError: String? = null
        for (entity in pending) {
            runCatching { saleRepository.syncPending(entity) }
                .onSuccess { success ->
                    synced++
                    saleAdmin.attachSaleIdIfMatches(entity.clientReference, success.saleId)
                }
                .onFailure { e ->
                    failed++
                    lastError = when (e) {
                        is ApiException -> e.errorCode ?: e.message
                        else -> e.message
                    }
                    saleRepository.markSyncFailed(entity.clientReference, lastError)
                }
        }
        return PendingSyncResult(synced = synced, failed = failed, lastError = lastError)
    }
}
