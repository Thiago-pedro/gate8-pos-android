package br.com.gate8.pos.cielo.deeplink

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import br.com.gate8.pos.core.util.CieloCallbackParser
import br.com.gate8.pos.core.util.CieloParsedCallback
import br.com.gate8.pos.core.util.CieloUserText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

enum class CieloLioOp {
    PAYMENT,
    PRINT,
}

/**
 * Sessão única de deep link Cielo.
 *
 * Callback **não** usa `order://response`: esse scheme é o default da Cielo e
 * o Checkout Móvel também registra — o Android mostra "Abrir com Gate8 / Checkout
 * Móvel", o Gate8 não recebe o retorno e a venda/ficha não concluem.
 *
 * Pagamento e impressão usam hosts diferentes para o callback do UriApp não
 * misturar um `lio://print` no meio do Pix.
 */
object CieloDeeplinkSession {
    private val mutex = Mutex()
    private val pending = AtomicReference<CompletableDeferred<CieloDeeplinkResponse>?>(null)
    private val pendingOp = AtomicReference<CieloLioOp?>(null)
    private val delayedError = AtomicReference<Runnable?>(null)
    private val parkedPaymentError = AtomicReference<CieloDeeplinkResponse.Error?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    const val CALLBACK_PAYMENT = "gate8cielo://payment"
    const val CALLBACK_PRINT = "gate8cielo://print"
    const val CALLBACK = CALLBACK_PAYMENT
    private const val TIMEOUT_MS = 10 * 60 * 1000L
    private const val PIX_CONFIRM_GRACE_MS = 2_000L

    suspend fun awaitResponse(
        op: CieloLioOp = CieloLioOp.PAYMENT,
        block: suspend () -> Unit,
    ): CieloDeeplinkResponse {
        mutex.withLock {
            pending.get()?.cancel()
            clearDelayedError()
            parkedPaymentError.set(null)
            val deferred = CompletableDeferred<CieloDeeplinkResponse>()
            pending.set(deferred)
            pendingOp.set(op)
            try {
                block()
                return withTimeout(TIMEOUT_MS) { deferred.await() }
            } finally {
                clearDelayedError()
                parkedPaymentError.set(null)
                pending.compareAndSet(deferred, null)
                pendingOp.compareAndSet(op, null)
            }
        }
    }

    fun completeFromUriResponse(responseBase64: String?) {
        completeFromCallback(responseBase64, uriResponseCode = null, host = null)
    }

    fun completeFromCallback(
        responseBase64: String?,
        uriResponseCode: Int?,
        host: String?,
    ) {
        val incomingOp = opFromHost(host)
        val waitingOp = pendingOp.get()
        if (incomingOp != null && waitingOp != null && incomingOp != waitingOp) {
            Log.w(TAG, "Ignorando callback $incomingOp; aguardando $waitingOp")
            return
        }
        val deferred = pending.get() ?: return
        if (responseBase64.isNullOrBlank()) {
            finish(deferred, CieloDeeplinkResponse.Error(code = -1, reason = "Resposta vazia da Cielo Smart."))
            return
        }
        val parsed = parseResponse(responseBase64, uriResponseCode)
        if (parsed is CieloDeeplinkResponse.Success) {
            parkedPaymentError.set(null)
            finish(deferred, parsed)
            return
        }
        if (
            parsed is CieloDeeplinkResponse.Error &&
            waitingOp == CieloLioOp.PAYMENT &&
            CieloUserText.isOperationNotFinished(parsed.reason)
        ) {
            // A Cielo dispara isso no Pix antes de confirmar o QR. Não encerra a venda ainda.
            parkedPaymentError.set(parsed)
            Log.w(TAG, "Callback 'não finalizada' estacionado — esperando confirmação ou retorno ao Gate8")
            return
        }
        parkedPaymentError.set(null)
        finish(deferred, parsed)
    }

    /**
     * MainActivity voltou ao foreground. Se o Pix só tinha mandado "não finalizada"
     * e não veio ordem paga, conclui como cancelado (com folga para um 2º callback).
     */
    fun onHostActivityResumed() {
        val parked = parkedPaymentError.get() ?: return
        val deferred = pending.get() ?: return
        if (pendingOp.get() != CieloLioOp.PAYMENT || deferred.isCompleted) return
        schedulePaymentError(deferred, parked)
    }

    fun cancelPending(reason: String = "Pagamento cancelado") {
        parkedPaymentError.set(null)
        clearDelayedError()
        pending.get()?.complete(CieloDeeplinkResponse.Error(code = 1, reason = reason))
    }

    fun toBase64(json: String): String =
        Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun schedulePaymentError(
        deferred: CompletableDeferred<CieloDeeplinkResponse>,
        error: CieloDeeplinkResponse.Error,
    ) {
        clearDelayedError()
        Log.w(TAG, "Callback 'não finalizada' — aguardando ${PIX_CONFIRM_GRACE_MS}ms por confirmação Pix")
        val runnable = Runnable {
            delayedError.set(null)
            parkedPaymentError.set(null)
            if (!deferred.isCompleted) {
                deferred.complete(error)
            }
        }
        delayedError.set(runnable)
        mainHandler.postDelayed(runnable, PIX_CONFIRM_GRACE_MS)
    }

    private fun finish(
        deferred: CompletableDeferred<CieloDeeplinkResponse>,
        result: CieloDeeplinkResponse,
    ) {
        clearDelayedError()
        deferred.complete(result)
    }

    private fun clearDelayedError() {
        delayedError.getAndSet(null)?.let { mainHandler.removeCallbacks(it) }
    }

    private fun opFromHost(host: String?): CieloLioOp? = when (host) {
        "print" -> CieloLioOp.PRINT
        "payment" -> CieloLioOp.PAYMENT
        else -> null
    }

    private fun parseResponse(base64: String, uriResponseCode: Int?): CieloDeeplinkResponse {
        return try {
            val raw = decodeCallback(base64)
            Log.i(TAG, "Callback JSON: ${raw.take(800)} uriResponseCode=$uriResponseCode")
            when (val parsed = CieloCallbackParser.parseJson(raw, uriResponseCode)) {
                is CieloParsedCallback.Approved -> CieloDeeplinkResponse.Success(parsed.json)
                is CieloParsedCallback.Rejected -> CieloDeeplinkResponse.Error(parsed.code, parsed.reason)
            }
        } catch (e: Exception) {
            CieloDeeplinkResponse.Error(code = -1, reason = e.message ?: "JSON inválido da Cielo.")
        }
    }

    private fun decodeCallback(base64: String): String {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        if (utf8.trimStart().startsWith("{") || utf8.trimStart().startsWith("[")) return utf8
        val latin1 = String(bytes, Charsets.ISO_8859_1)
        if (latin1.trimStart().startsWith("{") || latin1.trimStart().startsWith("[")) return latin1
        return utf8
    }

    private const val TAG = "CieloDeeplink"
}

sealed class CieloDeeplinkResponse {
    data class Success(val json: JSONObject) : CieloDeeplinkResponse()
    data class Error(val code: Int, val reason: String) : CieloDeeplinkResponse()
}
