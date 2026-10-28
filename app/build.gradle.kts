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
        versionCode = 4
        versionName = "0.3.0"
    }

    signingConfigs {
        // Chiave di debug pubblica e fissa (stessa convenzione di webradiodroid): ogni build CI si
        // installa sopra la precedente. Se i secret SIGNING_* sono impostati si usa la chiave privata.
        create("ciDebug") {
            storeFile = file("ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("ciDebug")
        }
        debug {
            signingConfig = signingConfigs.getByName("ciDebug")
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
