package br.com.gate8.pos

import br.com.gate8.pos.core.util.CieloCallbackParser
import br.com.gate8.pos.core.util.CieloParsedCallback
import org.junit.Assert.assertTrue
import org.junit.Test

class CieloCallbackParserTest {

    @Test
    fun pixStatusCodeZeroIsApprovedEvenWithNotFinishedReason() {
        val raw = """
            {"id":"pix-order-1","statusCode":0,"reason":"Operação não finalizada"}
        """.trimIndent()
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Approved)
    }

    @Test
    fun pixPaymentFieldsStatusCodeZeroIsApproved() {
        val raw = """
            {"id":"pix-order-2","payments":[{"id":"p1","paymentFields":{"statusCode":"0"}}]}
        """.trimIndent()
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Approved)
    }

    @Test
    fun pixPaidAmountIsApproved() {
        val raw = """{"id":"pix-order-3","paidAmount":1500,"payments":[]}"""
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Approved)
    }

    @Test
    fun errorEnvelopeWithoutOrderIsRejected() {
        val raw = """{"code":2,"reason":"Operação não finalizada"}"""
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Rejected)
    }

    @Test
    fun userCancelEnvelopeIsRejected() {
        val raw = """{"code":1,"reason":"CANCELADO PELO USUÁRIO"}"""
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Rejected)
    }

    @Test
    fun cardPaymentsArrayIsApproved() {
        val raw = """
            {"id":"card-1","status":"ENTERED","payments":[{"cieloCode":"123","authCode":"456","paymentFields":{"statusCode":"1"}}]}
        """.trimIndent()
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Approved)
    }

    @Test
    fun mojibakeNotFinishedWithoutOrderIsRejected() {
        val raw = """{"code":2,"reason":"OperaÃ§Ã£o nÃ£o finalizada."}"""
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Rejected)
    }

    @Test
    fun draftOrderWithNotFinishedReasonIsNotTreatedAsPaid() {
        val raw = """{"id":"draft-1","code":0,"reason":"Operação não finalizada"}"""
        val parsed = CieloCallbackParser.parseJson(raw)
        assertTrue(parsed is CieloParsedCallback.Rejected)
    }
}
