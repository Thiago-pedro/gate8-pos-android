package br.com.gate8.pos.mercadopago.payment

import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult

/**
 * Pagamentos via Mercado Pago Point (API de Orders).
 *
 * Fluxo previsto:
 * 1. App chama o backend Gate8 para criar a order no MP (`POST /payments/mp/orders`).
 * 2. O terminal Point recebe a order e o cliente paga.
 * 3. App aguarda confirmação (polling/webhook via backend).
 * 4. Retorna [PaymentResult] para registrar a venda em `/sales`.
 */
class MercadoPagoPaymentGateway(
    private val config: DeviceConfigStore,
    @Suppress("unused") private val api: PosApiService,
) : PaymentGateway {
    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
    ): PaymentResult {
        val terminalId = config.getMercadoPagoTerminalId()?.trim().orEmpty()
        if (terminalId.isBlank()) {
            throw IllegalStateException(
                "Configure o Terminal ID do Mercado Pago Point em Configurações.",
            )
        }
        if (method == PaymentMethodApi.CASH) {
            return PaymentResult(
                method = method,
                nsu = "",
                authorization = "",
                brand = "",
                transactionId = clientReference.orEmpty(),
            )
        }
        // Endpoints no Lovable ainda não implementados — próxima fase da migração.
        throw UnsupportedOperationException(
            "Integração Mercado Pago Point em desenvolvimento. " +
                "Próximo passo: endpoints /payments/mp no backend Gate8.",
        )
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult = VoidResult(
        success = false,
        message = "Estorno Mercado Pago ainda não implementado.",
    )

    override fun cancelCurrentPayment() {
        throw PaymentCancelledException()
    }
}
