import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "edu.csuci.tweeter"
    compileSdk = 37

    defaultConfig {
        applicationId = "edu.csuci.tweeter"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// ktlint parses Kotlin as the version it ships with, which can be older than the compiler. The
// app is limited to that version's language, so every file it can compile, ktlint can check.
// That also limits it to that version's standard library, where kotlin.time's Instant and Clock
// (the map library's cache times) are still marked experimental.
kotlin {
    compilerOptions {
        languageVersion.set(
            KotlinVersion.fromVersion(libs.versions.ktlintKotlin.get().substringBeforeLast('.'))
        )
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.maplibre.compose)
    // The map engine draws with Vulkan; the library needs exactly one renderer at runtime.
    runtimeOnly(libs.maplibre.compose.runtime.vulkan)
    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
    // Serves canned HTTP responses to the tile downloader's tests.
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
