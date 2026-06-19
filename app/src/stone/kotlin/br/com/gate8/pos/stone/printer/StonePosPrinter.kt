package br.com.gate8.pos.stone.printer

import android.app.Activity
import android.graphics.Bitmap

/**
 * Impressão Stone POS — implementação real em `stoneLive` (PosPrint* providers).
 */
interface StonePosPrinter {
    val isAvailable: Boolean

    fun printLines(
        activity: Activity,
        lines: List<String>,
        bitmap: Bitmap? = null,
        logoScale: Float = 1f,
    )

    fun printCardReceipt(activity: Activity, transactionId: String?, nsu: String?, merchantCopy: Boolean)

    fun reprintCardReceipt(activity: Activity, transactionId: String, merchantCopy: Boolean)
}

class StonePosPrinterUnavailable : StonePosPrinter {
    override val isAvailable: Boolean = false

    override fun printLines(
        activity: Activity,
        lines: List<String>,
        bitmap: Bitmap?,
        logoScale: Float,
    ) = Unit

    override fun printCardReceipt(
        activity: Activity,
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
    ) = Unit

    override fun reprintCardReceipt(activity: Activity, transactionId: String, merchantCopy: Boolean) = Unit
}
