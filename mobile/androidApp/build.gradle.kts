plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.mdportnov.monk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mdportnov.monk"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    // Stable release key: Android refuses an in-place update signed with a different key, and a
    // reinstall wipes the watch list. Wire it via env vars or gradle.properties; without it the
    // release build falls back to the debug key for local runs only.
    val keystorePath = System.getenv("MONK_KEYSTORE_FILE")
        ?: providers.gradleProperty("MONK_KEYSTORE_FILE").orNull
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MONK_KEYSTORE_PASSWORD") ?: providers.gradleProperty("MONK_KEYSTORE_PASSWORD").orNull
                keyAlias = System.getenv("MONK_KEY_ALIAS") ?: providers.gradleProperty("MONK_KEY_ALIAS").orNull
                keyPassword = System.getenv("MONK_KEY_PASSWORD") ?: providers.gradleProperty("MONK_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
}
