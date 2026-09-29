package br.com.gate8.pos.domain.model

object KitchenCategory {
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
        if (normalized.isBlank()) return false
        return normalized.contains("aliment") ||
            normalized.contains("comida") ||
            normalized.contains("food") ||
            normalized.contains("lanche") ||
            normalized.contains("cozinha") ||
            normalized.contains("kitchen")
    }
}
