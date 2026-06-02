package br.com.gate8.pos.data.repository

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.local.dao.PendingSaleDao
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ApiErrorDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.domain.model.SaleSuccess
import kotlinx.serialization.json.Json

class SaleRepository(
    private val api: PosApiService,
    private val pendingSaleDao: PendingSaleDao,
    private val json: Json,
) {
    suspend fun enqueuePending(entity: PendingSaleEntity) {
        pendingSaleDao.upsert(entity)
    }

    suspend fun listPending(): List<PendingSaleEntity> =
        pendingSaleDao.listAll()

    suspend fun submitSale(request: CreateSaleRequestDto): SaleSuccess {
        val response = api.createSale(request)
        when (response.code()) {
            200, 201 -> {
                val body = response.body()
                    ?: throw ApiException(response.code(), "Resposta vazia do servidor")
                val saleId = body.saleId
                    ?: throw ApiException(response.code(), "sale_id ausente na resposta")
                val codes = body.tickets.flatMap { g -> g.tickets.map { it.code } }
                pendingSaleDao.upsert(
                    PendingSaleEntity(
                        clientReference = request.clientReference,
                        payloadJson = json.encodeToString(CreateSaleRequestDto.serializer(), request),
                        status = PendingSaleStatus.SYNCED,
                        saleId = saleId,
                        createdAt = System.currentTimeMillis(),
                        lastAttemptAt = System.currentTimeMillis(),
                    ),
                )
                return SaleSuccess(saleId = saleId, duplicated = body.duplicated, ticketCodes = codes)
            }
            400, 409 -> throw parseErrorBody(response.code(), response.errorBody()?.string())
            401 -> throw ApiException(401, "Token inválido — verifique g8pos_ no admin")
            403 -> throw ApiException(403, "Dispositivo inativo")
            else -> throw ApiException(response.code(), "Erro ao registrar venda")
        }
    }

    suspend fun syncPending(entity: PendingSaleEntity): SaleSuccess {
        val request = json.decodeFromString(CreateSaleRequestDto.serializer(), entity.payloadJson)
        return submitSale(request)
    }

    private fun parseErrorBody(code: Int, raw: String?): ApiException {
        val parsed = raw?.let {
            runCatching { json.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull()
        }
        return ApiException(
            httpCode = code,
            message = parsed?.error ?: "Erro na venda",
            errorCode = parsed?.error,
            available = parsed?.available,
        )
    }
}
