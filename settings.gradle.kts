import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val localProperties = Properties().apply {
    val file = File(rootDir, "local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val packageCloudReadToken =
    localProperties.getProperty("packageCloudReadToken")
        ?: System.getenv("PACKAGECLOUD_READ_TOKEN")
        ?: ""

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (packageCloudReadToken.isNotBlank()) {
            maven {
                url = uri("https://packagecloud.io/priv/$packageCloudReadToken/stone/pos-android/maven2")
            }
        }
    }
}

rootProject.name = "Gate8POS"
include(":app")
