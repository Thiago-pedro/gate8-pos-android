package br.com.gate8.pos.cielo.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import br.com.gate8.pos.printer.Gate8ReceiptTextBuilder
import br.com.gate8.pos.printer.PrintImageFiles

/**
 * Cabeçalho do ingresso desenhado na largura da bobina (384 px).
 * A razão social é reduzida até caber numa linha, sem quebra.
 */
internal object CieloTicketHeaderBitmap {
    private const val TAG = "CieloTicketHeader"
    private const val PAPER_PX = 384
    private const val PAD_X = 4
    private const val FILE_NAME = "gate8_ticket_header.png"

    fun preparePath(context: Context): String? {
        return runCatching {
            val bitmap = render()
            val path = PrintImageFiles.writePng(FILE_NAME, bitmap)
            bitmap.recycle()
            path
        }.onFailure { Log.w(TAG, "Cabeçalho do ingresso não preparado", it) }
            .getOrNull()
    }

    private fun render(): Bitmap {
        val maxWidth = (PAPER_PX - PAD_X * 2).toFloat()
        val legalPaint = textPaint(Typeface.BOLD, 22f)
        fitOneLine(legalPaint, Gate8ReceiptTextBuilder.TICKET_ISSUER_LEGAL, maxWidth)
        val brandPaint = textPaint(Typeface.BOLD, legalPaint.textSize)
        val cnpjPaint = textPaint(Typeface.BOLD, legalPaint.textSize * 0.9f)
        fitOneLine(cnpjPaint, Gate8ReceiptTextBuilder.TICKET_ISSUER_CNPJ, maxWidth)

        val brandHeight = lineHeight(brandPaint)
        val legalHeight = lineHeight(legalPaint)
        val cnpjHeight = lineHeight(cnpjPaint)
        val gap = 3
        val height = brandHeight + gap + legalHeight + gap + cnpjHeight + 6
        val bitmap = Bitmap.createBitmap(PAPER_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var top = 0f
        top = drawCentered(canvas, Gate8ReceiptTextBuilder.TICKET_ISSUER_BRAND, brandPaint, top)
        top += gap
        top = drawCentered(canvas, Gate8ReceiptTextBuilder.TICKET_ISSUER_LEGAL, legalPaint, top)
        top += gap
        drawCentered(canvas, Gate8ReceiptTextBuilder.TICKET_ISSUER_CNPJ, cnpjPaint, top)
        return toSolidInk(bitmap)
    }

    private fun textPaint(style: Int, size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            textSize = size
        }

    /** Diminui a fonte até o texto inteiro caber na largura, sempre em uma linha. */
    private fun fitOneLine(paint: Paint, text: String, maxWidth: Float) {
        while (paint.textSize > 10f && paint.measureText(text) > maxWidth) {
            paint.textSize -= 0.5f
        }
    }

    private fun lineHeight(paint: Paint): Int {
        val metrics = paint.fontMetrics
        return (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
    }

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, top: Float): Float {
        val metrics = paint.fontMetrics
        canvas.drawText(text, PAPER_PX / 2f, top - metrics.ascent, paint)
        return top + (metrics.descent - metrics.ascent)
    }

    private fun toSolidInk(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val lum = 0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
            pixels[i] = if (lum < 160) Color.BLACK else Color.WHITE
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        source.recycle()
        return out
    }
}
