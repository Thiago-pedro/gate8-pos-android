package br.com.gate8.pos.stone.printer

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import br.com.stone.posandroid.providers.PosPrintProvider
import br.com.stone.posandroid.providers.PosPrintReceiptProvider
import br.com.stone.posandroid.providers.PosReprintReceiptProvider
import stone.application.enums.ReceiptType
import stone.application.interfaces.StoneCallbackInterface
import stone.database.transaction.TransactionDAO
import stone.database.transaction.TransactionObject

class StonePosPrinterLive : StonePosPrinter {
    override val isAvailable: Boolean = true

    override fun printLines(activity: Activity, lines: List<String>, bitmap: Bitmap?) {
        runCatching {
            val provider = PosPrintProvider(activity)
            lines.forEach { line -> provider.addLine(line) }
            bitmap?.let { provider.addBitmap(scaleForPrinter(it)) }
            provider.connectionCallback = logCallback("PosPrintProvider")
            provider.execute()
        }.onFailure { Log.e(TAG, "printLines", it) }
    }

    override fun printCardReceipt(
        activity: Activity,
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
    ) {
        val transaction = resolveTransaction(activity, transactionId, nsu) ?: run {
            Log.w(TAG, "Transação não encontrada para impressão (itk=$transactionId nsu=$nsu)")
            return
        }
        runCatching {
            val type = if (merchantCopy) ReceiptType.MERCHANT else ReceiptType.CLIENT
            val provider = PosPrintReceiptProvider(activity, transaction, type)
            provider.connectionCallback = logCallback("PosPrintReceiptProvider")
            provider.execute()
        }.onFailure { Log.e(TAG, "printCardReceipt", it) }
    }

    override fun reprintCardReceipt(activity: Activity, transactionId: String, merchantCopy: Boolean) {
        runCatching {
            val type = if (merchantCopy) ReceiptType.MERCHANT else ReceiptType.CLIENT
            val provider = PosReprintReceiptProvider(activity, transactionId, type)
            provider.connectionCallback = logCallback("PosReprintReceiptProvider")
            provider.execute()
        }.onFailure { Log.e(TAG, "reprintCardReceipt", it) }
    }

    private fun resolveTransaction(
        activity: Activity,
        transactionId: String?,
        nsu: String?,
    ): TransactionObject? {
        val dao = TransactionDAO(activity)
        transactionId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
            dao.findTransactionWithAtk(id)
                ?: dao.allTransactions?.firstOrNull {
                    it.initiatorTransactionKey.equals(id, ignoreCase = true)
                }
                ?.let { return it }
        }
        nsu?.trim()?.takeIf { it.isNotEmpty() }?.let { atk ->
            dao.findTransactionWithAtk(atk)?.let { return it }
        }
        return null
    }

    private fun scaleForPrinter(source: Bitmap): Bitmap {
        val maxW = PRINTER_MAX_WIDTH_PX
        val maxH = PRINTER_MAX_HEIGHT_PX
        if (source.width <= maxW && source.height <= maxH) return source
        val scale = minOf(maxW.toFloat() / source.width, maxH.toFloat() / source.height, 1f)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun logCallback(label: String) = object : StoneCallbackInterface {
        override fun onSuccess() {
            Log.i(TAG, "$label: impressão OK")
        }

        override fun onError() {
            Log.w(TAG, "$label: erro na impressão")
        }
    }

    companion object {
        private const val TAG = "Gate8StonePrinter"
        private const val PRINTER_MAX_WIDTH_PX = 380
        private const val PRINTER_MAX_HEIGHT_PX = 595
    }
}
