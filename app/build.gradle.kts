import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        // BuildConfig.VERSION_NAME names the app's version in the map's tile requests.
        buildConfig = true
        compose = true
    }
}

// ktlint parses Kotlin as the version it ships with, which can be older than the compiler. The
// app is limited to that version's language, so every file it can compile, ktlint can check.
// That also limits it to that version's standard library, where kotlin.time's Instant and Clock
// (the map library's cache times) are still marked experimental, so the files using them opt in.
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    // The map's GeoJSON features hold their properties as JSON objects, and its camera is saved
    // as JSON.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.compose)
    // The map engine draws with Vulkan; the library needs exactly one renderer at runtime.
    runtimeOnly(libs.maplibre.compose.runtime.vulkan)
    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    // Runs the tile downloader's retry waits in virtual time.
    testImplementation(libs.kotlinx.coroutines.test)
    // Serves canned HTTP responses to the tile downloader's tests.
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
