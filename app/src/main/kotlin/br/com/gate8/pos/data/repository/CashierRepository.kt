package br.com.gate8.pos.data.repository

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.CashierCloseRequestDto
import br.com.gate8.pos.data.remote.dto.CashierMovementRequestDto
import br.com.gate8.pos.data.remote.dto.CashierOpenRequestDto
import br.com.gate8.pos.data.remote.dto.CashierStatusDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class CashierRepository(
    private val api: PosApiService,
    private val json: Json,
) {
    suspend fun fetchStatus(): CashierStatusDto = parseResponse(api.getCashierStatus())

    suspend fun open(openingBalance: Double, operatorName: String, notes: String? = null): CashierStatusDto =
        parseResponse(
            api.openCashier(
                CashierOpenRequestDto(
                    openingBalance = openingBalance,
                    operatorName = operatorName,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                ),
            ),
        )

    suspend fun close(countedBalance: Double, notes: String? = null): CashierStatusDto =
        parseResponse(
            api.closeCashier(
                CashierCloseRequestDto(
                    countedBalance = countedBalance,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                ),
            ),
        )

    suspend fun withdrawal(amount: Double, description: String): CashierStatusDto =
        parseResponse(api.cashierWithdrawal(CashierMovementRequestDto(amount, description.trim())))

    suspend fun expense(amount: Double, description: String): CashierStatusDto =
        parseResponse(api.cashierExpense(CashierMovementRequestDto(amount, description.trim())))

    private fun parseResponse(response: retrofit2.Response<CashierStatusDto>): CashierStatusDto {
        if (response.isSuccessful) {
            return response.body() ?: throw ApiException(response.code(), "Resposta vazia")
        }
        throw parseError(response.code(), response.errorBody()?.string())
    }

    private fun parseError(code: Int, body: String?): ApiException {
        if (!body.isNullOrBlank()) {
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val errorCode = root["code"]?.jsonPrimitive?.content
                    ?: root["error"]?.jsonPrimitive?.content
                val message = root["error"]?.jsonPrimitive?.content
                    ?: root["message"]?.jsonPrimitive?.content
                    ?: body
                return ApiException(code, message, errorCode)
            }
        }
        return ApiException(code, body ?: "Erro no caixa")
    }

    companion object {
        suspend fun <T> runApi(block: suspend () -> T): Result<T> =
            runCatching { block() }.recoverCatching { e ->
                when (e) {
                    is ApiException -> throw e
                    is HttpException -> throw ApiException(e.code(), e.message())
                    else -> throw e
                }
            }
    }
}
