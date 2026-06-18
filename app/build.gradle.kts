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
        create("stoneGenericDebugImplementation")
        create("stonePositivoSeriesLDebugImplementation")
        create("stonePositivoSeriesLPositivoImplementation")
        create("stoneSunmiDebugImplementation")
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    if (stoneSdkLinked) {
        val envConfig = "br.com.stone.sdk.android:envconfig:$stoneSdkVersion"
        "stoneGenericDebugImplementation"(envConfig)
        "stonePositivoSeriesLDebugImplementation"(envConfig)
        "stonePositivoSeriesLPositivoImplementation"(envConfig)
        "stoneSunmiDebugImplementation"(envConfig)

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
    }
}
