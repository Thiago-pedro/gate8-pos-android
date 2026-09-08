package br.com.gate8.pos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/** Gera PNG monocromático do QR para impressão térmica (Cielo `PRINT_IMAGE`). */
object ThermalQrBitmap {
    private const val TAG = "ThermalQrBitmap"
    private const val SIZE_PX = 260
    private const val CIELO_IMAGE_DIR = "/storage/emulated/0/saved_images"

    fun prepareQrPath(context: Context, payload: String): String? {
        val content = payload.trim()
        if (content.isEmpty()) return null
        return runCatching {
            val dir = File(CIELO_IMAGE_DIR).takeIf { it.exists() || it.mkdirs() }
                ?: context.cacheDir
            val outFile = File(dir, "gate8_ticket_qr_${content.hashCode().toUInt()}.png")
            val bitmap = encodeQr(content, SIZE_PX) ?: return null
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            if (!outFile.exists() || outFile.length() == 0L) return null
            outFile.absolutePath
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
