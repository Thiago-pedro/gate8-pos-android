package br.com.gate8.pos.mock.di

import br.com.gate8.pos.mock.payment.MockPaymentGateway
import br.com.gate8.pos.mock.printer.MockPrinterProvider
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.stone.runtime.NoOpStoneRuntime
import br.com.gate8.pos.stone.runtime.StoneRuntime
import org.koin.dsl.module

val mockFlavorModule = module {
    single<PaymentGateway> { MockPaymentGateway() }
    single<ReceiptPrinter> { MockPrinterProvider() }
    single<StoneRuntime> { NoOpStoneRuntime() }
}
