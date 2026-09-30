package br.com.gate8.pos.core.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Interpreta o JSON do callback Deep Link da Cielo.
 *
 * Doc LIO: `statusCode` 0 = Pix pago, 1 = cartão autorizado, 2 = cancelado.
 * Esse campo vem em `paymentFields` (string) ou, em alguns retornos Pix, na raiz.
 * `code`/`reason` no envelope de erro NÃO é o mesmo que `statusCode`.
 */
object CieloCallbackParser {

    fun parseJson(raw: String, uriResponseCode: Int? = null): CieloParsedCallback {
        val json = JSONObject(raw)
        val payments = json.optJSONArray("payments")
        val orderId = json.optString("id").trim()
        val paidAmount = json.optLong("paidAmount", 0L)
        val statusCode = extractStatusCode(json, payments)
        val envelopeCode = if (json.has("code") && !json.isNull("code")) {
            json.optInt("code")
        } else {
            null
        }
        val reason = CieloUserText.repair(
            json.optString("reason", "").ifBlank { json.optString("message", "") },
        )

        if (isApproved(statusCode, paidAmount, payments, orderId, envelopeCode, reason, uriResponseCode)) {
            return CieloParsedCallback.Approved(json)
        }

        val errorCode = when {
            statusCode == 2 -> 2
            envelopeCode != null && envelopeCode != 0 -> envelopeCode
            uriResponseCode != null && uriResponseCode != 0 -> uriResponseCode
            else -> -1
        }
        return CieloParsedCallback.Rejected(
            code = errorCode,
            reason = reason.ifBlank { "Falha na operação Cielo." },
        )
    }

    private fun isApproved(
        statusCode: Int?,
        paidAmount: Long,
        payments: JSONArray?,
        orderId: String,
        envelopeCode: Int?,
        reason: String,
        uriResponseCode: Int?,
    ): Boolean {
        if (statusCode == 2) return false
        if (uriResponseCode == 2 && !hasApprovedPayment(payments) && paidAmount <= 0L) return false

        if (statusCode == 0 || statusCode == 1) return true
        if (paidAmount > 0L) return true
        if (hasApprovedPayment(payments)) return true

        val errorEnvelope = envelopeCode != null || reason.isNotBlank()
        if (orderId.isNotBlank() && (envelopeCode == null || envelopeCode == 0) && reason.isBlank()) {
            return true
        }
        if (!errorEnvelope && orderId.isNotBlank()) return true
        return false
    }

    private fun hasApprovedPayment(payments: JSONArray?): Boolean {
        if (payments == null || payments.length() == 0) return false
        var sawApproved = false
        for (i in 0 until payments.length()) {
            val payment = payments.optJSONObject(i) ?: continue
            val code = extractPaymentStatus(payment)
            if (code == 2) continue
            sawApproved = true
        }
        return sawApproved
    }

    private fun extractStatusCode(json: JSONObject, payments: JSONArray?): Int? {
        optStatusCode(json, "statusCode")?.let { return it }
        if (payments == null) return null
        for (i in 0 until payments.length()) {
            val payment = payments.optJSONObject(i) ?: continue
            extractPaymentStatus(payment)?.let { return it }
        }
        return null
    }

    private fun extractPaymentStatus(payment: JSONObject): Int? {
        optStatusCode(payment, "statusCode")?.let { return it }
        val fields = payment.optJSONObject("paymentFields") ?: return null
        return optStatusCode(fields, "statusCode")
    }

    private fun optStatusCode(obj: JSONObject, key: String): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return when (val raw = obj.opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }
}

sealed class CieloParsedCallback {
    data class Approved(val json: JSONObject) : CieloParsedCallback()
    data class Rejected(val code: Int, val reason: String) : CieloParsedCallback()
}
