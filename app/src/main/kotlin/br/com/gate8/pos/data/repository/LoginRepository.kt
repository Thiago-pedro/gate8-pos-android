package br.com.gate8.pos.data.repository

import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.LoginRequestDto
import br.com.gate8.pos.data.remote.dto.LoginResponseDto
import br.com.gate8.pos.domain.model.LoginResult
import kotlinx.serialization.json.Json

class LoginRepository(
    private val api: PosApiService,
    private val json: Json,
) {
    suspend fun login(producerToken: String, fingerprint: String, label: String?): LoginResult {
        val httpResponse = api.login(
            LoginRequestDto(
                token = producerToken,
                fingerprint = fingerprint,
                label = label?.takeIf { it.isNotBlank() },
            ),
        )
        val body = httpResponse.body()
            ?: parseErrorBody(httpResponse.errorBody()?.string())
            ?: return LoginResult.Error("Erro no servidor (${httpResponse.code()})")

        return mapResponse(body)
    }

    private fun parseErrorBody(raw: String?): LoginResponseDto? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(LoginResponseDto.serializer(), raw) }.getOrNull()
    }

    private fun mapResponse(response: LoginResponseDto): LoginResult = when (response.status) {
        "active" -> {
            val token = response.deviceToken
                ?: return LoginResult.Error("missing_device_token")
            val deviceId = response.deviceId
                ?: return LoginResult.Error("missing_device_id")
            LoginResult.Active(
                deviceToken = token,
                deviceId = deviceId,
                deviceName = response.deviceName ?: "Maquininha",
                producerName = response.producerName ?: "Produtor",
                merchantName = response.merchantName,
            )
        }
        "pending" -> LoginResult.Pending(response.deviceName)
        "disabled" -> LoginResult.Disabled(response.deviceName)
        "error" -> LoginResult.Error(response.error ?: "unknown")
        else -> LoginResult.Error("unknown_status")
    }
}
