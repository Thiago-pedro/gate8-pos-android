package br.com.gate8.pos.cielo.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import br.com.gate8.pos.R
import java.io.File
import java.io.FileOutputStream

/** Prepara a logo Gate8 como PNG monocromático para `lio://print` (PRINT_IMAGE). */
internal object CieloLogoBitmap {
    private const val TAG = "CieloLogoBitmap"
    /** Largura alvo (~45% de 384px — largura útil da bobina Cielo). */
    private const val TARGET_WIDTH_PX = 168
    private const val CIELO_IMAGE_DIR = "/storage/emulated/0/saved_images"
    private const val FILE_NAME = "gate8_ficha_logo.png"

    fun prepareLogoPath(context: Context): String? {
        return runCatching {
            val dir = File(CIELO_IMAGE_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Não foi possível criar $CIELO_IMAGE_DIR")
                return null
            }
            val outFile = File(dir, FILE_NAME)
            val drawable = ContextCompat.getDrawable(context, R.drawable.logo_gate8_header)
                ?: return null
            val bitmap = renderMonochromeBitmap(drawable)
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (!outFile.exists() || outFile.length() == 0L) return null
            outFile.absolutePath
        }.onFailure { Log.w(TAG, "Logo Gate8 não preparada", it) }
            .getOrNull()
    }

    private fun renderMonochromeBitmap(source: Drawable): Bitmap {
        val wrapped = DrawableCompat.wrap(source.mutate())
        val width = wrapped.intrinsicWidth.coerceAtLeast(1)
        val height = wrapped.intrinsicHeight.coerceAtLeast(1)
        val scale = TARGET_WIDTH_PX.toFloat() / width
        val outW = TARGET_WIDTH_PX
        val outH = (height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wrapped.setBounds(0, 0, outW, outH)
        wrapped.draw(canvas)
        val monochrome = toThermalInk(bitmap)
        val cropped = cropToInk(monochrome)
        if (cropped !== monochrome) monochrome.recycle()
        if (wrapped is BitmapDrawable) {
            wrapped.bitmap?.recycle()
        }
        bitmap.recycle()
        return cropped
    }

    /** PNG da marca tem fundo preto — só o texto/círculo do "8" vira tinta na térmica. */
    private fun toThermalInk(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val alpha = Color.alpha(p)
            if (alpha < 128) {
                pixels[i] = Color.WHITE
                continue
            }
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            pixels[i] = if (lum < 28) Color.WHITE else Color.BLACK
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun cropToInk(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] == Color.BLACK) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (bottom < top) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}
