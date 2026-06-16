package br.com.gate8.pos.stone

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.stone.runtime.StoneRuntime
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import br.com.gate8.pos.stone.work.StoneReversalWorker
import java.util.concurrent.TimeUnit

class StoneSdkBootstrap(
    private val application: Application,
    private val bridge: StoneSdkBridge,
    private val config: DeviceConfigStore,
) : StoneRuntime {

    override fun onApplicationStart() {
        if (BuildConfig.STONE_SDK_LINKED) {
            bridge.initialize(application)
            config.getStoneCode()?.let { code ->
                // Ativação assíncrona na 1ª abertura com StoneCode salvo
                // (completa em StoneSdkBridgeLive quando token presente)
            }
        }
        scheduleReversal()
    }

    private fun scheduleReversal() {
        val request = PeriodicWorkRequestBuilder<StoneReversalWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "gate8_stone_reversal"
    }
}
