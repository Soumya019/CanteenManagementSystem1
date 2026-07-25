import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// The Picovoice AccessKey never goes in git. Put it in local.properties as
// picovoice.accessKey=..., or export PICOVOICE_ACCESS_KEY. With no key the app
// still runs: it falls back to the platform SpeechRecognizer wake engine.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val picovoiceAccessKey: String =
    localProperties.getProperty("picovoice.accessKey")
        ?: System.getenv("PICOVOICE_ACCESS_KEY")
        ?: ""

android {
    namespace = "com.soumya.voicepilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soumya.voicepilot"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceAccessKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.porcupine.android)

    testImplementation(libs.junit)
}
