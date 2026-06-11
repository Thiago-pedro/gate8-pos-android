package br.com.gate8.pos.data.repository

import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.LoginRequestDto
import br.com.gate8.pos.domain.model.LoginResult

class LoginRepository(
    private val api: PosApiService,
) {
    suspend fun login(producerToken: String, fingerprint: String, label: String?): LoginResult {
        val response = api.login(
            LoginRequestDto(
                token = producerToken,
                fingerprint = fingerprint,
                label = label?.takeIf { it.isNotBlank() },
            ),
        )
        return when (response.status) {
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
                )
            }
            "pending" -> LoginResult.Pending(response.deviceName)
            "disabled" -> LoginResult.Disabled(response.deviceName)
            "error" -> LoginResult.Error(response.error ?: "unknown")
            else -> LoginResult.Error("unknown_status")
        }
    }
}
