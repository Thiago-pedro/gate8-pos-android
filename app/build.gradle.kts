import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

fun loadLocalProp(name: String): String {
    val props = Properties()
    listOf("local.properties", "local.properties.cielo.txt").forEach { fileName ->
        val file = rootProject.file(fileName)
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }
    return props.getProperty(name, "").orEmpty()
}

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

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
        buildConfigField("String", "CIELO_CLIENT_ID", "\"\"")
        buildConfigField("String", "CIELO_ACCESS_TOKEN", "\"\"")
        buildConfigField("String", "CIELO_MERCHANT_ID", "\"\"")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("mock") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "true")
        }
        create("mercadopago") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "false")
            versionNameSuffix = "-mp"
        }
        create("cielo") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "false")
            versionNameSuffix = "-cielo"
            minSdk = 24
            targetSdk = 29
            val clientId = escapeBuildConfigString(loadLocalProp("CIELO_CLIENT_ID"))
            val accessToken = escapeBuildConfigString(loadLocalProp("CIELO_ACCESS_TOKEN"))
            val merchantId = escapeBuildConfigString(loadLocalProp("CIELO_MERCHANT_ID"))
            buildConfigField("String", "CIELO_CLIENT_ID", "\"$clientId\"")
            buildConfigField("String", "CIELO_ACCESS_TOKEN", "\"$accessToken\"")
            buildConfigField("String", "CIELO_MERCHANT_ID", "\"$merchantId\"")
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
                "proguard-rules.pro",
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/api_release.kotlin_module",
                "META-INF/client_release.kotlin_module",
            )
        }
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
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
