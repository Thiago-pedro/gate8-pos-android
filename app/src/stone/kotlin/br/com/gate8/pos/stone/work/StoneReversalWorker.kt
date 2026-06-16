package br.com.gate8.pos.stone.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Boa prática Stone: ReversalProvider ~1h para transações WITH_ERROR.
 */
class StoneReversalWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val bridge: StoneSdkBridge by inject()

    override suspend fun doWork(): Result {
        bridge.runReversal(applicationContext)
        return Result.success()
    }
}
