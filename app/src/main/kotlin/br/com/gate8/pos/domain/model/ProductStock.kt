package br.com.gate8.pos.domain.model

import br.com.gate8.pos.data.remote.dto.ProductDto

/**
 * Controle de estoque no painel Gate8 ("Controlar estoque deste produto").
 *
 * O Lovable costuma enviar `stock_quantity: 0` mesmo com o toggle desligado.
 * Por isso só bloqueamos venda quando há flag explícita `true` ou quantidade > 0.
 */
val ProductDto.tracksStock: Boolean
    get() {
        if (trackStock == false || manageStock == false || stockControl == false ||
            trackInventory == false || manageInventory == false
        ) {
            return false
        }
        if (trackStock == true || manageStock == true || stockControl == true ||
            trackInventory == true || manageInventory == true
        ) {
            return true
        }
        return (stockQuantity ?: 0) > 0
    }

val ProductDto.isOutOfStock: Boolean
    get() = tracksStock && (stockQuantity ?: 0) <= 0

fun ProductDto.canAddMore(alreadyInCart: Int): Boolean {
    if (!tracksStock) return true
    val available = stockQuantity ?: 0
    return alreadyInCart < available
}
