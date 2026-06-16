package br.com.gate8.pos.stone.di

import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.stone.StoneActivityHolder
import br.com.gate8.pos.stone.StoneSdkBootstrap
import br.com.gate8.pos.stone.payment.StonePaymentGateway
import br.com.gate8.pos.stone.printer.StoneReceiptPrinterAdapter
import br.com.gate8.pos.stone.runtime.StoneRuntime
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import br.com.gate8.pos.stone.sdk.StoneSdkBridgeUnavailable
import org.koin.dsl.module

val stoneFlavorModule = module {
    single { StoneActivityHolder() }
    single<StoneSdkBridge> {
        if (BuildConfig.STONE_SDK_LINKED) {
            createLiveBridge(get(), get())
        } else {
            StoneSdkBridgeUnavailable()
        }
    }
    single<PaymentGateway> { StonePaymentGateway(get()) }
    single<ReceiptPrinter> { StoneReceiptPrinterAdapter(get()) }
    single<StoneRuntime> { StoneSdkBootstrap(get(), get(), get()) }
}

private fun createLiveBridge(
    activityHolder: StoneActivityHolder,
    config: DeviceConfigStore,
): StoneSdkBridge {
    return try {
        val clazz = Class.forName("br.com.gate8.pos.stone.sdk.StoneSdkBridgeLive")
        val ctor = clazz.getConstructor(StoneActivityHolder::class.java, DeviceConfigStore::class.java)
        ctor.newInstance(activityHolder, config) as StoneSdkBridge
    } catch (e: ReflectiveOperationException) {
        throw IllegalStateException(
            "STONE_SDK_LINKED=true mas StoneSdkBridgeLive não foi compilado. Sincronize o Gradle.",
            e,
        )
    }
}
