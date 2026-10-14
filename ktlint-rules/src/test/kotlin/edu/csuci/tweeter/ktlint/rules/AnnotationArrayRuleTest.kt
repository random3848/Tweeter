package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class AnnotationArrayRuleTest {
    private val assertThatCode = assertThatRule { AnnotationArrayRule() }

    @Test
    fun targetedArrayOfAnnotationsWithoutArguments_noViolation() {
        val code =
            """
            class Foo {
                @field:[JvmStatic Volatile]
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun separateAnnotations_noViolation() {
        val code =
            """
            class Foo {
                @field:Volatile
                @Suppress("unused")
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun arrayWithoutTarget_isReported() {
        val code =
            """
            class Foo {
                @[JvmStatic Volatile]
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "The '@[...]' syntax is used only with a use-site target: '@[JvmStatic Volatile]'"
        )
    }

    @Test
    fun arrayWithSingleAnnotation_isReported() {
        val code =
            """
            class Foo {
                @field:[Volatile]
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "The '@[...]' syntax combines two or more annotations: '@field:[Volatile]'"
        )
    }

    @Test
    fun arrayWithAnnotationArguments_isReported() {
        val code =
            """
            class Foo {
                @field:[Volatile Named("bar")]
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "The '@[...]' syntax combines only annotations without arguments: 'Named(\"bar\")'"
        )
    }

    @Test
    fun everyBrokenCondition_isReportedSeparately() {
        val code =
            """
            class Foo {
                @[Named("bar")]
                var bar: String? = null
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(
                2,
                5,
                "The '@[...]' syntax is used only with a use-site target: '@[Named(\"bar\")]'"
            ),
            LintViolation(
                2,
                5,
                "The '@[...]' syntax combines two or more annotations: '@[Named(\"bar\")]'"
            ),
            LintViolation(
                2,
                5,
                "The '@[...]' syntax combines only annotations without arguments: " +
                    "'Named(\"bar\")'"
            )
        )
    }
}
