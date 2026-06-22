package br.com.gate8.pos.data.repository

import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ReportsBrandRowDto
import br.com.gate8.pos.data.remote.dto.ReportsDeviceDto
import br.com.gate8.pos.data.remote.dto.ReportsItemRowDto
import br.com.gate8.pos.data.remote.dto.ReportsPaymentRowDto
import br.com.gate8.pos.data.remote.dto.ReportsPeriodDto
import br.com.gate8.pos.data.remote.dto.ReportsSummaryDto
import br.com.gate8.pos.data.remote.dto.ReportsTotalsDto
import retrofit2.HttpException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReportsRepository(
    private val api: PosApiService,
) {
    suspend fun fetchSummary(from: Instant, to: Instant, segment: String): ReportsSummaryDto {
        val fromIso = ISO.format(from)
        val toIso = ISO.format(to)
        return try {
            val response = api.getReportsSummary(fromIso, toIso, segment)
            if (response.isSuccessful) {
                response.body() ?: throw ApiException(response.code(), "Resposta vazia")
            } else if (response.code() == 404 && BuildConfig.DEBUG) {
                demoSummary(from, to, segment)
            } else {
                throw ApiException(response.code(), response.errorBody()?.string() ?: "Falha ao carregar relatório")
            }
        } catch (e: HttpException) {
            if (e.code() == 404 && BuildConfig.DEBUG) {
                demoSummary(from, to, segment)
            } else {
                throw ApiException(e.code(), e.message())
            }
        }
    }

    private fun demoSummary(from: Instant, to: Instant, segment: String): ReportsSummaryDto {
        val ticketItems = listOf(
            ReportsItemRowDto("Ingresso Pista", 42, 3360.0),
            ReportsItemRowDto("Ingresso VIP", 12, 2400.0),
            ReportsItemRowDto("Cortesia", 6, 0.0),
        )
        val productItems = listOf(
            ReportsItemRowDto("Copão Whisky", 18, 270.0),
            ReportsItemRowDto("Cerveja Lata", 14, 140.0),
            ReportsItemRowDto("Água", 9, 45.0),
        )
        val (totals, payments, brands, items) = when (segment) {
            "ticket" -> DemoBundle(
                ReportsTotalsDto(54, 1, 5760.0, 200.0, 5560.0, 106.67),
                listOf(
                    ReportsPaymentRowDto("pix", "Pix", 20, 2400.0),
                    ReportsPaymentRowDto("credit", "Crédito", 22, 2960.0),
                    ReportsPaymentRowDto("debit", "Débito", 6, 400.0),
                ),
                listOf(
                    ReportsBrandRowDto("Visa", 14, 2100.0),
                    ReportsBrandRowDto("Mastercard", 8, 980.0),
                    ReportsBrandRowDto("Elo", 6, 280.0),
                ),
                ticketItems,
            )
            "product" -> DemoBundle(
                ReportsTotalsDto(28, 0, 455.0, 0.0, 455.0, 16.25),
                listOf(
                    ReportsPaymentRowDto("cash", "Dinheiro", 11, 160.0),
                    ReportsPaymentRowDto("debit", "Débito", 10, 175.0),
                    ReportsPaymentRowDto("credit", "Crédito", 7, 120.0),
                ),
                listOf(
                    ReportsBrandRowDto("Mastercard", 9, 170.0),
                    ReportsBrandRowDto("Visa", 8, 125.0),
                ),
                productItems,
            )
            else -> DemoBundle(
                ReportsTotalsDto(82, 1, 6215.0, 200.0, 6015.0, 75.79),
                listOf(
                    ReportsPaymentRowDto("pix", "Pix", 20, 2400.0),
                    ReportsPaymentRowDto("credit", "Crédito", 29, 3080.0),
                    ReportsPaymentRowDto("debit", "Débito", 16, 575.0),
                    ReportsPaymentRowDto("cash", "Dinheiro", 11, 160.0),
                ),
                listOf(
                    ReportsBrandRowDto("Visa", 22, 2225.0),
                    ReportsBrandRowDto("Mastercard", 17, 1150.0),
                    ReportsBrandRowDto("Elo", 6, 280.0),
                ),
                ticketItems + productItems,
            )
        }
        return ReportsSummaryDto(
            period = ReportsPeriodDto(from = ISO.format(from), to = ISO.format(to)),
            device = ReportsDeviceDto(id = "demo", name = "Maquininha (demo)"),
            segment = segment,
            summary = totals,
            byPaymentMethod = payments,
            byBrand = brands,
            topItems = items,
        )
    }

    private data class DemoBundle(
        val totals: ReportsTotalsDto,
        val payments: List<ReportsPaymentRowDto>,
        val brands: List<ReportsBrandRowDto>,
        val items: List<ReportsItemRowDto>,
    )

    companion object {
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("America/Sao_Paulo"))
    }
}
