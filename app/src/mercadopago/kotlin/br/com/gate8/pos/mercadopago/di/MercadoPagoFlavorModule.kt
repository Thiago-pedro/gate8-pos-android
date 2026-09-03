package br.com.gate8.pos.mercadopago.di

import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.cashless.UnavailableCashlessCardGateway
import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.device.PosHardwareInfoUnavailable
import br.com.gate8.pos.mercadopago.payment.MercadoPagoPaymentGateway
import br.com.gate8.pos.mercadopago.printer.MercadoPagoReceiptPrinter
import br.com.gate8.pos.mercadopago.runtime.MercadoPagoRuntime
import br.com.gate8.pos.mercadopago.settings.MercadoPagoSettingsGatewayImpl
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentRuntime
import br.com.gate8.pos.payment.TerminalSettingsGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import org.koin.dsl.module

val mercadoPagoFlavorModule = module {
    single<PaymentGateway> { MercadoPagoPaymentGateway(get(), get()) }
    single<ReceiptPrinter> { MercadoPagoReceiptPrinter() }
    single<PaymentRuntime> { MercadoPagoRuntime() }
    single<TerminalSettingsGateway> { MercadoPagoSettingsGatewayImpl(get()) }
    single<PosHardwareInfo> { PosHardwareInfoUnavailable() }
    single<CashlessCardGateway> { UnavailableCashlessCardGateway() }
}
