package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class FileNameRuleTest {
    private val assertThatCode = assertThatRule { FileNameRule() }

    @Test
    fun singleClassInFileNamedAfterIt_noViolation() {
        assertThatCode("class MyClass\n")
            .asFileWithPath("/project/src/main/kotlin/MyClass.kt")
            .hasNoLintViolations()
    }

    @Test
    fun singleObjectInFileNamedAfterIt_noViolation() {
        assertThatCode("object Registry\n")
            .asFileWithPath("/project/src/main/kotlin/Registry.kt")
            .hasNoLintViolations()
    }

    @Test
    fun singleClassInFileWithOtherName_isReported() {
        assertThatCode("class MyClass\n")
            .asFileWithPath("/project/src/main/kotlin/Classes.kt")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "A file holding a single class is named after it: 'Classes.kt' should be " +
                    "'MyClass.kt'"
            )
    }

    @Test
    fun singleClassInFileDifferingOnlyInCase_isReported() {
        assertThatCode("interface MyClass\n")
            .asFileWithPath("/project/src/main/kotlin/myClass.kt")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "A file holding a single class is named after it: 'myClass.kt' should be " +
                    "'MyClass.kt'"
            )
    }

    @Test
    fun classWithExtensionFunctionInPascalCaseFile_noViolation() {
        val code =
            """
            class Bar

            fun Runnable.toBar(): Bar = Bar()
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/main/kotlin/Bar.kt")
            .hasNoLintViolations()
    }

    @Test
    fun functionsInPascalCaseFile_noViolation() {
        val code =
            """
            fun <T, O> Set<T>.map(func: (T) -> O): List<O> = map(func)

            fun <T, O> List<T>.map(func: (T) -> O): List<O> = map(func)
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/main/kotlin/Map.kt")
            .hasNoLintViolations()
    }

    @Test
    fun functionsInPluralCamelCaseFile_noViolation() {
        val code =
            """
            fun MyClass.process() = Unit

            fun MyResult.print() = Unit
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/main/kotlin/extensions.kt")
            .hasNoLintViolations()
    }

    @Test
    fun singleFunctionInPluralCamelCaseFile_noViolation() {
        assertThatCode("fun String.shout() = uppercase()\n")
            .asFileWithPath("/project/src/main/kotlin/strings.kt")
            .hasNoLintViolations()
    }

    @Test
    fun functionsInSingularCamelCaseFile_isReported() {
        val code =
            """
            fun MyClass.process() = Unit

            fun MyResult.print() = Unit
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/main/kotlin/extension.kt")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "File names are PascalCase, or camelCase if plural: 'extension.kt'"
            )
    }

    @Test
    fun singleFunctionInSnakeCaseFile_isReported() {
        assertThatCode("fun String.shout() = uppercase()\n")
            .asFileWithPath("/project/src/main/kotlin/string_utils.kt")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "File names are PascalCase, or camelCase if plural: 'string_utils.kt'"
            )
    }

    @Test
    fun fileWithoutDeclarations_noViolation() {
        assertThatCode("package com.example\n")
            .asFileWithPath("/project/src/main/kotlin/not_a_name.kt")
            .hasNoLintViolations()
    }

    @Test
    fun script_noViolation() {
        assertThatCode("fun helper() = Unit\n")
            .asFileWithPath("/project/build_logic.gradle.kts")
            .hasNoLintViolations()
    }

    @Test
    fun singleClassInMultiDotFileNamedAfterIt_noViolation() {
        assertThatCode("class Platform\n")
            .asFileWithPath("/project/src/androidMain/kotlin/Platform.android.kt")
            .hasNoLintViolations()
    }

    @Test
    fun functionsInMultiDotPascalCaseFile_noViolation() {
        val code =
            """
            fun first() = 1

            fun second() = 2
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/jvmMain/kotlin/PlatformUtils.jvm.kt")
            .hasNoLintViolations()
    }
}
