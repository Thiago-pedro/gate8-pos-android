package br.com.gate8.pos

import android.app.Application
import android.util.Log
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.KitchenUploadRetry
import br.com.gate8.pos.di.appModule
import br.com.gate8.pos.di.flavorModules
import br.com.gate8.pos.kitchen.KitchenRuntime
import br.com.gate8.pos.payment.PaymentRuntime
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class Gate8PosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@Gate8PosApplication)
            modules(appModule, *flavorModules().toTypedArray())
        }
        GlobalContext.get().get<PaymentRuntime>().onApplicationStart()
        GlobalContext.get().get<KitchenUploadRetry>().start()
        val config = GlobalContext.get().get<DeviceConfigStore>()
        if (config.isLoggedIn() && config.isKitchenMode()) {
            runCatching { KitchenRuntime.start(this) }
                .onFailure { Log.w("Gate8Kitchen", "Modo cozinha não retomou no arranque", it) }
        }
    }
}
