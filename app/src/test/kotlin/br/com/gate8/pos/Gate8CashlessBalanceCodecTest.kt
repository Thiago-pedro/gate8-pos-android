package br.com.gate8.pos

import br.com.gate8.pos.cashless.Gate8CashlessBalanceCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gate8CashlessBalanceCodecTest {
    @Test
    fun centoEQuinzeReaisIdaEVolta() {
        val block = Gate8CashlessBalanceCodec.encode(11_500)
        assertTrue(Gate8CashlessBalanceCodec.isGate8(block))
        assertTrue(Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(block))
        assertFalse(Gate8CashlessBalanceCodec.isVirgin(block))
        assertEquals(11_500, Gate8CashlessBalanceCodec.readCents(block))
    }

    @Test
    fun saldoZeroGate8NaoECartaoVirgem() {
        val block = Gate8CashlessBalanceCodec.encode(0)
        assertTrue(Gate8CashlessBalanceCodec.isGate8(block))
        assertTrue(Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(block))
        assertFalse(Gate8CashlessBalanceCodec.isVirgin(block))
        assertEquals(0, Gate8CashlessBalanceCodec.readCents(block))
    }

    @Test
    fun blocoVirgemEConfiavelMasSemSaldoGate8() {
        val zeros = ByteArray(16)
        val ff = ByteArray(16) { 0xFF.toByte() }
        assertTrue(Gate8CashlessBalanceCodec.isVirgin(zeros))
        assertTrue(Gate8CashlessBalanceCodec.isVirgin(ff))
        assertTrue(Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(zeros))
        assertNull(Gate8CashlessBalanceCodec.readCents(zeros))
        assertFalse(Gate8CashlessBalanceCodec.isGate8(zeros))
    }

    @Test
    fun blocoDeFabricaNaoViraSaldoZero() {
        val manufacturer = byteArrayOf(
            0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(),
            0xD4.toByte(), 0x08, 0x04, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        assertFalse(Gate8CashlessBalanceCodec.isGate8(manufacturer))
        assertFalse(Gate8CashlessBalanceCodec.isVirgin(manufacturer))
        assertFalse(Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(manufacturer))
        assertNull(Gate8CashlessBalanceCodec.readCents(manufacturer))
    }

    @Test
    fun leituraCurtaNaoEConfiavel() {
        val short = byteArrayOf('G'.code.toByte(), '8'.code.toByte())
        assertFalse(Gate8CashlessBalanceCodec.isTrustworthyBalanceBlock(short))
        assertNull(Gate8CashlessBalanceCodec.readCents(short))
    }
}
