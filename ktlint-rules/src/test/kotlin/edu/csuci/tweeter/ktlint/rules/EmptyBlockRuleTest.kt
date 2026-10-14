package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class EmptyBlockRuleTest {
    private val assertThatCode = assertThatRule { EmptyBlockRule() }

    @Test
    fun krStyleEmptyCatchBlock_noViolation() {
        val code =
            """
            fun foo() {
                try {
                    doSomething()
                } catch (e: Exception) {
                } // Okay
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun emptyCatchBlockOnOneLine_isReported() {
        val code =
            """
            fun foo() {
                try {
                    doSomething()
                } catch (e: Exception) {} // WRONG!
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo() {
                try {
                    doSomething()
                } catch (e: Exception) {
                } // WRONG!
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(4, 28, "An empty block is written K&R style: '{}'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyFunctionBodyWithSpace_isReported() {
        val code =
            """
            class Foo {
                fun bar() { }
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 15, "An empty block is written K&R style: '{ }'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyIfAndElseBodies_areReported() {
        val code =
            """
            fun foo(x: Boolean) {
                if (x) {} else {}
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(x: Boolean) {
                if (x) {
                } else {
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolations(
                LintViolation(2, 12, "An empty block is written K&R style: '{}'"),
                LintViolation(2, 20, "An empty block is written K&R style: '{}'")
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun emptyLambda_noViolation() {
        val code =
            """
            val noop = {}
            val alsoNoop: () -> Unit = { }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun blockWithComment_noViolation() {
        val code =
            """
            fun foo() { /* intentionally empty */ }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun emptyClassBody_noViolation() {
        val code =
            """
            class Foo {}
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }
}
