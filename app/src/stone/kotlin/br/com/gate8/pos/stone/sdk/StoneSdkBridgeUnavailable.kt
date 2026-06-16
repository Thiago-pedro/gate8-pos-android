package br.com.gate8.pos.stone.sdk

import android.app.Application
import android.content.Context
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult

/**
 * Ponte usada enquanto o token PackageCloud não estiver configurado.
 * Mensagens orientam o desenvolvedor.
 */
class StoneSdkBridgeUnavailable : StoneSdkBridge {
    override val isLinked: Boolean = false

    override fun initialize(application: Application) = Unit

    override suspend fun ensureActivated(stoneCode: String?): Result<Unit> =
        Result.failure(StoneSdkNotLinkedException())

    override suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult =
        throw StoneSdkNotLinkedException()

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult = VoidResult(
        success = false,
        message = StoneSdkNotLinkedException().message ?: "SDK Stone indisponível",
    )

    override fun runReversal(context: Context) = Unit
}
