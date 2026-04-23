import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.paperkeep.core.network"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject API base URL and TLS pin from properties files
        val apiProps = Properties().apply {
            file("api_base_url.properties").takeIf { it.exists() }
                ?.inputStream()?.use(::load)
        }
        val pinsProps = Properties().apply {
            file("pins.properties").takeIf { it.exists() }
                ?.inputStream()?.use(::load)
        }
        buildConfigField("String", "API_BASE_URL",
            "\"${apiProps.getProperty("API_BASE_URL", "https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com/v1")}\"")
        buildConfigField("String", "TLS_PIN_SHA256",
            "\"${pinsProps.getProperty("TLS_PIN_SHA256", "")}\"")
        buildConfigField("String", "TLS_PIN_HOST",
            "\"${pinsProps.getProperty("API_HOST", "")}\"")
        buildConfigField("String", "OTA_CONFIG_URL",
            "\"${apiProps.getProperty("OTA_CONFIG_URL",
                "https://Paperkeep-staging-vault-203a9e83.s3.ap-south-1.amazonaws.com/ota/config.json")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Ktor (Phase 4+ — declared here, used only when sync is enabled)
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.android)

    // EncryptedSharedPreferences for TokenStore
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
