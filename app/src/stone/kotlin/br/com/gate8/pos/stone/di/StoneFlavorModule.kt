package br.com.gate8.pos.stone.di

import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.device.PosHardwareInfoUnavailable
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.stone.StoneActivityHolder
import br.com.gate8.pos.stone.StoneSdkBootstrap
import br.com.gate8.pos.stone.payment.StonePaymentGateway
import br.com.gate8.pos.stone.printer.StonePosPrinter
import br.com.gate8.pos.stone.printer.StonePosPrinterUnavailable
import br.com.gate8.pos.stone.printer.StoneReceiptPrinterAdapter
import br.com.gate8.pos.stone.runtime.StoneRuntime
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import br.com.gate8.pos.stone.sdk.StoneSdkBridgeUnavailable
import br.com.gate8.pos.stone.settings.StoneSettingsGateway
import br.com.gate8.pos.stone.settings.StoneSettingsGatewayImpl
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
    single<StonePosPrinter> {
        if (BuildConfig.STONE_SDK_LINKED) {
            createLivePosPrinter()
        } else {
            StonePosPrinterUnavailable()
        }
    }
    single<PosHardwareInfo> {
        if (BuildConfig.STONE_SDK_LINKED) {
            createLiveHardwareInfo()
        } else {
            PosHardwareInfoUnavailable()
        }
    }
    single<PaymentGateway> { StonePaymentGateway(get()) }
    single<ReceiptPrinter> { StoneReceiptPrinterAdapter(get(), get(), get()) }
    single<StoneRuntime> { StoneSdkBootstrap(get(), get()) }
    single<StoneSettingsGateway> { StoneSettingsGatewayImpl(get(), get()) }
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

private fun createLiveHardwareInfo(): PosHardwareInfo {
    return try {
        val clazz = Class.forName("br.com.gate8.pos.stone.device.PosHardwareInfoLive")
        clazz.getDeclaredConstructor().newInstance() as PosHardwareInfo
    } catch (e: ReflectiveOperationException) {
        PosHardwareInfoUnavailable()
    }
}

private fun createLivePosPrinter(): StonePosPrinter {
    return try {
        val clazz = Class.forName("br.com.gate8.pos.stone.printer.StonePosPrinterLive")
        clazz.getDeclaredConstructor().newInstance() as StonePosPrinter
    } catch (e: ReflectiveOperationException) {
        throw IllegalStateException(
            "STONE_SDK_LINKED=true mas StonePosPrinterLive não foi compilado. Sincronize o Gradle.",
            e,
        )
    }
}
