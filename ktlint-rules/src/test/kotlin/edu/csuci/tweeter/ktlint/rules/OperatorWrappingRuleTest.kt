package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class OperatorWrappingRuleTest {
    private val assertThatCode = assertThatRule { OperatorWrappingRule() }

    @Test
    fun breakAfterOperators_noViolation() {
        val code =
            """
            val str = "a" +
                "b"

            fun foo(a: String?, b: Boolean, c: Boolean): Any =
                a ?:
                    b &&
                    c ||
                    (a as
                        String) in
                    setOf("x")
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun breakBeforeDotSafeAccessAndDoubleColon_noViolation() {
        val code =
            """
            fun foo(a: String?) = a
                ?.trim()
                .orEmpty()
                .let(String::length)

            fun bar(a: String) = a.
                length
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun elvisStartingLine_isMovedToEndOfPreviousLine() {
        val code =
            """
            fun foo(a: String?): String {
                val b = a
                    ?: "default"
                return b
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(a: String?): String {
                val b = a ?:
                    "default"
                return b
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 9, "Line break goes after the operator '?:', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun elvisAfterComment_isMovedBeforeComment() {
        val code =
            """
            fun foo(a: String?) = a // fallback below
                ?: "default"
            """.trimIndent()
        val formattedCode =
            """
            fun foo(a: String?) = a ?: // fallback below
                "default"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 5, "Line break goes after the operator '?:', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun logicalAndStartingLine_isMovedToEndOfPreviousLine() {
        val code =
            """
            fun foo(a: Boolean, b: Boolean) =
                a
                    && b
            """.trimIndent()
        val formattedCode =
            """
            fun foo(a: Boolean, b: Boolean) =
                a &&
                    b
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 9, "Line break goes after the operator '&&', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun plusStartingLineInParentheses_isMovedToEndOfPreviousLine() {
        val code =
            """
            val sum = (1
                + 2)
            """.trimIndent()
        val formattedCode =
            """
            val sum = (1 +
                2)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 5, "Line break goes after the operator '+', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun infixFunctionStartingLine_isMovedToEndOfPreviousLine() {
        val code =
            """
            val bits = (1
                shl 2)
            """.trimIndent()
        val formattedCode =
            """
            val bits = (1 shl
                2)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                2,
                5,
                "Line break goes after the infix function 'shl', not before it"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun asStartingLine_isMovedToEndOfPreviousLine() {
        val code =
            """
            fun foo(x: Any) = x
                as String
            """.trimIndent()
        val formattedCode =
            """
            fun foo(x: Any) = x as
                String
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 5, "Line break goes after the operator 'as', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun notIsStartingLine_isMovedToEndOfPreviousLine() {
        val code =
            """
            fun foo(x: Any) = (x
                !is String)
            """.trimIndent()
        val formattedCode =
            """
            fun foo(x: Any) = (x !is
                String)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 5, "Line break goes after the operator '!is', not before it")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun safeAccessEndingLine_isMovedToStartOfNextLine() {
        val code =
            """
            fun foo(a: String?) = a?.
                length
            """.trimIndent()
        val formattedCode =
            """
            fun foo(a: String?) = a
                ?.length
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(1, 24, "Line break goes before '?.', not after it")
            .isFormattedAs(formattedCode)
    }
}
