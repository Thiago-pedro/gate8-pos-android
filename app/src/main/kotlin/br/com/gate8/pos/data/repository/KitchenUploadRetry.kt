package br.com.gate8.pos.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A conveniência reenvia o pedido da cozinha até o painel aceitar,
 * mesmo depois que o operador sai da tela de venda.
 */
class KitchenUploadRetry(
    private val kitchenRepository: KitchenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { kitchenRepository.flushUnsynced() }
                    .onFailure { Log.w(TAG, "Nova tentativa da fila da cozinha falhou", it) }
                delay(4_000)
            }
        }
    }

    private companion object {
        const val TAG = "Gate8Kitchen"
    }
}
