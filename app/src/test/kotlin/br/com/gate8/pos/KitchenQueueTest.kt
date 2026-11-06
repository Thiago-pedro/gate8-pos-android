package br.com.gate8.pos

import br.com.gate8.pos.data.repository.mergeRecentOrders
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.domain.model.KitchenOrderItem
import br.com.gate8.pos.kitchen.KitchenOrderParser
import br.com.gate8.pos.kitchen.KitchenPrintQueue
import br.com.gate8.pos.kitchen.kitchenPrintShouldRetry
import br.com.gate8.pos.kitchen.kitchenPrinterWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class KitchenQueueTest {
    @Test
    fun printsOldestFirstAndSkipsOrderBeingPrinted() {
        val queue = KitchenPrintQueue()
        val older = order("6", 6, "2026-11-06T02:32:15Z")
        val middle = order("7", 7, "2026-11-06T02:38:35Z")
        val newer = order("8", 8, "2026-11-06T02:39:58Z")

        assertTrue(queue.offer(listOf(newer, middle, older)))
        assertEquals(6, queue.takeNext()?.orderNumber)
        assertFalse(queue.offer(listOf(older)))
        val extra = order("9", 9, "2026-11-06T02:40:00Z")
        assertTrue(queue.offer(listOf(extra)))

        assertEquals(7, queue.takeNext()?.orderNumber)
        assertEquals(8, queue.takeNext()?.orderNumber)
        assertEquals(9, queue.takeNext()?.orderNumber)
        assertNull(queue.takeNext())
    }

    @Test
    fun failedPrintCanBeQueuedAgainButSuccessCannotUntilDiscarded() {
        val queue = KitchenPrintQueue()
        val ticket = order("7", 7, "2026-11-06T02:38:35Z")
        queue.offer(listOf(ticket))
        val printing = queue.takeNext()!!
        queue.printFailed(printing.id)
        assertTrue(queue.offer(listOf(ticket)))

        queue.takeNext()
        queue.printSucceeded(ticket)
        assertFalse(queue.offer(listOf(ticket)))
        assertEquals(listOf("7"), queue.pendingAcks().map { it.id })
        queue.ackSucceeded(ticket.id)
        assertFalse(queue.offer(listOf(ticket)))
        assertTrue(queue.pendingAcks().isEmpty())
    }

    @Test
    fun obsItemBecomesNoteAndBrokenOrderDoesNotDropTheOthers() {
        val batch = KitchenOrderParser.parseList(
            """
            {
              "orders": [
                {
                  "id": "8",
                  "order_number": 8,
                  "terminal_name": "Caixa 9",
                  "sold_at": "2026-11-06T02:39:58.000Z",
                  "items": [{"description": "Suco", "quantity": 1}]
                },
                {
                  "id": "7",
                  "order_number": 7,
                  "terminal_name": "Caixa 9",
                  "sold_at": "2026-11-06T02:38:35.000Z",
                  "items": [
                    {"description": "Batata Frita (chips) 100g", "quantity": 1},
                    {"description": "OBS: mesa 3", "quantity": 1}
                  ]
                },
                {
                  "id": "ruim",
                  "order_number": "nao-numero",
                  "sold_at": {"invalido": true},
                  "items": "x"
                }
              ]
            }
            """.trimIndent(),
            now = 1_700_000_000_000L,
        )

        assertEquals(listOf("8", "7"), batch.orders.map { it.id })
        val potato = batch.orders.first { it.id == "7" }
        assertEquals(listOf("Batata Frita (chips) 100g"), potato.items.map { it.description })
        assertEquals("mesa 3", potato.note)
        assertEquals("Caixa 9", potato.terminalName)
        assertEquals(
            Instant.parse("2026-11-06T02:38:35.000Z").toEpochMilli(),
            potato.soldAtMillis,
        )
        assertTrue(batch.errors.any { it.contains("ruim") })

        val queue = KitchenPrintQueue()
        queue.offer(batch.orders)
        assertEquals(7, queue.takeNext()?.orderNumber)
        assertEquals(8, queue.takeNext()?.orderNumber)
    }

    @Test
    fun numericSoldAtAndBlankTerminalStillParse() {
        val batch = KitchenOrderParser.parseList(
            """
            {"orders":[{
              "id": "n1",
              "order_number": 9,
              "terminal_name": null,
              "sold_at": 1730853515000,
              "items": [{"description": "Agua", "quantity": "2"}]
            }]}
            """.trimIndent(),
        )
        val order = batch.orders.single()
        assertEquals("PDV", order.terminalName)
        assertEquals(1730853515000L, order.soldAtMillis)
        assertEquals(2, order.items.single().quantity)
        assertTrue(batch.errors.isEmpty())
    }

    @Test
    fun sameTicketNumberFromTwoCashiersBothPrint() {
        val queue = KitchenPrintQueue()
        val caixa1 = order("venda-caixa-1", 6, "2026-11-06T02:32:15Z")
        val caixa9 = order("venda-caixa-9", 6, "2026-11-06T02:38:35Z").copy(terminalName = "Caixa 9")
        assertTrue(queue.offer(listOf(caixa9, caixa1)))
        assertEquals("venda-caixa-1", queue.takeNext()?.id)
        assertEquals("venda-caixa-9", queue.takeNext()?.id)
        queue.printSucceeded(caixa1)
        queue.printFailed(caixa9.id)
        assertTrue(queue.offer(listOf(caixa9, caixa1)))
        assertEquals("venda-caixa-9", queue.takeNext()?.id)
        assertNull(queue.takeNext())
    }

    @Test
    fun printerWarningNamesPaperAndBusy() {
        assertEquals("Impressora sem papel", kitchenPrinterWarning("Sem papel na bobina"))
        assertEquals("Impressora ocupada", kitchenPrinterWarning("Impressora ocupada"))
        assertEquals("Abra o app Gate8 na maquininha", kitchenPrinterWarning("Abra o app Gate8 na Cielo Smart para imprimir."))
        assertNull(kitchenPrinterWarning("timeout"))
        assertNull(kitchenPrinterWarning("Falha na operação Cielo."))
    }

    @Test
    fun recentCardsReplaceSevenAndEightWithNewerSales() {
        val stuck = listOf(
            order("8", 8, "2026-11-06T02:39:58Z"),
            order("7", 7, "2026-11-06T02:38:35Z"),
        )
        val selling = (13 downTo 9).map { number ->
            order(number.toString(), number, "2026-11-06T03:${number.toString().padStart(2, '0')}:00Z")
        }
        assertEquals(listOf(13, 12, 11, 10), mergeRecentOrders(stuck, selling).map { it.orderNumber })
    }

    @Test
    fun `mesmo pedido nove vezes so imprime uma`() {
        val queue = KitchenPrintQueue()
        repeat(9) { index ->
            queue.offer(
                listOf(
                    order("copia-$index", 9, "2026-11-06T02:40:00Z")
                        .copy(items = listOf(KitchenOrderItem("Isca de frango", 1))),
                ),
            )
        }
        val printed = queue.takeNext()
        assertEquals("copia-0", printed?.id)
        queue.finishPrint(printed!!)
        assertNull(queue.takeNext())
        assertFalse(
            queue.offer(listOf(order("copia-nova", 9, "2026-11-06T02:40:00Z"))),
        )
        assertTrue(queue.offer(listOf(order("outro-caixa", 9, "2026-11-06T02:40:00Z").copy(terminalName = "Caixa 9"))))
        assertFalse(kitchenPrintShouldRetry("Falha na operação Cielo."))
        assertFalse(kitchenPrintShouldRetry("timeout"))
        assertTrue(kitchenPrintShouldRetry("Sem papel na bobina"))
    }

    private fun order(id: String, number: Int, soldAt: String) = KitchenOrder(
        id = id,
        orderNumber = number,
        terminalName = "Caixa 1",
        soldAtMillis = Instant.parse(soldAt).toEpochMilli(),
        items = listOf(KitchenOrderItem("Item", 1)),
    )
}
