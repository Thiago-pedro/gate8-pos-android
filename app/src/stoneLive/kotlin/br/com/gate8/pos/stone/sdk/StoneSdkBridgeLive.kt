package br.com.gate8.pos.stone.sdk

import android.app.Application
import android.content.Context
import br.com.stone.posandroid.providers.PosTransactionProvider
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult
import br.com.gate8.pos.stone.StoneActivityHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import stone.application.StoneStart
import stone.application.enums.Action
import stone.application.enums.InstalmentTransactionEnum
import stone.application.enums.TransactionStatusEnum
import stone.application.enums.TypeOfTransactionEnum
import stone.application.interfaces.StoneActionCallback
import stone.application.interfaces.StoneCallbackInterface
import stone.database.transaction.TransactionDAO
import stone.database.transaction.TransactionObject
import stone.providers.activeapplication.ActiveApplicationProvider
import stone.providers.CancellationProvider
import stone.providers.ReversalProvider
import stone.user.UserModel
import stone.utils.Stone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong

/**
 * SDK Stone real — compilado apenas com `packageCloudReadToken` + dependências no Gradle.
 */
class StoneSdkBridgeLive(
    private val activityHolder: StoneActivityHolder,
    private val config: DeviceConfigStore,
) : StoneSdkBridge {

    override val isLinked: Boolean = true

    override fun initialize(application: Application) {
        StoneStart.init(application)
        Stone.setAppName(APP_NAME)
    }

    override suspend fun ensureActivated(stoneCode: String?): Result<Unit> {
        val code = stoneCode?.trim().orEmpty().ifBlank { config.getStoneCode().orEmpty() }
        if (code.isBlank()) {
            return Result.failure(IllegalStateException("Informe o StoneCode em Configurações"))
        }
        if (hasStoneCode(code)) {
            return Result.success(Unit)
        }
        return runCatching { activateStoneCode(code) }
    }

    override suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult {
        ensureActivated(null).getOrElse { throw it }
        val user = requireUser()
        val activity = activityHolder.requireActivity()
        val transaction = buildTransaction(amount, method)

        val provider = PosTransactionProvider(activity, transaction, user)
        provider.dialogTitle = "Pagamento"
        provider.dialogMessage = "Processando…"

        executePosTransaction(provider)

        return when (transaction.transactionStatus) {
            TransactionStatusEnum.APPROVED -> mapApprovedPayment(method, transaction, activity)
            else -> {
                val msg = provider.messageFromAuthorize
                    ?: provider.listOfErrors?.joinToString().orEmpty()
                    .ifBlank { "Transação não aprovada" }
                throw IllegalStateException(msg)
            }
        }
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult {
        ensureActivated(null).getOrElse {
            return VoidResult(false, it.message ?: "Stone não ativado")
        }
        val activity = activityHolder.requireActivity()
        val dao = TransactionDAO(activity)
        val transaction = dao.findTransactionWithAtk(transactionId)
            ?: dao.allTransactions?.firstOrNull { it.initiatorTransactionKey == transactionId }
            ?: return VoidResult(false, "Transação não encontrada no SDK")

        val provider = CancellationProvider(activity, transaction)
        provider.dialogTitle = "Cancelamento"
        provider.dialogMessage = "Estornando…"

        val ok = suspendCancellableCoroutine { cont ->
            provider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError() {
                    if (cont.isActive) cont.resume(false)
                }
            }
            provider.execute()
        }

        return if (ok) {
            VoidResult(true, "Estorno aprovado na Stone")
        } else {
            VoidResult(false, "Falha no estorno Stone")
        }
    }

    override fun runReversal(context: Context) {
        runCatching {
            val provider = ReversalProvider(context)
            provider.dialogTitle = "Gate8"
            provider.dialogMessage = "Verificando transações com erro…"
            provider.isDefaultUI()
            provider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() = Unit
                override fun onError() = Unit
            }
            provider.execute()
        }
    }

    private fun hasStoneCode(code: String): Boolean {
        val users = Stone.sessionApplication?.userModelList.orEmpty()
        return users.any { it.stoneCode.equals(code, ignoreCase = true) }
    }

    private suspend fun activateStoneCode(stoneCode: String) {
        val context = activityHolder.requireActivity()
        suspendCancellableCoroutine { cont ->
            val provider = ActiveApplicationProvider(context)
            provider.dialogTitle = "Gate8"
            provider.dialogMessage = "Ativando terminal Stone…"
            provider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    config.setStoneCode(stoneCode)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError() {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Falha ao ativar StoneCode $stoneCode"),
                        )
                    }
                }
            }
            provider.activate(stoneCode)
        }
    }

    private suspend fun executePosTransaction(provider: PosTransactionProvider) {
        suspendCancellableCoroutine { cont ->
            provider.connectionCallback = object : StoneActionCallback {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onStatusChanged(action: Action) {
                    // PIX: exibir transactionObject.qRCode em overlay (próxima iteração)
                }

                override fun onError() {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                provider.listOfErrors?.joinToString().orEmpty()
                                    .ifBlank { "Erro na transação" },
                            ),
                        )
                    }
                }
            }
            provider.execute()
        }
    }

    private fun requireUser(): UserModel =
        Stone.getUserModel(0)
            ?: throw IllegalStateException("Terminal Stone não ativado. Configure o StoneCode.")

    private fun mapApprovedPayment(
        method: PaymentMethodApi,
        transaction: TransactionObject,
        activity: android.app.Activity,
    ): PaymentResult {
        val dao = TransactionDAO(activity)
        val atk = transaction.acquirerTransactionKey
        val saved = if (!atk.isNullOrBlank()) dao.findTransactionWithAtk(atk) else null
        val tx = saved ?: transaction
        return PaymentResult(
            method = method,
            nsu = tx.acquirerTransactionKey ?: tx.initiatorTransactionKey.orEmpty(),
            authorization = tx.authorizationCode.orEmpty(),
            brand = tx.cardBrandName.orEmpty(),
            transactionId = tx.initiatorTransactionKey ?: tx.acquirerTransactionKey.orEmpty(),
        )
    }

    private fun buildTransaction(amount: Double, method: PaymentMethodApi): TransactionObject {
        val cents = (amount * 100.0).roundToLong().coerceAtLeast(1L)
        return TransactionObject().apply {
            setAmount(cents.toString())
            setInstalmentTransaction(InstalmentTransactionEnum.ONE_INSTALMENT)
            setTypeOfTransaction(
                when (method) {
                    PaymentMethodApi.CREDIT -> TypeOfTransactionEnum.CREDIT
                    PaymentMethodApi.DEBIT -> TypeOfTransactionEnum.DEBIT
                    PaymentMethodApi.PIX -> TypeOfTransactionEnum.PIX
                    else -> throw IllegalArgumentException("Método não suportado na Stone: $method")
                },
            )
            setCapture(true)
            setShortName("Gate8")
        }
    }

    companion object {
        private const val APP_NAME = "Gate8"
    }
}
