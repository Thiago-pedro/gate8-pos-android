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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import stone.application.enums.ReceiptType
import stone.application.interfaces.StoneCallbackInterface
import stone.database.transaction.TransactionDAO
import stone.database.transaction.TransactionObject

class StonePosPrinterLive : StonePosPrinter {
    override val isAvailable: Boolean = true

    /**
     * Fila sequencial de impressão: cada trabalho só começa quando o anterior
     * termina (ou estoura o timeout). Sem isso, as várias chamadas assíncronas
     * de `execute()` saem embaralhadas (via lojista, via cliente, comprovante e
     * fichas misturados no mesmo rolo).
     */
    private val printQueue = Executors.newSingleThreadExecutor()

    /**
     * Enfileira um trabalho de impressão. O `block` é executado na UI thread
     * (padrão dos providers Stone) e deve, ao final, chamar `latch.countDown()`
     * via callback (use [callback]) ou diretamente quando não houver o que imprimir.
     */
    private fun enqueue(activity: Activity, label: String, block: (CountDownLatch) -> Unit) {
        printQueue.execute {
            val latch = CountDownLatch(1)
            activity.runOnUiThread {
                runCatching { block(latch) }.onFailure {
                    Log.e(TAG, "$label: falha ao imprimir", it)
                    latch.countDown()
                }
            }
            runCatching { latch.await(PRINT_TIMEOUT_SEC, TimeUnit.SECONDS) }
        }
    }

    private fun callback(label: String, latch: CountDownLatch) = object : StoneCallbackInterface {
        override fun onSuccess() {
            Log.i(TAG, "$label: impressão OK")
            latch.countDown()
        }

        override fun onError() {
            Log.w(TAG, "$label: erro na impressão")
            latch.countDown()
        }
    }

    override fun printLines(
        activity: Activity,
        lines: List<String>,
        bitmap: Bitmap?,
        logoScale: Float,
    ) {
        enqueue(activity, "PosPrintProvider") { latch ->
            val provider = PosPrintProvider(activity)
            logoBitmap(activity)?.let { provider.addBitmap(scaleLogo(it, logoScale)) }
            lines.forEach { line -> provider.addLine(line) }
            bitmap?.let { provider.addBitmap(scaleForPrinter(it)) }
            provider.connectionCallback = callback("PosPrintProvider", latch)
            provider.execute()
        }
    }

    /** Logo Gate8 do cabecalho, convertida para preto puro: o "8" azul saia fraco na termica. */
    private fun logoBitmap(activity: Activity): Bitmap? = runCatching {
        val source = BitmapFactory.decodeResource(activity.resources, R.drawable.logo_gate8_header)
            ?: return null
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val alpha = (c ushr 24) and 0xFF
            if (alpha < 128) {
                pixels[i] = Color.WHITE
                continue
            }
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            pixels[i] = if (luminance < BLACK_THRESHOLD) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.onFailure { Log.w(TAG, "logoBitmap", it) }.getOrNull()

    override fun printCardReceipt(
        activity: Activity,
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
    ) {
        enqueue(activity, "PosPrintReceiptProvider") { latch ->
            val transaction = resolveTransaction(activity, transactionId, nsu)
            if (transaction == null) {
                Log.w(TAG, "Transação não encontrada para impressão (itk=$transactionId nsu=$nsu)")
                latch.countDown()
                return@enqueue
            }
            val type = if (merchantCopy) ReceiptType.MERCHANT else ReceiptType.CLIENT
            val provider = PosPrintReceiptProvider(activity, transaction, type)
            provider.connectionCallback = callback("PosPrintReceiptProvider", latch)
            provider.execute()
        }
    }

    override fun printTicket(
        activity: Activity,
        topLines: List<String>,
        qrContent: String,
        bottomLines: List<String>,
    ) {
        enqueue(activity, "PosPrintProvider-ticket") { latch ->
            val provider = PosPrintProvider(activity)
            logoBitmap(activity)?.let { provider.addBitmap(scaleLogo(it, 1f)) }
            topLines.forEach { line -> provider.addLine(line) }
            provider.addLine("")
            qrBitmap(qrContent)?.let { provider.addBitmap(scaleLogo(it, QR_SCALE)) }
            provider.addLine("")
            provider.addLine("")
            bottomLines.forEach { line -> provider.addLine(line) }
            provider.connectionCallback = callback("PosPrintProvider-ticket", latch)
            provider.execute()
        }
    }

    /** Gera o QR Code do ingresso como bitmap preto/branco (conteúdo = código de validação). */
    private fun qrBitmap(content: String, sizePx: Int = 360): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.onFailure { Log.w(TAG, "qrBitmap", it) }.getOrNull()

    override fun reprintCardReceipt(activity: Activity, transactionId: String, merchantCopy: Boolean) {
        enqueue(activity, "PosReprintReceiptProvider") { latch ->
            val transaction = resolveTransaction(activity, transactionId, null)
            if (transaction == null) {
                Log.w(TAG, "Transação não encontrada para reimpressão: $transactionId")
                latch.countDown()
                return@enqueue
            }
            val dbId = transaction.idFromBase
            if (dbId == null) {
                Log.w(TAG, "idFromBase ausente para reimpressão: $transactionId")
                latch.countDown()
                return@enqueue
            }
            val type = if (merchantCopy) ReceiptType.MERCHANT else ReceiptType.CLIENT
            val provider = PosReprintReceiptProvider(activity, dbId, type)
            provider.connectionCallback = callback("PosReprintReceiptProvider", latch)
            provider.execute()
        }
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

    /**
     * Reduz a logo para uma fração da largura do papel. A impressora Stone estica
     * qualquer bitmap até a largura total da bobina; por isso, para deixar a logo
     * menor, desenhamos ela centralizada numa tela branca de largura cheia. Assim,
     * quando o SDK esticar a tela, a logo ocupa apenas `fraction` da largura.
     */
    private fun scaleLogo(source: Bitmap, fraction: Float): Bitmap {
        if (fraction >= 1f) return scaleForPrinter(source)
        val canvasW = source.width
        val logoW = (canvasW * fraction).toInt().coerceIn(1, canvasW)
        val logoH = (source.height * fraction).toInt().coerceAtLeast(1)
        val scaledLogo = Bitmap.createScaledBitmap(source, logoW, logoH, true)
        return Bitmap.createBitmap(canvasW, logoH, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaledLogo, ((canvasW - logoW) / 2).toFloat(), 0f, null)
            }
        }
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

    companion object {
        private const val TAG = "Gate8StonePrinter"
        private const val PRINTER_MAX_WIDTH_PX = 380
        private const val PRINTER_MAX_HEIGHT_PX = 595
        private const val PRINT_TIMEOUT_SEC = 30L
        private const val BLACK_THRESHOLD = 210.0
        // Fração da largura da bobina ocupada pelo QR do ingresso (centralizado).
        private const val QR_SCALE = 0.6f
    }
}
