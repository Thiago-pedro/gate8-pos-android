package br.com.gate8.pos

import br.com.gate8.pos.data.remote.dto.ProductDto
import br.com.gate8.pos.domain.model.KitchenCategory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenCategoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun onlyCozinhaCategoryPrintsOnKitchenMachine() {
        assertTrue(KitchenCategory.matches("Cozinha"))
        assertTrue(KitchenCategory.matches("COZINHA"))
        assertTrue(KitchenCategory.matches("  cozinha "))
        assertFalse(KitchenCategory.matches("Bebidas"))
        assertFalse(KitchenCategory.matches("Lanche"))
        assertFalse(KitchenCategory.matches("Alimentação"))
        assertFalse(KitchenCategory.matches(null))
        assertFalse(KitchenCategory.matches(""))
    }

    @Test
    fun catalogCategoryStringIsCozinha() {
        val product = json.decodeFromString<ProductDto>(
            """{"id":"1","name":"X-Burger","price":12.0,"category":"Cozinha"}""",
        )
        assertEquals("Cozinha", product.category)
        assertTrue(KitchenCategory.matches(product.category))
    }

    @Test
    fun catalogCategoryObjectNameIsCozinha() {
        val product = json.decodeFromString<ProductDto>(
            """{"id":"1","name":"Batata","price":8.0,"categoria":{"name":"Cozinha"}}""",
        )
        assertEquals("Cozinha", product.category)
        assertTrue(KitchenCategory.matches(product.category))
    }
}
