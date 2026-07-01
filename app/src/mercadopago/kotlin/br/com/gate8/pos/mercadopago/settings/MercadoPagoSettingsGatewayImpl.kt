package br.com.gate8.pos.mercadopago.settings

import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.payment.TerminalSettingsGateway

class MercadoPagoSettingsGatewayImpl(
    private val config: DeviceConfigStore,
) : TerminalSettingsGateway {
    override val showTerminalSection: Boolean = true

    override fun getTerminalId(): String = config.getMercadoPagoTerminalId().orEmpty()

    override suspend fun saveTerminalId(terminalId: String): Result<String> {
        val trimmed = terminalId.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o Terminal ID do Mercado Pago Point."))
        }
        config.setMercadoPagoTerminalId(trimmed)
        return Result.success("Terminal Mercado Pago salvo.")
    }
}
