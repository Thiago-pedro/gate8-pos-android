package br.com.gate8.pos.stone.printer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import br.com.gate8.pos.R
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
            logoBitmap(activity)?.let { provider.addBitmap(scaleForPrinter(it)) }
            lines.forEach { line -> provider.addLine(line) }
            bitmap?.let { provider.addBitmap(scaleForPrinter(it)) }
            provider.connectionCallback = logCallback("PosPrintProvider")
            provider.execute()
        }.onFailure { Log.e(TAG, "printLines", it) }
    }

    /** Logo Gate8 do cabecalho, achatada sobre fundo branco para a impressora termica. */
    private fun logoBitmap(activity: Activity): Bitmap? = runCatching {
        val source = BitmapFactory.decodeResource(activity.resources, R.drawable.logo_gate8_header)
            ?: return null
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        flattened
    }.onFailure { Log.w(TAG, "logoBitmap", it) }.getOrNull()

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
        val transaction = resolveTransaction(activity, transactionId, null) ?: run {
            Log.w(TAG, "Transação não encontrada para reimpressão: $transactionId")
            return
        }
        val dbId = transaction.idFromBase ?: run {
            Log.w(TAG, "idFromBase ausente para reimpressão: $transactionId")
            return
        }
        runCatching {
            val type = if (merchantCopy) ReceiptType.MERCHANT else ReceiptType.CLIENT
            val provider = PosReprintReceiptProvider(activity, dbId, type)
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
                ?: dao.getAllTransactions()?.firstOrNull {
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
