package br.com.gate8.pos.cielo.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import br.com.gate8.pos.printer.PrintImageFiles

/**
 * Relatório e resumo de caixa em bitmap.
 * A impressora da Cielo centraliza o texto e usa fonte proporcional, então
 * rótulo e valor não fecham nas bordas. Aqui o rótulo encosta à esquerda,
 * o valor à direita, e os pontos preenchem o vão medido em pixels.
 */
internal object CieloAlignedReceiptBitmap {
    private const val TAG = "CieloAlignedReceipt"
    private const val PAPER_PX = 384
    private const val PAD_X = 8f
    private const val TEXT_SIZE = 22f

    fun preparePath(lines: List<String>, fileName: String): String? {
        if (lines.isEmpty()) return null
        return runCatching {
            val bitmap = render(lines)
            val path = PrintImageFiles.writePng(fileName, bitmap)
            bitmap.recycle()
            path
        }.onFailure { Log.w(TAG, "Recibo alinhado não preparado", it) }
            .getOrNull()
    }

    private fun render(lines: List<String>): Bitmap {
        val text = textPaint(TEXT_SIZE)
        val rows = lines.map { classify(it) }
        val height = rows.sumOf { rowHeight(it, text) } + 28
        val bitmap = Bitmap.createBitmap(PAPER_PX, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var top = 8f
        for (row in rows) {
            top = draw(canvas, row, text, top)
        }
        return toSolidInk(bitmap)
    }

    private fun draw(canvas: Canvas, row: Row, text: Paint, top: Float): Float {
        val content = PAPER_PX - PAD_X * 2
        return when (row) {
            Row.Gap -> top + 10f
            Row.Rule -> {
                val y = top + 8f
                val rule = textPaint(TEXT_SIZE).apply { strokeWidth = 2f }
                canvas.drawLine(PAD_X, y, PAPER_PX - PAD_X, y, rule)
                y + 10f
            }
            is Row.Center -> {
                val baseline = top - text.fontMetrics.ascent
                val width = text.measureText(row.text)
                canvas.drawText(row.text, (PAPER_PX - width) / 2f, baseline, text)
                baseline + text.fontMetrics.descent + 4f
            }
            is Row.Leader -> {
                val metrics = text.fontMetrics
                val baseline = top - metrics.ascent
                var right = row.right
                var left = row.left
                val maxRight = content * 0.55f
                if (text.measureText(right) > maxRight) right = fit(text, right, maxRight)
                val rightW = text.measureText(right)
                val maxLeft = (content - rightW - 28f).coerceAtLeast(24f)
                if (text.measureText(left) > maxLeft) left = fit(text, left, maxLeft)
                val leftW = text.measureText(left)
                canvas.drawText(left, PAD_X, baseline, text)
                canvas.drawText(right, PAPER_PX - PAD_X - rightW, baseline, text)
                val dot = "."
                val dotW = text.measureText(dot)
                val step = dotW + 3f
                var x = PAD_X + leftW + 8f
                val end = PAPER_PX - PAD_X - rightW - 8f
                while (x + dotW <= end) {
                    canvas.drawText(dot, x, baseline, text)
                    x += step
                }
                baseline + metrics.descent + 3f
            }
        }
    }

    private fun rowHeight(row: Row, text: Paint): Int {
        val line = (text.fontMetrics.descent - text.fontMetrics.ascent + 6f).toInt()
        return when (row) {
            Row.Gap -> 10
            Row.Rule -> 18
            is Row.Center, is Row.Leader -> line
        }
    }

    private fun classify(raw: String): Row {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Row.Gap
        if (trimmed.all { it == '-' || it == '=' || it == '.' }) return Row.Rule
        val match = LEADER.matchEntire(trimmed)
        if (match != null) {
            val left = match.groupValues[1].trim()
            val right = match.groupValues[2].trim()
            if (left.isNotEmpty() && right.isNotEmpty()) return Row.Leader(left, right)
        }
        return Row.Center(trimmed)
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var value = text
        while (value.isNotEmpty() && paint.measureText(value.trimEnd() + "...") > maxWidth) {
            value = value.dropLast(1)
        }
        val trimmed = value.trimEnd()
        return if (trimmed.isEmpty()) "..." else "$trimmed..."
    }

    private fun textPaint(size: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = size
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

    private sealed interface Row {
        data object Gap : Row
        data object Rule : Row
        data class Center(val text: String) : Row
        data class Leader(val left: String, val right: String) : Row
    }

    private val LEADER = Regex("""^(.+?)(?:\s\.)+\s+(.+)$""")
}
