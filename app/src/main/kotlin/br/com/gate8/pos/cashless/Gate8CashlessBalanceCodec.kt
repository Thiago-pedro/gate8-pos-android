package br.com.gate8.pos.cashless

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Formato Gate8 no bloco de saldo (16 bytes, setor 1 bloco 0):
 * - 0..3  magic `G8CL`
 * - 4..7  saldo em centavos (Int LE)
 * - 8     versão (=1)
 * - 9..15 reservado (0)
 */
object Gate8CashlessBalanceCodec {
    private val MAGIC = byteArrayOf('G'.code.toByte(), '8'.code.toByte(), 'C'.code.toByte(), 'L'.code.toByte())
    private const val VERSION: Byte = 1

    fun isGate8(block: ByteArray): Boolean =
        block.size >= 8 &&
            block[0] == MAGIC[0] &&
            block[1] == MAGIC[1] &&
            block[2] == MAGIC[2] &&
            block[3] == MAGIC[3]

    fun readCents(block: ByteArray): Int? {
        if (!isGate8(block)) return null
        return ByteBuffer.wrap(block, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun encode(cents: Int): ByteArray {
        require(cents >= 0) { "Saldo não pode ser negativo" }
        val out = ByteArray(16)
        MAGIC.copyInto(out, 0)
        ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(cents)
        out[8] = VERSION
        return out
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

    fun toAscii(bytes: ByteArray): String = buildString {
        bytes.forEach { b ->
            val c = b.toInt() and 0xFF
            append(if (c in 32..126) c.toChar() else '.')
        }
    }

    fun uidToHex(uid: ByteArray): String =
        uid.joinToString("") { b -> "%02X".format(b.toInt() and 0xFF) }
}
