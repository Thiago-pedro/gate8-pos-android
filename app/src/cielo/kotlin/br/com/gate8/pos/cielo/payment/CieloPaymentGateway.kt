package br.com.gate8.pos.cielo.payment

import android.net.Uri
import android.util.Log
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkResponse
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkSession
import br.com.gate8.pos.cielo.deeplink.CieloLioLauncher
import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.CardBrandNormalizer
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * Pagamentos na Cielo Smart via Deep Link (`lio://payment` / `payment-reversal`).
 */
class CieloPaymentGateway : PaymentGateway {

    @Volatile
    private var cancelRequested = false

    override fun cancelCurrentPayment() {
        cancelRequested = true
        CieloDeeplinkSession.cancelPending("Pagamento cancelado")
    }

    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
        saleDraft: MpSaleDraftDto?,
    ): PaymentResult {
        if (method == PaymentMethodApi.CASH) {
            return PaymentResult(
                method = method,
                nsu = "",
                authorization = "",
                brand = "",
                transactionId = clientReference.orEmpty(),
            )
        }
        ensureCredentials()
        cancelRequested = false

        val cents = (amount * 100.0).roundToLong().coerceAtLeast(1L)
        val paymentCode = paymentCodeFor(method)
        val body = JSONObject().apply {
            put("accessToken", BuildConfig.CIELO_ACCESS_TOKEN)
            put("clientID", BuildConfig.CIELO_CLIENT_ID)
            if (!clientReference.isNullOrBlank()) put("reference", clientReference)
            if (BuildConfig.CIELO_MERCHANT_ID.isNotBlank()) {
                // merchantCode no deep link é o EC formatado; merchant-id UUID vai só se a Cielo pedir —
                // deixamos reference para conciliação Gate8.
            }
            put("installments", 0)
            put(
                "items",
                JSONArray().put(
                    JSONObject()
                        .put("name", "Gate8")
                        .put("quantity", 1)
                        .put("sku", clientReference ?: "gate8")
                        .put("unitOfMeasure", "unidade")
                        .put("unitPrice", cents),
                ),
            )
            put("paymentCode", paymentCode)
            put("value", cents.toString())
        }

        val response = launchLio("payment", body)
        if (cancelRequested) throw PaymentCancelledException()
        return when (response) {
            is CieloDeeplinkResponse.Error -> {
                if (response.reason.contains("CANCEL", ignoreCase = true) ||
                    response.reason.contains("usuário", ignoreCase = true)
                ) {
                    throw PaymentCancelledException()
                }
                throw IllegalStateException(formatCieloError(response.code, response.reason))
            }
            is CieloDeeplinkResponse.Success -> mapPaymentSuccess(response.json, method)
        }
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
        authorization: String?,
    ): VoidResult {
        if (method == PaymentMethodApi.CASH) {
            return VoidResult(success = true, message = "Estorno em dinheiro — ajuste no caixa.")
        }
        ensureCredentials()
        val orderId = transactionId.trim()
        val cieloCode = nsu?.trim().orEmpty()
        val authCode = authorization?.trim().orEmpty()
        if (orderId.isBlank() || cieloCode.isBlank() || authCode.isBlank()) {
            return VoidResult(
                success = false,
                message = "Estorno Cielo exige id da ordem, NSU (cieloCode) e autorização (authCode).",
            )
        }
        val cents = (amount * 100.0).roundToLong().coerceAtLeast(1L)
        val body = JSONObject().apply {
            put("id", orderId)
            put("clientID", BuildConfig.CIELO_CLIENT_ID)
            put("accessToken", BuildConfig.CIELO_ACCESS_TOKEN)
            put("cieloCode", cieloCode)
            put("authCode", authCode)
            put("value", cents)
        }
        return when (val response = launchLio("payment-reversal", body)) {
            is CieloDeeplinkResponse.Error ->
                VoidResult(success = false, message = response.reason)
            is CieloDeeplinkResponse.Success ->
                VoidResult(success = true, message = "Estorno Cielo OK · ordem $orderId")
        }
    }

    private suspend fun launchLio(path: String, body: JSONObject): CieloDeeplinkResponse {
        val base64 = CieloDeeplinkSession.toBase64(body.toString())
        val uri = Uri.parse(
            "lio://$path?request=${Uri.encode(base64)}&urlCallback=${Uri.encode(CieloDeeplinkSession.CALLBACK)}",
        )
        return CieloDeeplinkSession.awaitResponse {
            val activity = CieloActivityHolder.get()
                ?: throw IllegalStateException("Abra o app Gate8 na Cielo Smart para pagar.")
            CieloLioLauncher.start(activity, uri)
            Log.i(TAG, "Deep link Cielo aberto: lio://$path")
        }
    }

    private fun mapPaymentSuccess(json: JSONObject, method: PaymentMethodApi): PaymentResult {
        val orderId = json.optString("id")
        val payments = json.optJSONArray("payments")
        val payment = payments?.optJSONObject(0)
        val cieloCode = payment?.optString("cieloCode").orEmpty()
        val authCode = payment?.optString("authCode").orEmpty()
        val brand = extractBrand(payment, method)
        if (brand.isBlank() && method != PaymentMethodApi.PIX && method != PaymentMethodApi.CASH) {
            Log.w(TAG, "Bandeira ausente no retorno Cielo. payment=${payment?.toString()?.take(800)}")
        } else if (brand.isNotBlank()) {
            Log.i(TAG, "Bandeira Cielo: $brand")
        }
        if (orderId.isBlank()) {
            throw IllegalStateException("Resposta Cielo sem id da ordem.")
        }
        return PaymentResult(
            method = method,
            nsu = cieloCode.ifBlank { orderId.takeLast(8) },
            authorization = authCode,
            brand = brand,
            transactionId = orderId,
        )
    }

    private fun extractBrand(payment: JSONObject?, method: PaymentMethodApi): String {
        // Pix/dinheiro não têm bandeira — payment_method já carrega a forma.
        if (method == PaymentMethodApi.PIX || method == PaymentMethodApi.CASH) return ""
        if (payment == null) return ""
        val fields = payment.optJSONObject("paymentFields")
        val card = payment.optJSONObject("card")
        // Doc Cielo: payments[].brand (Deep Link) ou card.brand (Order Manager).
        // NÃO usar primaryProductName (é DEBITO/CREDITO).
        val candidates = listOf(
            payment.optString("brand"),
            payment.optString("cardBrand"),
            payment.optString("brandName"),
            payment.optString("bandeira"),
            card?.optString("brand"),
            card?.optString("cardBrand"),
            card?.optString("brandName"),
            fields?.optString("brand"),
            fields?.optString("cardBrand"),
            fields?.optString("brandName"),
            fields?.optString("bandeira"),
            fields?.optString("cardLabelApplication"),
        )
        return candidates
            .asSequence()
            .mapNotNull { CardBrandNormalizer.normalize(it) }
            .firstOrNull()
            .orEmpty()
    }

    private fun paymentCodeFor(method: PaymentMethodApi): String = when (method) {
        PaymentMethodApi.DEBIT -> "DEBITO_AVISTA"
        PaymentMethodApi.CREDIT -> "CREDITO_AVISTA"
        PaymentMethodApi.PIX -> "PIX"
        else -> "CREDITO_AVISTA"
    }

    private fun ensureCredentials() {
        if (BuildConfig.CIELO_CLIENT_ID.isBlank() || BuildConfig.CIELO_ACCESS_TOKEN.isBlank()) {
            throw IllegalStateException(
                "Credenciais Cielo ausentes. Preencha CIELO_* em local.properties.cielo.txt",
            )
        }
    }

    /** Mantém code+reason juntos para o mapper de UX (opt-in -999/-990 etc.). */
    private fun formatCieloError(code: Int, reason: String): String {
        val trimmed = reason.trim()
        if (trimmed.startsWith("$code") || trimmed.startsWith("-$code")) return trimmed
        return if (code != 0) "$code, $trimmed" else trimmed
    }

    companion object {
        private const val TAG = "CieloPayment"
    }
}
