package br.com.gate8.pos.cielo.deeplink

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/**
 * Sessão única de deep link Cielo: aguarda o callback `order://response`.
 */
object CieloDeeplinkSession {
    private val mutex = Mutex()
    private val pending = AtomicReference<CompletableDeferred<CieloDeeplinkResponse>?>(null)

    const val CALLBACK = "order://response"
    private const val TIMEOUT_MS = 10 * 60 * 1000L

    suspend fun awaitResponse(block: suspend () -> Unit): CieloDeeplinkResponse {
        mutex.withLock {
            pending.get()?.cancel()
            val deferred = CompletableDeferred<CieloDeeplinkResponse>()
            pending.set(deferred)
            try {
                block()
                return withTimeout(TIMEOUT_MS) { deferred.await() }
            } finally {
                pending.compareAndSet(deferred, null)
            }
        }
    }

    fun completeFromUriResponse(responseBase64: String?) {
        val deferred = pending.get() ?: return
        if (responseBase64.isNullOrBlank()) {
            deferred.complete(
                CieloDeeplinkResponse.Error(code = -1, reason = "Resposta vazia da Cielo Smart."),
            )
            return
        }
        deferred.complete(parseResponse(responseBase64))
    }

    fun cancelPending(reason: String = "Pagamento cancelado") {
        pending.get()?.complete(CieloDeeplinkResponse.Error(code = 1, reason = reason))
    }

    fun toBase64(json: String): String =
        Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun parseResponse(base64: String): CieloDeeplinkResponse {
        return try {
            val raw = String(Base64.decode(base64, Base64.DEFAULT), StandardCharsets.UTF_8)
            val json = JSONObject(raw)
            if (json.has("payments") || json.has("id")) {
                return CieloDeeplinkResponse.Success(json)
            }
            val code = json.optInt("code", 0)
            val reason = json.optString("reason", "")
                .ifBlank { json.optString("message", "") }
            if (reason.isNotBlank() && code != 0) {
                CieloDeeplinkResponse.Error(code = code, reason = reason)
            } else if (code != 0) {
                CieloDeeplinkResponse.Error(
                    code = code,
                    reason = reason.ifBlank { "Falha na operação Cielo." },
                )
            } else {
                CieloDeeplinkResponse.Success(json)
            }
        } catch (e: Exception) {
            CieloDeeplinkResponse.Error(code = -1, reason = e.message ?: "JSON inválido da Cielo.")
        }
    }
}

sealed class CieloDeeplinkResponse {
    data class Success(val json: JSONObject) : CieloDeeplinkResponse()
    data class Error(val code: Int, val reason: String) : CieloDeeplinkResponse()
}
