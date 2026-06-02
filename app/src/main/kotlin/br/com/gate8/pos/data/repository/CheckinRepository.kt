package br.com.gate8.pos.data.repository

import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.CheckinRequestDto
import br.com.gate8.pos.domain.model.CheckinOutcome
import br.com.gate8.pos.domain.model.CheckinResult

class CheckinRepository(
    private val api: PosApiService,
) {
    suspend fun checkin(code: String): CheckinResult {
        val normalized = code.trim().lowercase()
        val response = api.checkin(CheckinRequestDto(normalized))
        val body = response.body()

        return when (response.code()) {
            200 -> mapResult(body?.result, body?.ticket?.holderName)
            404 -> CheckinResult(CheckinOutcome.Invalid, "Ingresso não encontrado")
            409 -> mapResult(body?.result, body?.ticket?.holderName)
            401 -> CheckinResult(CheckinOutcome.Unknown, "Token inválido")
            else -> CheckinResult(CheckinOutcome.Unknown, "Erro ${response.code()}")
        }
    }

    private fun mapResult(result: String?, holderName: String?): CheckinResult {
        return when (result) {
            "ok" -> CheckinResult(CheckinOutcome.Ok, "Check-in OK", holderName)
            "invalid" -> CheckinResult(CheckinOutcome.Invalid, "Código inválido")
            "already_used" -> CheckinResult(CheckinOutcome.AlreadyUsed, "Ingresso já utilizado", holderName)
            "wrong_event" -> CheckinResult(CheckinOutcome.WrongEvent, "Ingresso de outro evento", holderName)
            else -> CheckinResult(CheckinOutcome.Unknown, result ?: "Resposta desconhecida")
        }
    }
}
