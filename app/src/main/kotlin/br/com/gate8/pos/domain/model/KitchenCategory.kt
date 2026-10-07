package br.com.gate8.pos.domain.model

/**
 * Categoria da conveniência que dispara ficha na maquininha em modo cozinha.
 * No painel o nome cadastrado é "Cozinha".
 */
object KitchenCategory {
    const val NAME = "Cozinha"

    fun matches(category: String?): Boolean {
        val normalized = category.orEmpty()
            .lowercase()
            .replace("á", "a")
            .replace("à", "a")
            .replace("ã", "a")
            .replace("â", "a")
            .replace("é", "e")
            .replace("ê", "e")
            .trim()
        return normalized == "cozinha" || normalized == "kitchen"
    }
}
