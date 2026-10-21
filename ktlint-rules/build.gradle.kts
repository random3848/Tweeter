import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Custom ktlint rules for the parts of the Android Kotlin style guide that ktlint's own rules
// don't cover. The root project's ktlintCheck and ktlintFormat tasks load them.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ktlint runs these rules on the Kotlin it ships with, which can be older than the app's. The
// rules depend on that version's standard library and use only its API, so they run on it.
val ktlintKotlin: String = libs.versions.ktlintKotlin.get()

kotlin {
    explicitApi()
    coreLibrariesVersion = ktlintKotlin
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        apiVersion.set(KotlinVersion.fromVersion(ktlintKotlin.substringBeforeLast('.')))
    }
}

dependencies {
    // ktlint provides these at runtime.
    compileOnly(libs.ktlint.cli.ruleset.core)
    compileOnly(libs.ktlint.rule.engine.core)

    testImplementation(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.ruleset.standard)
    testImplementation(libs.ktlint.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // ktlint logs through SLF4J; without a binding the test engine fails to start.
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    // The style guide tests load the repository's .editorconfig.
    systemProperty("repoRoot", rootDir.absolutePath)
}
