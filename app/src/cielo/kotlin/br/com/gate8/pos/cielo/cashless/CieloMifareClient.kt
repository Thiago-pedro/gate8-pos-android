package br.com.gate8.pos.cielo.cashless

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.tech.MifareClassic
import android.os.Build
import android.util.Log
import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.cashless.CashlessCardSnapshot
import br.com.gate8.pos.cashless.CashlessOperationException
import br.com.gate8.pos.cashless.Gate8CashlessBalanceCodec
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cashless Mifare via serviço `cielo.lio.cashless` (Broadcast callback).
 *
 * Cielo documenta suporte a **Mifare Classic 1K** apenas.
 * Saldo Gate8: setor 1, bloco 0 (formato [Gate8CashlessBalanceCodec]).
 */
class CieloMifareClient(
    private val app: Application,
) : CashlessCardGateway {

    private val mutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)
    private val writing = AtomicBoolean(false)
    private val inFlight = AtomicReference<CompletableDeferred<MifareResult>?>(null)

    override fun cancel() {
        if (writing.get()) return
        cancelRequested.set(true)
        inFlight.getAndSet(null)?.completeExceptionally(PaymentCancelledException())
        val host = CieloActivityHolder.get() ?: return
        runCatching {
            val intent = Intent("cielo.lio.cashless.mifare.DEACTIVATE").apply {
                setPackage(CASHLESS_PACKAGE)
                putExtra("cbType", 'B')
                putExtra("cbPackage", app.packageName)
                putExtra("cbAction", "DEACTIVATE_CALLBACK")
            }
            startCashlessService(host, intent)
        }
    }

    private fun beginOperation() {
        cancelRequested.set(false)
        writing.set(false)
    }

    private fun ensureNotCancelled() {
        if (cancelRequested.get()) throw PaymentCancelledException()
    }

    override suspend fun readCard(): CashlessCardSnapshot = mutex.withLock {
        beginOperation()
        withContext(Dispatchers.Main.immediate) {
            try {
                val uid = detect()
                val block = readBalanceBlockConfirmed()
                snapshot(uid, block)
            } finally {
                runCatching { deactivate() }
            }
        }
    }

    override suspend fun topUp(amountReais: Double, requireUid: String?): CashlessCardSnapshot = mutex.withLock {
        beginOperation()
        require(amountReais > 0.0) { "Informe um valor maior que zero" }
        val addCents = (amountReais * 100.0).roundToInt()
        require(addCents > 0) { "Informe um valor maior que zero" }
        withContext(Dispatchers.Main.immediate) {
            try {
                val uid = detect()
                val uidHex = Gate8CashlessBalanceCodec.uidToHex(uid)
                if (requireUid != null && !uidHex.equals(requireUid, ignoreCase = true)) {
                    throw CashlessOperationException(
                        "WRONG_CARD",
                        "Cartão diferente do cadastrado ($requireUid). Aproxime o cartão certo.",
                    )
                }
                val current = readBalanceBlockConfirmed()
                if (Gate8CashlessBalanceCodec.isBlocked(current)) {
                    throw CashlessOperationException(
                        "BLOCKED",
                        "Este cartão está bloqueado. Use Bloquear cartão ou Cartão perdido para transferir.",
                    )
                }
                val currentCents = Gate8CashlessBalanceCodec.readCents(current) ?: 0
                val nextCents = currentCents + addCents
                val encoded = Gate8CashlessBalanceCodec.encode(nextCents, blocked = false)
                val written = writeBalanceBlockConfirmed(encoded)
                snapshot(
                    uid,
                    written,
                    message = "Crédito de R$ ${"%.2f".format(amountReais)} adicionado",
                )
            } finally {
                runCatching { deactivate() }
            }
        }
    }

    override suspend fun writeBalance(
        amountReais: Double,
        blocked: Boolean,
        rejectUid: String?,
        requireUid: String?,
    ): CashlessCardSnapshot =
        mutex.withLock {
            beginOperation()
            require(amountReais >= 0.0) { "Saldo inválido" }
            val cents = (amountReais * 100.0).roundToInt().coerceAtLeast(0)
            withContext(Dispatchers.Main.immediate) {
                try {
                    val uid = detect()
                    val uidHex = Gate8CashlessBalanceCodec.uidToHex(uid)
                    if (rejectUid != null && uidHex.equals(rejectUid, ignoreCase = true)) {
                        throw CashlessOperationException(
                            "SAME_CARD",
                            "Você aproximou o mesmo cartão. Use um cartão NOVO.",
                        )
                    }
                    if (requireUid != null && !uidHex.equals(requireUid, ignoreCase = true)) {
                        throw CashlessOperationException(
                            "WRONG_CARD",
                            "Cartão diferente do esperado ($requireUid). Aproxime o cartão certo.",
                        )
                    }
                    val encoded = Gate8CashlessBalanceCodec.encode(cents, blocked = blocked)
                    val written = writeBalanceBlockConfirmed(encoded)
                    snapshot(uid, written)
                } finally {
                    runCatching { deactivate() }
                }
            }
        }

    override suspend fun wipeCard(requireUid: String?): CashlessCardSnapshot =
        mutex.withLock {
            beginOperation()
            withContext(Dispatchers.Main.immediate) {
                try {
                    val uid = detect()
                    val uidHex = Gate8CashlessBalanceCodec.uidToHex(uid)
                    if (requireUid != null && !uidHex.equals(requireUid, ignoreCase = true)) {
                        throw CashlessOperationException(
                            "WRONG_CARD",
                            "Cartão diferente do esperado ($requireUid). Aproxime o cartão certo.",
                        )
                    }
                    val blank = ByteArray(16) { 0 }
                    val written = writeBalanceBlockConfirmed(blank)
                    snapshot(
                        uid,
                        written,
                        message = "Cartão limpo · em branco para novo cadastro",
                    )
                } finally {
                    runCatching { deactivate() }
                }
            }
        }

    override suspend fun debit(amountReais: Double): CashlessCardSnapshot = mutex.withLock {
        beginOperation()
        require(amountReais > 0.0) { "Informe um valor maior que zero" }
        val debitCents = (amountReais * 100.0).roundToInt()
        require(debitCents > 0) { "Informe um valor maior que zero" }
        withContext(Dispatchers.Main.immediate) {
            try {
                val uid = detect()
                val current = readBalanceBlockConfirmed()
                if (!Gate8CashlessBalanceCodec.isGate8(current)) {
                    throw CashlessOperationException(
                        "EMPTY",
                        "Cartão sem saldo Gate8. Faça uma recarga antes de pagar.",
                    )
                }
                if (Gate8CashlessBalanceCodec.isBlocked(current)) {
                    throw CashlessOperationException(
                        "BLOCKED",
                        "Este cartão está bloqueado e não pode pagar.",
                    )
                }
                val currentCents = Gate8CashlessBalanceCodec.readCents(current) ?: 0
                if (currentCents < debitCents) {
                    val have = currentCents / 100.0
                    throw CashlessOperationException(
                        "INSUFFICIENT",
                        "Saldo insuficiente. Disponível R$ ${"%.2f".format(have)}; " +
                            "necessário R$ ${"%.2f".format(amountReais)}.",
                    )
                }
                val nextCents = currentCents - debitCents
                val encoded = Gate8CashlessBalanceCodec.encode(nextCents, blocked = false)
                val written = writeBalanceBlockConfirmed(encoded)
                snapshot(
                    uid,
                    written,
                    message = "Pago R$ ${"%.2f".format(amountReais)} · " +
                        "saldo R$ ${"%.2f".format(nextCents / 100.0)}",
                )
            } finally {
                runCatching { deactivate() }
            }
        }
    }

    /**
     * Lê só o bloco de saldo (setor 1, bloco 0). Nunca cai no setor 0:
     * o bloco 0 do setor 0 é o UID de fábrica e aparecia como saldo R$ 0,00.
     *
     * Aceita o valor quando duas leituras seguidas são idênticas e o bloco é Gate8.
     * Bloco virgem (0x00/0xFF) só vale depois de uma terceira leitura igual —
     * uma resposta vazia da Cielo não pode zerar um cartão com saldo.
     */
    private suspend fun readBalanceBlockConfirmed(): ByteArray {
        var lastError: Exception? = null
        repeat(BALANCE_READ_ATTEMPTS) { attempt ->
            ensureNotCancelled()
            if (attempt > 0) kotlinx.coroutines.delay(RETRY_GAP_MS)
            if (!authenticateBalanceSector()) {
                lastError = CashlessOperationException(
                    "AUTH",
                    "Não autentiquei o setor de saldo. Mantenha o cartão encostado e tente de novo.",
                )
                return@repeat
            }
            val first = try {
                readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
            } catch (e: CashlessOperationException) {
                lastError = e
                return@repeat
            }
            val second = try {
                readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
            } catch (e: CashlessOperationException) {
                lastError = e
                return@repeat
            }
            if (!first.contentEquals(second)) {
                Log.w(
                    TAG,
                    "leituras diferentes attempt=$attempt " +
                        "${Gate8CashlessBalanceCodec.toHex(first)} vs " +
                        Gate8CashlessBalanceCodec.toHex(second),
                )
                lastError = unstableRead()
                return@repeat
            }
            if (Gate8CashlessBalanceCodec.isGate8(first)) {
                Log.i(TAG, "saldo confirmado ${Gate8CashlessBalanceCodec.toHex(first)}")
                return first
            }
            if (Gate8CashlessBalanceCodec.isVirgin(first)) {
                if (!authenticateBalanceSector()) {
                    lastError = CashlessOperationException(
                        "AUTH",
                        "Não autentiquei o setor de saldo. Mantenha o cartão encostado e tente de novo.",
                    )
                    return@repeat
                }
                val third = try {
                    readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
                } catch (e: CashlessOperationException) {
                    lastError = e
                    return@repeat
                }
                if (third.contentEquals(first)) {
                    Log.i(TAG, "cartão virgem confirmado")
                    return first
                }
                Log.w(TAG, "zero não confirmou attempt=$attempt")
                lastError = unstableRead()
                return@repeat
            }
            Log.w(TAG, "bloco ilegível attempt=$attempt ${Gate8CashlessBalanceCodec.toHex(first)}")
            lastError = unstableRead()
        }
        throw lastError ?: unstableRead()
    }

    private fun unstableRead() = CashlessOperationException(
        "UNSTABLE",
        "Não confirmei o saldo do cartão. Mantenha ele encostado e tente de novo.",
    )

    /** Grava o bloco de saldo e só devolve quando a leitura de volta é idêntica. */
    private suspend fun writeBalanceBlockConfirmed(data: ByteArray): ByteArray {
        writing.set(true)
        var lastError: Exception? = null
        try {
        repeat(WRITE_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                kotlinx.coroutines.delay(RETRY_GAP_MS)
                if (!authenticateBalanceSector()) {
                    lastError = CashlessOperationException(
                        "AUTH",
                        "Não autentiquei o setor de saldo. Mantenha o cartão encostado e tente de novo.",
                    )
                    return@repeat
                }
            } else if (!authenticateBalanceSector()) {
                lastError = CashlessOperationException(
                    "AUTH",
                    "Não autentiquei o setor de saldo. Mantenha o cartão encostado e tente de novo.",
                )
                return@repeat
            }
            try {
                writeBlock(BALANCE_SECTOR, BALANCE_BLOCK, data)
                val read = readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
                if (read.contentEquals(data)) {
                    Log.i(TAG, "gravação confirmada ${Gate8CashlessBalanceCodec.toHex(read)}")
                    return read
                }
                Log.w(
                    TAG,
                    "gravação não confirmou attempt=$attempt " +
                        "esperado=${Gate8CashlessBalanceCodec.toHex(data)} " +
                        "lido=${Gate8CashlessBalanceCodec.toHex(read)}",
                )
                lastError = CashlessOperationException(
                    "VERIFY",
                    "A gravação do saldo não confirmou. Mantenha o cartão encostado e tente de novo.",
                )
            } catch (e: CashlessOperationException) {
                lastError = e
            }
        }
        throw lastError ?: CashlessOperationException(
            "VERIFY",
            "A gravação do saldo não confirmou. Mantenha o cartão encostado e tente de novo.",
        )
        } finally {
            writing.set(false)
        }
    }

    private suspend fun authenticateBalanceSector(): Boolean {
        if (authenticateBestEffort(BALANCE_SECTOR, 'A') != null) return true
        return authenticateBestEffort(BALANCE_SECTOR, 'B') != null
    }

    private suspend fun authenticateBestEffort(
        sector: Byte,
        keyType: Char = 'A',
    ): Unit? = runCatching {
        authenticate(sector, keyType)
    }.onFailure { e ->
        Log.w(TAG, "AUTH falhou sector=$sector key=$keyType: ${e.message}")
    }.getOrNull()

    private fun snapshot(
        uid: ByteArray,
        block: ByteArray,
        message: String? = null,
    ): CashlessCardSnapshot {
        val cents = Gate8CashlessBalanceCodec.readCents(block)
        val isGate8 = Gate8CashlessBalanceCodec.isGate8(block)
        val virgin = Gate8CashlessBalanceCodec.isVirgin(block)
        val blocked = Gate8CashlessBalanceCodec.isBlocked(block)
        val confirmed = Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(block)
        val balanceReais = when {
            cents != null -> cents / 100.0
            virgin -> 0.0
            else -> null
        }
        return CashlessCardSnapshot(
            uidHex = Gate8CashlessBalanceCodec.uidToHex(uid),
            balanceReais = balanceReais,
            isGate8Format = isGate8,
            isBlocked = blocked,
            balanceConfirmed = confirmed,
            blockHex = Gate8CashlessBalanceCodec.toHex(block),
            blockAscii = Gate8CashlessBalanceCodec.toAscii(block),
            message = message ?: when {
                !confirmed -> null
                blocked -> "Cartão bloqueado · saldo R$ ${"%.2f".format(balanceReais ?: 0.0)}"
                isGate8 -> null
                else -> "Cartão em branco · saldo zerado."
            },
        )
    }

    private suspend fun detect(): ByteArray {
        // Limpa sessão anterior — R10/Parameter error costuma aparecer com leitor “preso”.
        runCatching { deactivate() }
        Log.i(TAG, "DETECT — aproxime o cartão Mifare")
        val result = call(
            action = "cielo.lio.cashless.mifare.DETECT",
            cbAction = "DETECT_CALLBACK",
            timeoutMs = DETECT_TIMEOUT_MS,
        ) {}
        val uid = result.data
            ?: throw CashlessOperationException(result.code, humanize(result.code, result.detail, "DETECT"))
        Log.i(TAG, "DETECT ok uid=${Gate8CashlessBalanceCodec.uidToHex(uid)}")
        return uid
    }

    private suspend fun authenticate(sector: Byte, keyType: Char = 'A') {
        Log.i(TAG, "AUTHENTICATE sector=$sector keyType=$keyType")
        call(
            action = "cielo.lio.cashless.mifare.AUTHENTICATE",
            cbAction = "AUTHENTICATE_CALLBACK",
            timeoutMs = OP_TIMEOUT_MS,
        ) {
            putExtra("sector", sector)
            putExtra("key", MifareClassic.KEY_DEFAULT)
            putExtra("keyType", keyType)
        }
    }

    private suspend fun readBlock(sector: Byte, block: Byte): ByteArray {
        Log.i(TAG, "READ sector=$sector block=$block")
        val result = call(
            action = "cielo.lio.cashless.mifare.READ",
            cbAction = "AUTH_CALLBACK_READ",
            timeoutMs = OP_TIMEOUT_MS,
        ) {
            putExtra("sector", sector)
            putExtra("block", block)
        }
        val data = result.data
            ?: throw CashlessOperationException(
                "SHORT",
                "Leitura sem dados. Mantenha o cartão encostado e tente de novo.",
            )
        if (data.size < 16) {
            throw CashlessOperationException(
                "SHORT",
                "Leitura incompleta do cartão. Mantenha ele encostado e tente de novo.",
            )
        }
        return data.copyOf(16)
    }

    private suspend fun writeBlock(sector: Byte, block: Byte, data: ByteArray) {
        require(data.size == 16) { "Bloco do cartão deve ter 16 bytes" }
        Log.i(TAG, "WRITE sector=$sector block=$block")
        call(
            action = "cielo.lio.cashless.mifare.WRITE",
            cbAction = "AUTH_CALLBACK_WRITE",
            timeoutMs = OP_TIMEOUT_MS,
        ) {
            putExtra("sector", sector)
            putExtra("block", block)
            putExtra("data", data)
        }
    }

    private suspend fun deactivate() {
        Log.i(TAG, "DEACTIVATE")
        runCatching {
            call(
                action = "cielo.lio.cashless.mifare.DEACTIVATE",
                cbAction = "DEACTIVATE_CALLBACK",
                timeoutMs = OP_TIMEOUT_MS,
            ) {}
        }
    }

    private data class MifareResult(val code: String, val detail: String, val data: ByteArray?)

    private suspend fun call(
        action: String,
        cbAction: String,
        timeoutMs: Long,
        configure: Intent.() -> Unit,
    ): MifareResult {
        ensureNotCancelled()
        val deferred = CompletableDeferred<MifareResult>()
        val pending = AtomicReference(deferred)
        inFlight.set(deferred)
        if (cancelRequested.get()) {
            deferred.completeExceptionally(PaymentCancelledException())
        }
        // Broadcast antigo (sticky) chega no registerReceiver, antes do startService.
        val acceptCallbacks = AtomicBoolean(false)
        // Actions oficiais da doc Cielo (sem UUID — alguns firmwares ignoram cbAction custom).
        val replyAction = cbAction

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (!acceptCallbacks.get()) {
                    Log.w(TAG, "callback antecipado ignorado action=${intent.action}")
                    return
                }
                val code = intent.getStringExtra("result").orEmpty()
                val detail = intent.getStringExtra("detail").orEmpty()
                val data = intent.getByteArrayExtra("data")
                Log.i(
                    TAG,
                    "callback action=${intent.action} result=$code detail=$detail " +
                        "dataLen=${data?.size} extras=${intent.extras?.keySet()}",
                )
                val d = pending.getAndSet(null) ?: return
                if (code == "R00") {
                    d.complete(MifareResult(code, detail, data))
                } else {
                    d.completeExceptionally(
                        CashlessOperationException(
                            code.ifBlank { "ERR" },
                            humanize(code, detail, action.substringAfterLast('.')),
                        ),
                    )
                }
            }
        }

        val filter = IntentFilter(replyAction)
        val host = foregroundContext()
        registerReceiver(host, receiver, filter)
        try {
            val intent = Intent(action).apply {
                setPackage(CASHLESS_PACKAGE)
                putExtra("cbType", 'B')
                putExtra("cbPackage", app.packageName)
                putExtra("cbAction", replyAction)
                configure()
            }
            acceptCallbacks.set(true)
            startCashlessService(host, intent)
            return try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                if (cancelRequested.get()) throw PaymentCancelledException()
                throw CashlessOperationException(
                    "TIMEOUT",
                    "Tempo esgotado. Aproxime o cartão cashless e tente de novo.",
                )
            }
        } catch (e: IllegalStateException) {
            pending.getAndSet(null)?.cancel()
            Log.e(TAG, "Falha ao iniciar serviço cashless", e)
            throw CashlessOperationException(
                "BG",
                "A Cielo bloqueou o leitor (app em background). Deixe o Gate8 aberto na tela e tente de novo.",
            )
        } catch (e: Exception) {
            pending.getAndSet(null)?.cancel()
            throw e
        } finally {
            inFlight.compareAndSet(deferred, null)
            runCatching { host.unregisterReceiver(receiver) }
        }
    }

    private fun humanize(code: String, detail: String, op: String): String {
        val d = detail.trim()
        val lower = d.lowercase()
        val opLabel = when (op.uppercase()) {
            "DETECT" -> "detecção do cartão"
            "AUTHENTICATE", "AUTH" -> "autenticação"
            "READ" -> "leitura"
            "WRITE" -> "gravação"
            else -> op
        }
        if (code.equals("R10", ignoreCase = true) || lower.contains("parameter error")) {
            return "Erro ao iniciar a $opLabel no leitor. " +
                "Deixe o Gate8 aberto na tela, afaste o cartão e aproxime de novo."
        }
        if (code.equals("R01", ignoreCase = true) || lower.contains("timeout") || lower.contains("time out")) {
            return "Tempo esgotado na $opLabel. Aproxime o cartão cashless e tente de novo."
        }
        if (code.equals("R02", ignoreCase = true) || lower.contains("cancel")) {
            return "Operação cancelada na $opLabel."
        }
        if (lower.contains("internal") || lower == "internal error" || code.equals("internal", true)) {
            return "Erro interno da Cielo na $opLabel. " +
                "Quase sempre o cartão não é compatível com o cashless Gate8 " +
                "ou a chave do setor é diferente da esperada. Código: ${code.ifBlank { "—" }}"
        }
        if (d.isNotBlank()) {
            val translated = when {
                lower.contains("parameter") -> "Erro de parâmetro no leitor"
                lower.contains("no card") || lower.contains("card not") -> "Nenhum cartão detectado"
                lower.contains("auth") -> "Falha de autenticação no cartão"
                else -> d
            }
            return "$translated ($opLabel/${code.ifBlank { "—" }})"
        }
        return "Operação cashless falhou na $opLabel (código ${code.ifBlank { "—" }})"
    }

    private fun foregroundContext(): Context =
        CieloActivityHolder.get()
            ?: throw CashlessOperationException(
                "NO_UI",
                "Abra a tela Cashless no Gate8 (em primeiro plano) e tente de novo.",
            )

    private fun startCashlessService(context: Context, intent: Intent) {
        try {
            context.startService(intent)
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.w(TAG, "startService bloqueado — tentando startForegroundService", e)
                context.startForegroundService(intent)
            } else {
                throw e
            }
        }
    }

    private fun registerReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    companion object {
        private const val TAG = "CieloMifare"
        private const val CASHLESS_PACKAGE = "cielo.lio.cashless"
        private const val BALANCE_SECTOR: Byte = 1
        private const val BALANCE_BLOCK: Byte = 0
        private const val DETECT_TIMEOUT_MS = 45_000L
        private const val OP_TIMEOUT_MS = 15_000L
        private const val BALANCE_READ_ATTEMPTS = 3
        private const val WRITE_ATTEMPTS = 2
        private const val RETRY_GAP_MS = 60L
    }
}
