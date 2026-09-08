package br.com.gate8.pos.data.remote.dto

import br.com.gate8.pos.domain.model.IssuedTicket
import br.com.gate8.pos.domain.model.SaleTicketGroup
import java.util.Locale

fun TicketCodeDto.toIssuedTicket(): IssuedTicket {
    val hex = code.trim()
    val qr = qrPayload?.trim()?.takeIf { it.isNotEmpty() } ?: hex
    val manual = manualCode?.trim()?.takeIf { it.isNotEmpty() }
        ?: hex.filter { it.isLetterOrDigit() }.take(8).uppercase(Locale.US)
    return IssuedTicket(
        id = id,
        code = hex,
        qrPayload = qr,
        manualCode = manual,
        holderName = holderName,
        eventName = eventName,
        batchName = batchName,
        eventDate = eventDate,
        venue = venue,
        price = price,
        statusLabel = statusLabel,
        issuedAt = issuedAt,
        purchaseCode = purchaseCode,
    )
}

fun SaleTicketGroupDto.toDomain(): SaleTicketGroup =
    SaleTicketGroup(
        itemIndex = itemIndex,
        tickets = tickets.map { it.toIssuedTicket() },
    )
