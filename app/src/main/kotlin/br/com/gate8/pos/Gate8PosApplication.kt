package br.com.gate8.pos

import android.app.Application
import br.com.gate8.pos.di.appModule
import br.com.gate8.pos.di.flavorModules
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
    }
}
