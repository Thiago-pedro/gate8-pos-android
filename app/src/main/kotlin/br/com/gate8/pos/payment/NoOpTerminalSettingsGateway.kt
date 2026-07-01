package br.com.gate8.pos.payment

class NoOpTerminalSettingsGateway : TerminalSettingsGateway {
    override val showTerminalSection: Boolean = false

    override fun getTerminalId(): String = ""

    override suspend fun saveTerminalId(terminalId: String): Result<String> =
        Result.failure(UnsupportedOperationException("Flavor mock"))
}
