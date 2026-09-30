package br.com.gate8.pos.cielo.deeplink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Recebe o callback Deep Link da Cielo Smart (`gate8cielo://payment` / `print` / `response`).
 */
class CieloResponseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        val response = uri?.getQueryParameter("response")
        val responseCode = uri?.getQueryParameter("responsecode")?.toIntOrNull()
            ?: uri?.getQueryParameter("responseCode")?.toIntOrNull()
        Log.i(TAG, "Callback Cielo host=${uri?.host} responsecode=$responseCode len=${response?.length ?: 0}")
        CieloDeeplinkSession.completeFromCallback(
            responseBase64 = response,
            uriResponseCode = responseCode,
            host = uri?.host,
        )
    }

    companion object {
        private const val TAG = "CieloResponse"
    }
}
