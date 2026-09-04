package br.com.gate8.pos.di

import androidx.room.Room
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.local.db.Gate8Database
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.payment.MpOrderReconciliation
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.prefs.LastSaleStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.interceptor.AuthInterceptor
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.CheckinRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.core.session.SessionEvents
import br.com.gate8.pos.core.time.ServerClock
import br.com.gate8.pos.data.repository.LoginRepository
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.CashlessAccountRepository
import br.com.gate8.pos.data.repository.ReportsRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import br.com.gate8.pos.ui.cashier.CashierViewModel
import br.com.gate8.pos.ui.cashless.CashlessViewModel
import br.com.gate8.pos.ui.config.SetupViewModel
import br.com.gate8.pos.ui.login.LoginViewModel
import br.com.gate8.pos.ui.pdv.PdvViewModel
import br.com.gate8.pos.ui.products.ProductsViewModel
import br.com.gate8.pos.ui.checkin.CheckinViewModel
import br.com.gate8.pos.ui.pending.PendingViewModel
import br.com.gate8.pos.ui.refund.RefundViewModel
import br.com.gate8.pos.ui.reports.ReportsViewModel
import android.content.Context
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

val appModule = module {
    single { json }
    single { ServerClock() }
    single { SessionEvents() }
    single { DeviceConfigStore(androidContext()) }
    single {
        LastSaleStore(
            androidContext().getSharedPreferences("gate8_pos_last_sale", Context.MODE_PRIVATE),
            get(),
        )
    }
    single { SaleAdminService(get(), get(), get(), get()) }
    single { PendingSaleSync(get(), get()) }
    single { MpOrderReconciliation(get()) }

    single {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get(), get()))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    single {
        val config = get<DeviceConfigStore>()
        val baseUrl = config.getBaseUrl() ?: BuildConfig.DEFAULT_BASE_URL
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(get())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    single { get<Retrofit>().create(PosApiService::class.java) }

    single {
        Room.databaseBuilder(androidContext(), Gate8Database::class.java, "gate8_pos.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    single { get<Gate8Database>().catalogDao() }
    single { get<Gate8Database>().pendingSaleDao() }
    single { get<Gate8Database>().cashlessAccountDao() }
    single { get<Gate8Database>().cashlessMovementDao() }

    single { CatalogRepository(get(), get(), get(), get(), get()) }
    single { SaleRepository(get(), get(), get()) }
    single { CheckinRepository(get()) }
    single { LoginRepository(get(), get()) }
    single { ReportsRepository(get()) }
    single { CashierRepository(get(), get()) }
    single { CashlessAccountRepository(get(), get(), get(), get()) }

    viewModel { LoginViewModel(androidApplication(), get(), get(), get()) }
    viewModel { SetupViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RefundViewModel(get(), get()) }
    viewModel { ReportsViewModel(get(), get(), get(), get(), get()) }
    viewModel { CashierViewModel(get(), get(), get()) }
    viewModel {
        CashlessViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), BuildConfig.DEBUG,
        )
    }
    viewModel { PdvViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), BuildConfig.DEBUG) }
    viewModel {
        ProductsViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), BuildConfig.DEBUG,
        )
    }
    viewModel { CheckinViewModel(get()) }
    viewModel { PendingViewModel(get()) }
}
