package br.com.gate8.pos

import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.payment.MoneyCents
import br.com.gate8.pos.payment.SplitPayments
import br.com.gate8.pos.payment.SplitSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitPaymentMathTest {
    @Test
    fun dezoitoReaisFechaEmDezMaisOito() {
        val total = MoneyCents.parse("18,00")
        val pix = MoneyCents.parse("10,00")
        val cash = MoneyCents.parse("8,00")
        assertEquals(1800L, total)
        assertEquals(1000L, pix)
        assertEquals(800L, cash)
        assertEquals(total, pix!! + cash!!)
    }

    @Test
    fun rejeitaZeroNegativoEValorAcimaDoRestante() {
        assertNull(MoneyCents.parse("0"))
        assertNull(MoneyCents.parse("0,00"))
        assertNull(MoneyCents.parse("-1"))
        val session = SplitSessionRecord(
            scope = "products",
            clientReference = "ref",
            totalCents = 1800L,
        )
        assertEquals(
            "Informe um valor maior que zero.",
            SplitPayments.validatePart(session, 0L),
        )
        assertEquals(
            "O valor passa do restante de R$ 18,00.",
            SplitPayments.validatePart(session, 1801L),
        )
        assertNull(SplitPayments.validatePart(session, 1000L))
    }

    @Test
    fun totalDoCarrinhoUsaCentavos() {
        val cart = listOf(
            CartLine(ItemType.PRODUCT, description = "A", quantity = 1, unitPrice = 10.0),
            CartLine(ItemType.PRODUCT, description = "B", quantity = 1, unitPrice = 8.0),
        )
        assertEquals(1800L, SplitPayments.cartTotalCents(cart))
    }
}
