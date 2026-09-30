package br.com.gate8.pos

import br.com.gate8.pos.core.util.CieloUserText
import br.com.gate8.pos.ui.common.PaymentUserMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CieloUserTextTest {
    @Test
    fun repairsMojibakeOperacaoNaoFinalizada() {
        val raw = "OperaÃ§Ã£o nÃ£o finalizada."
        assertEquals("Operação não finalizada.", CieloUserText.repair(raw))
    }

    @Test
    fun detectsNotFinishedInMojibakeAndPlain() {
        assertTrue(CieloUserText.isOperationNotFinished("OperaÃ§Ã£o nÃ£o finalizada."))
        assertTrue(CieloUserText.isUserCancel("Operação não finalizada"))
        assertFalse(CieloUserText.isOperationNotFinished("Pagamento recusado"))
    }

    @Test
    fun mapsFailureReasonWithoutMojibake() {
        val reason = PaymentUserMessages.failureReason(
            IllegalStateException("2, OperaÃ§Ã£o nÃ£o finalizada."),
        )
        assertEquals(PaymentUserMessages.NOT_FINISHED, reason)
        assertFalse(reason.contains("Ã"))
    }
}
