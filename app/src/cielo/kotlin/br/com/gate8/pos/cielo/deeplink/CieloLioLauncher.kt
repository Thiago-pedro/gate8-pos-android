package br.com.gate8.pos.cielo.deeplink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Abre `lio://` direto no UriApp da Cielo, sem o chooser
 * "Abrir com Gate8 / Checkout Móvel".
 */
internal object CieloLioLauncher {
    const val URIAPP_PACKAGE = "com.ads.lio.uriappclient"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(activity: Activity, uri: Uri) {
        val launch = {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                `package` = URIAPP_PACKAGE
            }
            if (intent.resolveActivity(activity.packageManager) == null) {
                Log.w(TAG, "UriApp não resolvido — abrindo lio:// sem package")
                intent.`package` = null
            }
            activity.startActivity(intent)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launch()
        } else {
            mainHandler.post(launch)
        }
    }

    private const val TAG = "CieloLio"
}
