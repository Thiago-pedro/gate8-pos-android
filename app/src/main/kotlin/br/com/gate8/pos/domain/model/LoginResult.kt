package br.com.gate8.pos.domain.model

sealed class LoginResult {
    data class Active(
        val deviceToken: String,
        val deviceId: String,
        val deviceName: String,
        val producerName: String,
        val merchantName: String?,
    ) : LoginResult()

    data class Pending(val deviceName: String?) : LoginResult()

    data class Disabled(val deviceName: String?) : LoginResult()

    data class Error(val code: String) : LoginResult()
}
