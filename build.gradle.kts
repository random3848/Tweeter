// Plugins used by subprojects are declared here once so they all load in the same classloader.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

// ktlint runs from the command line tool's Maven artifact, with this repo's own rule set (the
// :ktlint-rules project) on its classpath. The rules are configured in .editorconfig.
val ktlint: Configuration = configurations.create("ktlint") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    ktlint(libs.ktlint.cli)
    ktlint(project(":ktlint-rules"))
}

// Gradle-generated sources under build/ are not ours to format.
val ktlintPatterns = listOf("**/*.kt", "**/*.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks every Kotlin file against the Android Kotlin style guide."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(listOf("--relative") + ktlintPatterns)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fixes the Android Kotlin style guide violations that ktlint can fix on its own."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(listOf("--relative", "--format") + ktlintPatterns)
}
