package br.com.gate8.pos.cielo.deeplink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Recebe o callback Deep Link da Cielo Smart (`gate8cielo://response?response=…`).
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
        val response = intent.data?.getQueryParameter("response")
        Log.i(TAG, "Callback Cielo recebido (len=${response?.length ?: 0})")
        CieloDeeplinkSession.completeFromUriResponse(response)
    }

    companion object {
        private const val TAG = "CieloResponse"
    }
}
