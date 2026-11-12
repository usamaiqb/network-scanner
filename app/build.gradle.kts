import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.detekt)
}

// Load signing config from keystore.properties (local) or environment variables (CI)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val releaseStoreFile: File? = when {
    keystorePropertiesFile.exists() -> rootProject.file(keystoreProperties["storeFile"] as String)
    System.getenv("KEYSTORE_PATH") != null -> file(System.getenv("KEYSTORE_PATH")!!)
    else -> null
}
val releaseStorePassword: String? = keystoreProperties["storePassword"] as? String ?: System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = keystoreProperties["keyAlias"] as? String ?: System.getenv("KEY_ALIAS")
val releaseKeyPassword: String? = keystoreProperties["keyPassword"] as? String ?: System.getenv("KEY_PASSWORD")

android {
    namespace = "com.networkscanner.app"
    compileSdk = 36

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.networkscanner.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "1.1.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Marks the build in versionName, which is what the Settings identity row shows.
            // Doing it here rather than in the UI keeps it out of release builds by construction.
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Bundle native debug symbols into the AAB so Play can symbolicate
            // native crashes/ANRs (fixes the "no debug symbols uploaded" warning).
            ndk {
                debugSymbolLevel = "FULL"
            }
            if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    lint {
        // Suppressions and severities live in lint.xml so the IDE, the CLI and CI all
        // read the same policy. Each entry there carries its reason.
        lintConfig = file("lint.xml")
    }
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    // Findings that predate detekt, so the rules bind to new code without the change that
    // introduced them rewriting the app. This file is meant to shrink and then be deleted —
    // regenerating it wholesale during a refactor turns temporary debt into permanent debt.
    baseline = file("$rootDir/config/detekt/baseline.xml")
    // Our config only states where we differ, so detekt's own defaults still apply.
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
    reports {
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
        txt.required.set(false)
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + M3 Expressive (alpha override for expressive APIs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (for type-safe navigation)
    implementation(libs.kotlinx.serialization.json)

    // Preferences (still needed for SharedPreferences access)
    implementation(libs.androidx.preference.ktx)

    // Shizuku (privileged ARP table access)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Kotlin static analysis, including the Compose rule set
    detektPlugins(libs.detekt.compose.rules)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

easylauncher {
    buildTypes {
        register("debug") {
            filters(customRibbon(ribbonColor = "#FFB951", labelColor = "#2B1B00"))
        }
    }
}
