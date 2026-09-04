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
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    override suspend fun readCard(): CashlessCardSnapshot = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            try {
                val uid = detect()
                val block = readBalanceBlockOrNull()
                if (block != null) {
                    snapshot(uid, block)
                } else {
                    CashlessCardSnapshot(
                        uidHex = Gate8CashlessBalanceCodec.uidToHex(uid),
                        balanceReais = null,
                        isGate8Format = false,
                        isBlocked = false,
                        blockHex = "—",
                        blockAscii = "—",
                        message = "Cartão detectado (UID ${Gate8CashlessBalanceCodec.uidToHex(uid)}), " +
                            "mas não autenticou com a chave padrão. " +
                            "A Cielo só lê Mifare Classic 1K. Se for Ultralight, DESFire, NTAG ou " +
                            "cartão com chave customizada, não serve neste fluxo.",
                    )
                }
            } finally {
                runCatching { deactivate() }
            }
        }
    }

    override suspend fun topUp(amountReais: Double, requireUid: String?): CashlessCardSnapshot = mutex.withLock {
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
                authenticateBestEffort(BALANCE_SECTOR)
                    ?: throw CashlessOperationException(
                        "AUTH",
                        "Não autenticou o setor de saldo. Precisa ser Mifare Classic 1K com chave padrão (FF..FF).",
                    )
                val current = readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
                if (Gate8CashlessBalanceCodec.isBlocked(current)) {
                    throw CashlessOperationException(
                        "BLOCKED",
                        "Este cartão está bloqueado. Use Bloquear cartão ou Cartão perdido para transferir.",
                    )
                }
                val currentCents = Gate8CashlessBalanceCodec.readCents(current) ?: 0
                val nextCents = currentCents + addCents
                val encoded = Gate8CashlessBalanceCodec.encode(nextCents, blocked = false)
                writeBlock(BALANCE_SECTOR, BALANCE_BLOCK, encoded)
                val written = readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
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
                    authenticateBestEffort(BALANCE_SECTOR)
                        ?: throw CashlessOperationException(
                            "AUTH",
                            "Não autenticou o setor de saldo. Precisa ser Mifare Classic 1K com chave padrão (FF..FF).",
                        )
                    val encoded = Gate8CashlessBalanceCodec.encode(cents, blocked = blocked)
                    writeBlock(BALANCE_SECTOR, BALANCE_BLOCK, encoded)
                    val written = readBlock(BALANCE_SECTOR, BALANCE_BLOCK)
                    snapshot(uid, written)
                } finally {
                    runCatching { deactivate() }
                }
            }
        }

    /** Tenta setor 1 depois setor 0; key A depois key B. */
    private suspend fun readBalanceBlockOrNull(): ByteArray? {
        val attempts = listOf(
            BALANCE_SECTOR to 'A',
            BALANCE_SECTOR to 'B',
            0.toByte() to 'A',
            0.toByte() to 'B',
        )
        for ((sector, keyType) in attempts) {
            val ok = authenticateBestEffort(sector, keyType)
            if (ok == null) continue
            return runCatching {
                readBlock(sector, if (sector == BALANCE_SECTOR) BALANCE_BLOCK else 0)
            }.getOrNull()
        }
        return null
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
        val blocked = Gate8CashlessBalanceCodec.isBlocked(block)
        return CashlessCardSnapshot(
            uidHex = Gate8CashlessBalanceCodec.uidToHex(uid),
            balanceReais = cents?.let { it / 100.0 },
            isGate8Format = isGate8,
            isBlocked = blocked,
            blockHex = Gate8CashlessBalanceCodec.toHex(block),
            blockAscii = Gate8CashlessBalanceCodec.toAscii(block),
            message = message ?: when {
                blocked -> "Cartão bloqueado · saldo R$ ${"%.2f".format((cents ?: 0) / 100.0)}"
                isGate8 -> null
                else -> "Cartão em branco · saldo zerado."
            },
        )
    }

    private suspend fun detect(): ByteArray {
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
            ?: throw CashlessOperationException(result.code, humanize(result.code, result.detail, "READ"))
        return if (data.size >= 16) data.copyOf(16) else data.copyOf(16)
    }

    private suspend fun writeBlock(sector: Byte, block: Byte, data: ByteArray) {
        require(data.size == 16) { "Bloco Mifare deve ter 16 bytes" }
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
        val deferred = CompletableDeferred<MifareResult>()
        val pending = AtomicReference(deferred)
        // Actions oficiais da doc Cielo (sem UUID — alguns firmwares ignoram cbAction custom).
        val replyAction = cbAction

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
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
            startCashlessService(host, intent)
            return try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                throw CashlessOperationException(
                    "TIMEOUT",
                    "Tempo esgotado. Aproxime o cartão Mifare Classic 1K e tente de novo.",
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
            runCatching { host.unregisterReceiver(receiver) }
        }
    }

    private fun humanize(code: String, detail: String, op: String): String {
        val d = detail.trim()
        val lower = d.lowercase()
        if (lower.contains("internal") || lower == "internal error" || code.equals("internal", true)) {
            return "Erro interno da Cielo no $op. " +
                "Quase sempre o cartão não é Mifare Classic 1K (ex.: Ultralight, DESFire, NTAG) " +
                "ou a chave do setor é diferente da padrão. Código: ${code.ifBlank { "—" }}"
        }
        if (d.isNotBlank()) return "$d ($op/$code)"
        return "Operação Mifare falhou ($op/$code)"
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
    }
}
