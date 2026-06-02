package br.com.gate8.pos.di

import androidx.room.Room
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.local.db.Gate8Database
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.interceptor.AuthInterceptor
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.CheckinRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.core.time.ServerClock
import br.com.gate8.pos.mock.di.mockFlavorModule
import br.com.gate8.pos.stone.di.stoneFlavorModule
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import br.com.gate8.pos.ui.config.SetupViewModel
import br.com.gate8.pos.ui.pdv.PdvViewModel
import br.com.gate8.pos.ui.checkin.CheckinViewModel
import br.com.gate8.pos.ui.pending.PendingViewModel
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

val appModule = module {
    single { json }
    single { ServerClock() }
    single { DeviceConfigStore(androidContext()) }

    single {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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

    single { CatalogRepository(get(), get(), get(), get()) }
    single { SaleRepository(get(), get(), get()) }
    single { CheckinRepository(get()) }

    viewModel { SetupViewModel(get()) }
    viewModel { PdvViewModel(get(), get(), get(), get(), get(), get(), BuildConfig.DEBUG) }
    viewModel { CheckinViewModel(get()) }
    viewModel { PendingViewModel(get()) }
}

fun flavorModules() = if (BuildConfig.USE_MOCK_PAYMENT) {
    listOf(mockFlavorModule)
} else {
    listOf(stoneFlavorModule)
}
