package br.com.gate8.pos.stone.di

import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.printer.NoOpReceiptPrinter
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.stone.payment.StonePaymentGatewayStub
import org.koin.dsl.module

val stoneFlavorModule = module {
    single<PaymentGateway> { StonePaymentGatewayStub() }
    single<ReceiptPrinter> { NoOpReceiptPrinter() }
}
