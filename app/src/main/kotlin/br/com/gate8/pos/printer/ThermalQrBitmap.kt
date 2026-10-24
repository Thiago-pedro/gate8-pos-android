package br.com.gate8.pos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Gera PNG monocromático do QR para impressão térmica (Cielo `PRINT_IMAGE`). */
object ThermalQrBitmap {
    private const val TAG = "ThermalQrBitmap"
    private const val SIZE_PX = 260

    fun prepareQrPath(context: Context, payload: String): String? {
        val content = payload.trim()
        if (content.isEmpty()) return null
        return runCatching {
            val bitmap = encodeQr(content, SIZE_PX) ?: return null
            val path = PrintImageFiles.writePng(
                "gate8_ticket_qr_${content.hashCode().toUInt()}.png",
                bitmap,
            )
            bitmap.recycle()
            path
        }.onFailure { Log.w(TAG, "QR não gerado", it) }.getOrNull()
    }

    private fun encodeQr(content: String, size: Int): Bitmap? {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
