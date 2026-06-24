plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val packageCloudReadToken =
    localProperties.getProperty("packageCloudReadToken")
        ?: System.getenv("PACKAGECLOUD_READ_TOKEN")
        ?: ""
val stoneSdkLinked = packageCloudReadToken.isNotBlank()
val stoneSdkVersion: String = findProperty("stoneSdkVersion") as String? ?: "4.16.3"

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

val stonePixQrAuthorization =
    (localProperties.getProperty("stonePixQrAuthorization") ?: "").escapeForBuildConfig()
val stonePixQrProviderId =
    (localProperties.getProperty("stonePixQrProviderId") ?: "").escapeForBuildConfig()

apply(from = "${rootProject.projectDir}/positivo/positivo-signing-config.gradle")

android {
    namespace = "br.com.gate8.pos"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.gate8.pos.terminal"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://gate8.club/\"")
        buildConfigField("boolean", "STONE_SDK_LINKED", stoneSdkLinked.toString())
        buildConfigField("String", "STONE_PIX_QR_AUTHORIZATION", "\"$stonePixQrAuthorization\"")
        buildConfigField("String", "STONE_PIX_QR_PROVIDERID", "\"$stonePixQrProviderId\"")
    }

    flavorDimensions += listOf("environment", "model")
    productFlavors {
        create("mock") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "true")
        }
        create("stone") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "false")
        }

        create("generic") {
            dimension = "model"
            buildConfigField("String", "TERMINAL_MODEL", "\"generic\"")
        }
        create("positivoSeriesL") {
            dimension = "model"
            // Série L cobre Positivo L300 (L3) e L400 (L4) — mesma dependência stone-sdk-posandroid-positivo.
            buildConfigField("String", "TERMINAL_MODEL", "\"positivo_series_l\"")
            versionNameSuffix = "-positivo-l"
        }
        create("sunmi") {
            dimension = "model"
            buildConfigField("String", "TERMINAL_MODEL", "\"sunmi_p2\"")
            versionNameSuffix = "-sunmi-p2"
        }
        create("sunmiSeriesP") {
            dimension = "model"
            // Mesma dependência do Sunmi P2 (stone-sdk-posandroid-sunmi); cobre o Sunmi P2 A11 (Android 11).
            buildConfigField("String", "TERMINAL_MODEL", "\"sunmi_p2_a11\"")
            versionNameSuffix = "-sunmi-p2-a11"
        }
        create("tectoySeriesT") {
            dimension = "model"
            // Série T cobre a Tectoy T8 — dependência stone-sdk-posandroid-tectoy.
            buildConfigField("String", "TERMINAL_MODEL", "\"tectoy_t8\"")
            versionNameSuffix = "-tectoy-t8"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Build de PRODUCAO para terminais que aceitam assinatura debug:
        // Sunmi P2, Sunmi P2 A11, Tectoy T8 e generic. (No demo oficial da Stone, tectoySeriesT
        // e sunmi usam signingConfigs.debug; so a Positivo exige JKS propria -> positivoProd.)
        // Herda do debug (sem minify, com logs) e NAO recebe envconfig -> SDK Stone usa PRODUCAO.
        // Com -PprodCleanId o package fica LIMPO (br.com.gate8.pos.terminal) -> APK de homologacao.
        create("prod") {
            initWith(getByName("debug"))
            // initWith copia o applicationIdSuffix ".debug" do build type debug; sobrescrevemos:
            // com -PprodCleanId o package fica LIMPO (null), senao usa ".prod" (lado a lado c/ sandbox).
            applicationIdSuffix = if (project.hasProperty("prodCleanId")) null else ".prod"
            matchingFallbacks += listOf("release", "debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("stone") {
            if (stoneSdkLinked) {
                java.srcDir("src/stoneLive/kotlin")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/api_release.kotlin_module",
                "META-INF/client_release.kotlin_module",
            )
        }
    }
}

// Com 2 flavor dimensions, combinações flavor/buildType exigem configuration explícita (AGP).
if (stoneSdkLinked) {
    configurations {
        create("stoneGenericImplementation")
        create("stonePositivoSeriesLImplementation")
        create("stoneSunmiImplementation")
        create("stoneSunmiSeriesPImplementation")
        create("stoneTectoySeriesTImplementation")
        create("stoneGenericDebugImplementation")
        create("stonePositivoSeriesLDebugImplementation")
        create("stonePositivoSeriesLPositivoImplementation")
        create("stoneSunmiDebugImplementation")
        create("stoneSunmiSeriesPDebugImplementation")
        create("stoneTectoySeriesTDebugImplementation")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.security.crypto)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    // Geração de QR Code do ingresso (impressão na maquininha). Core puro Java, sem câmera.
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    if (stoneSdkLinked) {
        val envConfig = "br.com.stone.sdk.android:envconfig:$stoneSdkVersion"
        "stoneGenericDebugImplementation"(envConfig)
        "stonePositivoSeriesLDebugImplementation"(envConfig)
        "stonePositivoSeriesLPositivoImplementation"(envConfig)
        "stoneSunmiDebugImplementation"(envConfig)
        "stoneSunmiSeriesPDebugImplementation"(envConfig)
        "stoneTectoySeriesTDebugImplementation"(envConfig)

        "stoneGenericImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stoneGenericImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")

        "stonePositivoSeriesLImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stonePositivoSeriesLImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")
        "stonePositivoSeriesLImplementation"(
            "br.com.stone:stone-sdk-posandroid-positivo:$stoneSdkVersion",
        )

        "stoneSunmiImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stoneSunmiImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")
        "stoneSunmiImplementation"("br.com.stone:stone-sdk-posandroid-sunmi:$stoneSdkVersion")

        // Sunmi P2 A11: mesma lib de provider do Sunmi P2.
        "stoneSunmiSeriesPImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stoneSunmiSeriesPImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")
        "stoneSunmiSeriesPImplementation"("br.com.stone:stone-sdk-posandroid-sunmi:$stoneSdkVersion")

        // Tectoy T8: provider especifico da Tectoy.
        "stoneTectoySeriesTImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stoneTectoySeriesTImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")
        "stoneTectoySeriesTImplementation"("br.com.stone:stone-sdk-posandroid-tectoy:$stoneSdkVersion")
    }
}
