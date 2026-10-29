plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.antidroidvirus"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.antidroidvirus"
        minSdk = 30
        targetSdk = 36
        // Ogni build della CI ha un versionCode più alto della precedente: F-Droid la vede come aggiornamento.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 6
        versionName = "0.5.0"
    }

    signingConfigs {
        // Chiave privata dai secret del repository (SIGNING_KEYSTORE_BASE64, SIGNING_PASSWORD).
        // Senza secret, es. nelle PR da fork, la build release usa la chiave di debug del runner.
        val releaseKeystore = file("release.jks")
        if (releaseKeystore.exists() && System.getenv("SIGNING_PASSWORD") != null) {
            create("private") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "antidroidvirus"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Build non debuggable, ridotta e offuscata (solo le librerie: il codice dell'app resta leggibile nei log).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
