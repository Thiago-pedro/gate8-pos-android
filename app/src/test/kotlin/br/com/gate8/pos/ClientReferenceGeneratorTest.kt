package br.com.gate8.pos

import br.com.gate8.pos.core.util.ClientReferenceGenerator
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientReferenceGeneratorTest {
    @Test
    fun generatesUniqueReferences() {
        val a = ClientReferenceGenerator.newReference("POS01", useTestPrefix = true)
        val b = ClientReferenceGenerator.newReference("POS01", useTestPrefix = true)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("TEST-"))
        assertTrue(a.length <= 100)
    }
}
