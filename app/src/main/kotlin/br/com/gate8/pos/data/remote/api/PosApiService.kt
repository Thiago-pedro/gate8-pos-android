package br.com.gate8.pos.data.remote.api

import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.LoginRequestDto
import br.com.gate8.pos.data.remote.dto.LoginResponseDto
import br.com.gate8.pos.data.remote.dto.CheckinRequestDto
import br.com.gate8.pos.data.remote.dto.CheckinResponseDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.CreateSaleResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PosApiService {
    @POST("api/public/pos/login")
    suspend fun login(@Body body: LoginRequestDto): retrofit2.Response<LoginResponseDto>

    @GET("api/public/pos/catalog")
    suspend fun getCatalog(): CatalogResponseDto

    @POST("api/public/pos/sales")
    suspend fun createSale(@Body body: CreateSaleRequestDto): Response<CreateSaleResponseDto>

    @POST("api/public/pos/checkin")
    suspend fun checkin(@Body body: CheckinRequestDto): Response<CheckinResponseDto>
}
