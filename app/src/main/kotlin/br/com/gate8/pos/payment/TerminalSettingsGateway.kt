package br.com.gate8.pos.payment

interface TerminalSettingsGateway {
    val showTerminalSection: Boolean

    fun getTerminalId(): String

    suspend fun saveTerminalId(terminalId: String): Result<String>
}
