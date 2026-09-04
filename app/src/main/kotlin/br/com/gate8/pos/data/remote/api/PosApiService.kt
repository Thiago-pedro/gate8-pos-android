package br.com.gate8.pos.data.remote.api

import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.LoginRequestDto
import br.com.gate8.pos.data.remote.dto.LoginResponseDto
import br.com.gate8.pos.data.remote.dto.CheckinRequestDto
import br.com.gate8.pos.data.remote.dto.CheckinResponseDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.CreateSaleResponseDto
import br.com.gate8.pos.data.remote.dto.VoidSaleRequestDto
import br.com.gate8.pos.data.remote.dto.VoidSaleResponseDto
import br.com.gate8.pos.data.remote.dto.CashierCloseRequestDto
import br.com.gate8.pos.data.remote.dto.CashierMovementRequestDto
import br.com.gate8.pos.data.remote.dto.CashierOperatorRequestDto
import br.com.gate8.pos.data.remote.dto.CashierOpenRequestDto
import br.com.gate8.pos.data.remote.dto.CashierStatusDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderRequestDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderResponseDto
import br.com.gate8.pos.data.remote.dto.ReconcileMpOrderRequestDto
import br.com.gate8.pos.data.remote.dto.ReconcileMpOrderResponseDto
import br.com.gate8.pos.data.remote.dto.MpOrderActionResponseDto
import br.com.gate8.pos.data.remote.dto.MpOrderStatusResponseDto
import br.com.gate8.pos.data.remote.dto.ReportsSummaryDto
import br.com.gate8.pos.data.remote.dto.CashlessBlockByCpfRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessCardLookupDto
import br.com.gate8.pos.data.remote.dto.CashlessCardResponseDto
import br.com.gate8.pos.data.remote.dto.CashlessPatchRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessReassignRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessRegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PosApiService {
    @POST("api/public/pos/login")
    suspend fun login(@Body body: LoginRequestDto): retrofit2.Response<LoginResponseDto>

    @GET("api/public/pos/catalog")
    @Headers("Cache-Control: no-cache")
    suspend fun getCatalog(): CatalogResponseDto

    @POST("api/public/pos/sales")
    suspend fun createSale(@Body body: CreateSaleRequestDto): Response<CreateSaleResponseDto>

    @POST("api/public/pos/sales/{id}/void")
    suspend fun voidSale(
        @Path("id") saleId: String,
        @Body body: VoidSaleRequestDto,
    ): Response<VoidSaleResponseDto>

    @POST("api/public/pos/checkin")
    suspend fun checkin(@Body body: CheckinRequestDto): Response<CheckinResponseDto>

    @GET("api/public/pos/reports/summary")
    suspend fun getReportsSummary(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("segment") segment: String,
    ): Response<ReportsSummaryDto>

    @GET("api/public/pos/cashier/status")
    suspend fun getCashierStatus(): Response<CashierStatusDto>

    @POST("api/public/pos/cashier/open")
    suspend fun openCashier(@Body body: CashierOpenRequestDto): Response<CashierStatusDto>

    @POST("api/public/pos/cashier/close")
    suspend fun closeCashier(@Body body: CashierCloseRequestDto): Response<CashierStatusDto>

    @POST("api/public/pos/cashier/withdrawal")
    suspend fun cashierWithdrawal(@Body body: CashierMovementRequestDto): Response<CashierStatusDto>

    @POST("api/public/pos/cashier/expense")
    suspend fun cashierExpense(@Body body: CashierMovementRequestDto): Response<CashierStatusDto>

    @PATCH("api/public/pos/cashier/operator")
    suspend fun updateCashierOperator(@Body body: CashierOperatorRequestDto): Response<CashierStatusDto>

    @POST("api/public/pos/payments/mp/orders")
    suspend fun createMpOrder(@Body body: CreateMpOrderRequestDto): Response<CreateMpOrderResponseDto>

    @GET("api/public/pos/payments/mp/orders/{id}")
    suspend fun getMpOrder(@Path("id") mpOrderId: String): Response<MpOrderStatusResponseDto>

    @POST("api/public/pos/payments/mp/orders/{id}/cancel")
    suspend fun cancelMpOrder(
        @Path("id") mpOrderId: String,
        @Header("X-Idempotency-Key") idempotencyKey: String,
    ): Response<MpOrderActionResponseDto>

    @POST("api/public/pos/payments/mp/orders/{id}/refund")
    suspend fun refundMpOrder(
        @Path("id") mpOrderId: String,
        @Header("X-Idempotency-Key") idempotencyKey: String,
    ): Response<MpOrderActionResponseDto>

    @POST("api/public/pos/payments/mp/orders/{id}/reconcile")
    suspend fun reconcileMpOrder(
        @Path("id") mpOrderId: String,
        @Body body: ReconcileMpOrderRequestDto = ReconcileMpOrderRequestDto(),
    ): Response<ReconcileMpOrderResponseDto>

    // --- Cashless ---

    @GET("api/public/pos/cashless/cards/{uid}")
    suspend fun getCashlessByUid(@Path("uid") uidHex: String): Response<CashlessCardLookupDto>

    @GET("api/public/pos/cashless/cards")
    suspend fun getCashlessByCpf(@Query("cpf") cpf: String): Response<CashlessCardLookupDto>

    @POST("api/public/pos/cashless/cards")
    suspend fun registerCashlessCard(@Body body: CashlessRegisterRequestDto): Response<CashlessCardResponseDto>

    @PATCH("api/public/pos/cashless/cards/{uid}")
    suspend fun patchCashlessCard(
        @Path("uid") uidHex: String,
        @Body body: CashlessPatchRequestDto,
    ): Response<CashlessCardResponseDto>

    @POST("api/public/pos/cashless/cards/block-by-cpf")
    suspend fun blockCashlessByCpf(@Body body: CashlessBlockByCpfRequestDto): Response<CashlessCardResponseDto>

    @POST("api/public/pos/cashless/cards/reassign")
    suspend fun reassignCashlessCard(@Body body: CashlessReassignRequestDto): Response<CashlessCardResponseDto>
}
