plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Version comes from the release tag (mobile-vX.Y.Z → -PmonkVersion=X.Y.Z in CI); local builds
// are 0.0.0-dev so a sideloaded dev build always sees the published release as newer.
val monkVersion: String = providers.gradleProperty("monkVersion").orNull
    ?: providers.environmentVariable("MONK_VERSION").orNull
    ?: "0.0.0-dev"
val monkVersionCode: Int = monkVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    .let { p -> (p.getOrElse(0) { 0 } * 1_000_000 + p.getOrElse(1) { 0 } * 1_000 + p.getOrElse(2) { 0 }).coerceAtLeast(1) }

android {
    namespace = "com.mdportnov.monk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mdportnov.monk"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = monkVersionCode
        versionName = monkVersion
    }

    buildFeatures {
        compose = true
    }

    // Stable release key: Android refuses an in-place update signed with a different key, and a
    // reinstall wipes the watch list. Wire it via env vars or gradle.properties; without it the
    // release build falls back to the debug key for local runs only.
    fun secret(name: String): String? = providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull
    val keystorePath = secret("MONK_KEYSTORE_FILE")
    // A versioned release signed with the debug key would break the update chain for every
    // installed user (signature mismatch → uninstall → lost watch list). Refuse loudly.
    check(keystorePath != null || monkVersion == "0.0.0-dev") {
        "Release keystore missing (MONK_KEYSTORE_FILE) for versioned build $monkVersion"
    }
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = secret("MONK_KEYSTORE_PASSWORD")
                keyAlias = secret("MONK_KEY_ALIAS")
                keyPassword = secret("MONK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: material-icons-extended alone is thousands of classes; shrinking is what keeps
            // every self-update download small.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}
