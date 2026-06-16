package br.com.gate8.pos.stone.sdk

import android.app.Application
import android.content.Context
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult

interface StoneSdkBridge {
    val isLinked: Boolean

    fun initialize(application: Application)

    suspend fun ensureActivated(stoneCode: String?): Result<Unit>

    suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult

    suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult

    fun runReversal(context: Context)
}

class StoneSdkNotLinkedException(
    message: String = "SDK Stone não vinculado. Adicione packageCloudReadToken em local.properties e sincronize o Gradle.",
) : IllegalStateException(message)
