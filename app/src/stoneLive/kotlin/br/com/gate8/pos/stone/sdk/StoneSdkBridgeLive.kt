package br.com.gate8.pos.stone.sdk

import android.app.Application
import android.util.Log
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.prefs.DeviceConfigStore
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
import stone.providers.ActiveApplicationProvider
import stone.providers.CancellationProvider
import stone.providers.ReversalProvider
import stone.user.UserModel
import stone.utils.Stone
import stone.utils.keys.StoneKeyType
import br.com.stone.posandroid.providers.PosTransactionProvider
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.payment.VoidResult
import android.content.Context
import kotlinx.coroutines.CancellableContinuation
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

    /** Usuários retornados por [StoneStart.init] (null = ainda não ativado no app). */
    private var initUserList: List<UserModel>? = null

    /** Estado do pagamento em andamento, para permitir cancelamento manual. */
    private var currentProvider: PosTransactionProvider? = null
    private var currentContinuation: CancellableContinuation<Unit>? = null
    @Volatile private var userCancelled = false

    override fun knownActiveStoneCodes(): List<String> =
        activeUserModels().mapNotNull { it.stoneCode?.trim() }.filter { it.isNotEmpty() }.distinct()

    override fun initialize(application: Application) {
        val pixKeys = pixKeysFromBuildConfig()
        Log.i(
            TAG,
            "Stone SDK init — credenciais PIX configuradas: ${pixKeys.isNotEmpty()} " +
                "(authorization=${BuildConfig.STONE_PIX_QR_AUTHORIZATION.isNotBlank()}, " +
                "providerId=${BuildConfig.STONE_PIX_QR_PROVIDERID.isNotBlank()})",
        )
        initUserList = if (pixKeys.isEmpty()) {
            StoneStart.init(application)
        } else {
            StoneStart.init(application, pixKeys)
        }
        Stone.setAppName(APP_NAME)

        val codes = knownActiveStoneCodes()
        when {
            codes.isEmpty() -> Log.i(TAG, "Stone SDK init — aguardando ativação (StoneCode)")
            else -> Log.i(
                TAG,
                "Stone SDK init — ${codes.size} StoneCode(s) no POS: ${codes.joinToString()}",
            )
        }
    }

    override suspend fun ensureActivated(stoneCode: String?): Result<StoneActivationOutcome> {
        val code = stoneCode?.trim().orEmpty().ifBlank { config.getStoneCode().orEmpty() }
        if (code.isBlank()) {
            val onPos = knownActiveStoneCodes()
            val hint = if (onPos.isNotEmpty()) {
                " Informe o StoneCode (ativo no POS: ${onPos.joinToString()})."
            } else {
                ""
            }
            return Result.failure(IllegalStateException("Informe o StoneCode em Configurações.$hint"))
        }
        config.setStoneCode(code)
        if (hasStoneCode(code)) {
            return Result.success(StoneActivationOutcome.ALREADY_ACTIVE)
        }
        return runCatching {
            activateStoneCode(code)
            StoneActivationOutcome.NEWLY_ACTIVATED
        }
    }

    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
    ): PaymentResult {
        ensureActivated(null).getOrElse { throw it }
        val user = requireUser()
        val activity = activityHolder.requireActivity()
        val transaction = buildTransaction(amount, method, clientReference)

        val provider = PosTransactionProvider(activity, transaction, user)
        provider.dialogTitle = "Pagamento"
        provider.dialogMessage = "Processando…"

        executePosTransaction(provider, transaction, method)

        val status = provider.transactionStatus ?: transaction.transactionStatus
        return when (status) {
            TransactionStatusEnum.APPROVED -> mapApprovedPayment(method, transaction, activity)
            else -> throw IllegalStateException(resolveTransactionError(provider, transaction, method))
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
            ?: dao.getAllTransactions()?.firstOrNull { it.initiatorTransactionKey == transactionId }
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

    private fun activeUserModels(): List<UserModel> {
        val fromInit = initUserList.orEmpty()
        if (fromInit.isNotEmpty()) return fromInit
        return Stone.sessionApplication?.userModelList.orEmpty()
    }

    private fun hasStoneCode(code: String): Boolean =
        activeUserModels().any { it.stoneCode.equals(code, ignoreCase = true) }

    private suspend fun activateStoneCode(stoneCode: String) {
        val context = activityHolder.requireActivity()
        suspendCancellableCoroutine { cont ->
            val provider = ActiveApplicationProvider(context)
            provider.dialogTitle = "Aguarde"
            provider.dialogMessage = "Ativando o Stone Code"
            provider.connectionCallback = object : StoneCallbackInterface {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError() {
                    if (cont.isActive) {
                        val detail = provider.listOfErrors?.joinToString().orEmpty()
                        cont.resumeWithException(
                            IllegalStateException(
                                detail.ifBlank { "Falha ao ativar StoneCode $stoneCode" },
                            ),
                        )
                    }
                }
            }
            provider.activate(listOf(stoneCode))
        }
    }

    private suspend fun executePosTransaction(
        provider: PosTransactionProvider,
        transaction: TransactionObject,
        method: PaymentMethodApi,
    ) {
        userCancelled = false
        currentProvider = provider
        try {
            suspendCancellableCoroutine { cont ->
                currentContinuation = cont
                activityHolder.setCancelHandler { cancelCurrentPayment() }
                provider.isDefaultUI()
                provider.connectionCallback = object : StoneActionCallback {
                    override fun onSuccess() {
                        if (cont.isActive) {
                            if (userCancelled) {
                                cont.resumeWithException(PaymentCancelledException())
                            } else {
                                cont.resume(Unit)
                            }
                        }
                    }

                    override fun onStatusChanged(action: Action) {
                        when (action) {
                            Action.TRANSACTION_WAITING_QRCODE_SCAN -> {
                                Log.i(TAG, "PIX: aguardando leitura do QRCode (DefaultUI Stone)")
                                activityHolder.onPixQrCodeWaiting(transaction.qrCode)
                            }
                            else -> Log.d(TAG, "Stone action: $action")
                        }
                    }

                    override fun onError() {
                        if (cont.isActive) {
                            if (userCancelled) {
                                cont.resumeWithException(PaymentCancelledException())
                                return
                            }
                            if (method == PaymentMethodApi.PIX && isQrExpired(provider)) {
                                Log.i(TAG, "PIX: QR Code expirou")
                                cont.resumeWithException(PixExpiredException())
                                return
                            }
                            Log.e(
                                TAG,
                                "Transação $method falhou — errors=${provider.listOfErrors} " +
                                    "msgAuthorize=${provider.messageFromAuthorize} " +
                                    "status=${provider.transactionStatus ?: transaction.transactionStatus}",
                            )
                            cont.resumeWithException(
                                IllegalStateException(
                                    resolveTransactionError(provider, transaction, method),
                                ),
                            )
                        }
                    }
                }
                cont.invokeOnCancellation {
                    activityHolder.clearPixQrCode()
                    runCatching { provider.abortPayment() }
                }
                provider.execute()
            }
        } finally {
            activityHolder.setCancelHandler(null)
            activityHolder.clearPixQrCode()
            currentProvider = null
            currentContinuation = null
        }
    }

    override fun cancelCurrentPayment() {
        val cont = currentContinuation ?: return
        userCancelled = true
        activityHolder.setCancelHandler(null)
        activityHolder.clearPixQrCode()
        runCatching { currentProvider?.abortPayment() }
        currentContinuation = null
        if (cont.isActive) {
            cont.resumeWithException(PaymentCancelledException())
        }
    }

    private fun isQrExpired(provider: PosTransactionProvider): Boolean {
        val text = buildString {
            append(provider.messageFromAuthorize.orEmpty())
            append(' ')
            append(provider.listOfErrors?.joinToString().orEmpty())
        }.uppercase()
        return text.contains("EXPIRED") || text.contains("EXPIRAD")
    }

    private fun resolveTransactionError(
        provider: PosTransactionProvider,
        transaction: TransactionObject,
        method: PaymentMethodApi,
    ): String {
        val fromAuthorize = provider.messageFromAuthorize?.trim().orEmpty()
        val fromErrors = provider.listOfErrors?.joinToString().orEmpty().trim()
        val status = provider.transactionStatus ?: transaction.transactionStatus
        val base = when {
            fromAuthorize.isNotBlank() -> fromAuthorize
            fromErrors.isNotBlank() -> fromErrors
            status != null -> "Transação não aprovada ($status)"
            else -> "Transação não aprovada"
        }
        return if (method == PaymentMethodApi.PIX) {
            "$base. Se o cliente pagou, confira na Conta Stone antes de cobrar de novo."
        } else {
            base
        }
    }

    private fun requireUser(): UserModel {
        val code = config.getStoneCode()?.trim().orEmpty()
        if (code.isNotEmpty()) {
            activeUserModels()
                .firstOrNull { it.stoneCode.equals(code, ignoreCase = true) }
                ?.let { return it }
        }
        return Stone.getUserModel(0)
            ?: throw IllegalStateException("Terminal Stone não ativado. Configure o StoneCode.")
    }

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

    private fun buildTransaction(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
    ): TransactionObject {
        val cents = (amount * 100.0).roundToLong().coerceAtLeast(1L)
        return TransactionObject().apply {
            setAmount(cents.toString())
            clientReference?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
                setInitiatorTransactionKey(ref)
                setRequestId(ref)
            }
            setInstalmentTransaction(InstalmentTransactionEnum.ONE_INSTALMENT)
            setTypeOfTransaction(
                when (method) {
                    PaymentMethodApi.CREDIT -> TypeOfTransactionEnum.CREDIT
                    PaymentMethodApi.DEBIT -> TypeOfTransactionEnum.DEBIT
                    PaymentMethodApi.PIX -> TypeOfTransactionEnum.PIX
                    else -> throw IllegalArgumentException("Método não suportado na Stone: $method")
                },
            )
            // Captura automática (autorização + cobrança na mesma operação).
            // Captura posterior (setCapture false + CaptureTransactionProvider) não é usada no Gate8:
            // bilheteria/conveniência exige pagamento confirmado na hora.
            setCapture(true)
            setShortName(SHORT_NAME)
            // PIX exige os dados do sub-merchant para montar o payload do QR Code.
            // Sem eles a SDK falha instantaneamente (INTERNAL_ERROR) antes de exibir o QR.
            if (method == PaymentMethodApi.PIX) {
                applySubMerchant()
            }
        }
    }

    /**
     * Dados do estabelecimento (sub-merchant) exigidos pela Stone para gerar o QR Code PIX.
     * Espelha o fluxo do demo oficial (stone-payments/demo-sdk-android).
     * TODO: para produção, popular com os dados reais do estabelecimento (via config).
     */
    private fun TransactionObject.applySubMerchant() {
        setSubMerchantCity("Sao Paulo")
        setSubMerchantTaxIdentificationNumber("00.000.000/0001-91")
        setSubMerchantRegisteredIdentifier("00.000.000/0001-91")
        setSubMerchantPostalAddress("01001-000")
        setSubMerchantLegalName("Gate8")
        setSubMerchantTaxIdentificationType("JRDC")
        setSubMerchantPhoneNumber("(11) 9 9999-9999")
        setSubMerchantCountryCode("076")
        setSubMerchantState("SP")
        setSubMerchantNeighborhood("Centro")
        setSubMerchantEmail("contato@gate8.com.br")
        setSubMerchantSiteUrl("www.gate8.com.br")
        setSubMerchantBuildingNumber("1")
        setSubMerchantAddress("Praca da Se")
        setSubMerchantCategoryCode("5734")
        setSubMerchantPaymentGatewayId("123123")
    }

    companion object {
        private const val APP_NAME = "Gate8"
        private const val SHORT_NAME = "Gate8"
        private const val TAG = "Gate8Stone"

        private fun pixKeysFromBuildConfig(): Map<StoneKeyType, String> {
            val authorization = BuildConfig.STONE_PIX_QR_AUTHORIZATION.trim()
            val providerId = BuildConfig.STONE_PIX_QR_PROVIDERID.trim()
            if (authorization.isBlank() || providerId.isBlank()) return emptyMap()
            return mapOf(
                StoneKeyType.QRCODE_AUTHORIZATION to normalizeQrAuthorization(authorization),
                StoneKeyType.QRCODE_PROVIDERID to providerId,
            )
        }

        /**
         * O demo oficial da Stone (stone-payments/demo-sdk-android) passa o valor de
         * QRCODE_AUTHORIZATION cru (sem prefixo "Bearer"). Mantemos o mesmo formato:
         * removemos qualquer prefixo "Bearer" caso venha configurado por engano.
         */
        private fun normalizeQrAuthorization(value: String): String {
            val trimmed = value.trim()
            return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                trimmed.removePrefix("Bearer ").removePrefix("bearer ").trim()
            } else {
                trimmed
            }
        }
    }
}
