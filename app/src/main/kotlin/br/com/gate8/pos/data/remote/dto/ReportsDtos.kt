package br.com.gate8.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReportsSummaryDto(
    val period: ReportsPeriodDto,
    val device: ReportsDeviceDto? = null,
    val segment: String? = null,
    val summary: ReportsTotalsDto,
    @SerialName("by_payment_method") val byPaymentMethod: List<ReportsPaymentRowDto> = emptyList(),
    @SerialName("by_brand") val byBrand: List<ReportsBrandRowDto> = emptyList(),
    @SerialName("top_items") val topItems: List<ReportsItemRowDto> = emptyList(),
)

@Serializable
data class ReportsPeriodDto(
    val from: String,
    val to: String,
)

@Serializable
data class ReportsDeviceDto(
    val id: String,
    val name: String,
)

@Serializable
data class ReportsTotalsDto(
    @SerialName("sale_count") val saleCount: Int = 0,
    @SerialName("void_count") val voidCount: Int = 0,
    @SerialName("gross_total") val grossTotal: Double = 0.0,
    @SerialName("void_total") val voidTotal: Double = 0.0,
    @SerialName("net_total") val netTotal: Double = 0.0,
    @SerialName("average_ticket") val averageTicket: Double = 0.0,
)

@Serializable
data class ReportsPaymentRowDto(
    val method: String,
    val label: String,
    val count: Int = 0,
    val total: Double = 0.0,
)

@Serializable
data class ReportsBrandRowDto(
    val brand: String,
    val count: Int = 0,
    val total: Double = 0.0,
)

@Serializable
data class ReportsItemRowDto(
    val name: String,
    val quantity: Int = 0,
    val total: Double = 0.0,
)
