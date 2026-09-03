package br.com.gate8.pos.cielo.di

import br.com.gate8.pos.cielo.cashless.CieloMifareClient
import br.com.gate8.pos.cielo.payment.CieloPaymentGateway
import br.com.gate8.pos.cielo.printer.CieloReceiptPrinter
import br.com.gate8.pos.cielo.runtime.CieloRuntime
import br.com.gate8.pos.cielo.settings.CieloSettingsGatewayImpl
import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.device.PosHardwareInfoUnavailable
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentRuntime
import br.com.gate8.pos.payment.TerminalSettingsGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val cieloFlavorModule = module {
    single<PaymentGateway> { CieloPaymentGateway() }
    single<ReceiptPrinter> { CieloReceiptPrinter(get(), androidApplication()) }
    single<PaymentRuntime> { CieloRuntime(androidApplication()) }
    single<TerminalSettingsGateway> { CieloSettingsGatewayImpl() }
    single<PosHardwareInfo> { PosHardwareInfoUnavailable() }
    single<CashlessCardGateway> { CieloMifareClient(androidApplication()) }
}
