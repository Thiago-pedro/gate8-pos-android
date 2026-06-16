plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

val localProperties = java.util.Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val packageCloudReadToken =
    localProperties.getProperty("packageCloudReadToken")
        ?: System.getenv("PACKAGECLOUD_READ_TOKEN")
        ?: ""
val stoneSdkLinked = packageCloudReadToken.isNotBlank()
val stoneSdkVersion: String = findProperty("stoneSdkVersion") as String? ?: "4.16.3"
val stoneTerminal: String =
    localProperties.getProperty("stoneTerminal")
        ?: findProperty("stoneTerminal") as String?
        ?: ""

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
    }

    flavorDimensions += "environment"
    productFlavors {
        create("mock") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "true")
        }
        create("stone") {
            dimension = "environment"
            buildConfigField("boolean", "USE_MOCK_PAYMENT", "false")
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
        "stoneImplementation"("br.com.stone:stone-sdk:$stoneSdkVersion")
        "stoneImplementation"("br.com.stone:stone-sdk-posandroid:$stoneSdkVersion")
        when (stoneTerminal.lowercase()) {
            "positivo" -> "stoneImplementation"("br.com.stone:stone-sdk-posandroid-positivo:$stoneSdkVersion")
            "sunmi" -> "stoneImplementation"("br.com.stone:stone-sdk-posandroid-sunmi:$stoneSdkVersion")
            "gertec" -> "stoneImplementation"("br.com.stone:stone-sdk-posandroid-gertec:$stoneSdkVersion")
            "ingenico" -> "stoneImplementation"("br.com.stone:stone-sdk-posandroid-ingenico:$stoneSdkVersion")
            "tectoy" -> "stoneImplementation"("br.com.stone:stone-sdk-posandroid-tectoy:$stoneSdkVersion")
        }
    }
}
