package br.com.gate8.pos.cielo.settings

import br.com.gate8.pos.payment.TerminalSettingsGateway

/** Deep Link não exige Terminal ID (diferente do MP Point PDV). */
class CieloSettingsGatewayImpl : TerminalSettingsGateway {
    override val showTerminalSection: Boolean = false

    override fun getTerminalId(): String = ""

    override suspend fun saveTerminalId(terminalId: String): Result<String> =
        Result.success("Cielo Smart não usa Terminal ID — pagamento via Deep Link.")
}
