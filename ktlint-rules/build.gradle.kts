import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Custom ktlint rules for the parts of the Android Kotlin style guide that ktlint's own rules
// don't cover. The root project's ktlintCheck and ktlintFormat tasks load them.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
    // ktlint logs through SLF4J; without a binding the test engine fails to start.
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    // The style guide tests load the repository's .editorconfig.
    systemProperty("repoRoot", rootDir.absolutePath)
}
