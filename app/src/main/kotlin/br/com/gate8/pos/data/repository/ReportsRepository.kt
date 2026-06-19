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
    suspend fun fetchSummary(from: Instant, to: Instant): ReportsSummaryDto {
        val fromIso = ISO.format(from)
        val toIso = ISO.format(to)
        return try {
            val response = api.getReportsSummary(fromIso, toIso)
            if (response.isSuccessful) {
                response.body() ?: throw ApiException(response.code(), "Resposta vazia")
            } else if (response.code() == 404 && BuildConfig.DEBUG) {
                demoSummary(from, to)
            } else {
                throw ApiException(response.code(), response.errorBody()?.string() ?: "Falha ao carregar relatório")
            }
        } catch (e: HttpException) {
            if (e.code() == 404 && BuildConfig.DEBUG) {
                demoSummary(from, to)
            } else {
                throw ApiException(e.code(), e.message())
            }
        }
    }

    private fun demoSummary(from: Instant, to: Instant): ReportsSummaryDto {
        return ReportsSummaryDto(
            period = ReportsPeriodDto(from = ISO.format(from), to = ISO.format(to)),
            device = ReportsDeviceDto(id = "demo", name = "Maquininha (demo)"),
            summary = ReportsTotalsDto(
                saleCount = 28,
                voidCount = 1,
                grossTotal = 4320.0,
                voidTotal = 80.0,
                netTotal = 4240.0,
                averageTicket = 154.29,
            ),
            byPaymentMethod = listOf(
                ReportsPaymentRowDto("pix", "Pix", 12, 1680.0),
                ReportsPaymentRowDto("credit", "Crédito", 9, 1890.0),
                ReportsPaymentRowDto("debit", "Débito", 5, 590.0),
                ReportsPaymentRowDto("cash", "Dinheiro", 2, 160.0),
            ),
            byBrand = listOf(
                ReportsBrandRowDto("Visa", 8, 1420.0),
                ReportsBrandRowDto("Mastercard", 4, 780.0),
                ReportsBrandRowDto("Elo", 2, 280.0),
            ),
            topItems = listOf(
                ReportsItemRowDto("Copão Whisky", 18, 270.0),
                ReportsItemRowDto("Cerveja Lata", 14, 140.0),
                ReportsItemRowDto("Água", 9, 45.0),
            ),
        )
    }

    companion object {
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("America/Sao_Paulo"))
    }
}
