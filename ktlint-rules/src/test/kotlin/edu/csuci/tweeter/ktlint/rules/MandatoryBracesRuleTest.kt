package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class MandatoryBracesRuleTest {
    private val assertThatCode = assertThatRule { MandatoryBracesRule() }

    @Test
    fun bracedLoops_noViolation() {
        val code =
            """
            fun foo(xs: List<Int>) {
                var i = 3
                for (x in xs) {
                    println(x)
                }
                while (i > 0) {
                    i--
                }
                do {
                    i++
                } while (i < 3)
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun singleLineForBody_isBraced() {
        val code =
            """
            fun foo(xs: List<Int>) {
                for (x in xs) println(x)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(xs: List<Int>) {
                for (x in xs) {
                    println(x)
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 19, "The body of a 'for' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyForBody_isBraced() {
        val code =
            """
            fun foo(xs: List<Int>) {
                for (y in xs);
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(xs: List<Int>) {
                for (y in xs) {
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 18, "The body of a 'for' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyWhileBody_isBraced() {
        val code =
            """
            fun foo(n: Int) {
                var x = n
                while (x > 0);
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(n: Int) {
                var x = n
                while (x > 0) {
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 18, "The body of a 'while' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyWhileBodyFollowedByCode_isBracedAndCodeWrapped() {
        val code =
            """
            fun foo(n: Int) {
                var x = n
                while (x > 0) ; x--
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(n: Int) {
                var x = n
                while (x > 0) {
                }
                x--
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 19, "The body of a 'while' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun missingDoWhileBody_isBraced() {
        val code =
            """
            fun foo(n: Int) {
                var x = n
                do while (x > 0)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(n: Int) {
                var x = n
                do {
                } while (x > 0)
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 8, "The body of a 'do-while' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun emptyLoopBodyAfterComment_isReportedWithoutAutoCorrect() {
        val code =
            """
            fun foo(xs: List<Int>) {
                for (y in xs) /* nothing */ ;
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(
                2,
                33,
                "The body of a 'for' loop is enclosed in braces"
            )
    }

    @Test
    fun forBodyOnNextLine_isBraced() {
        val code =
            """
            fun foo(xs: List<Int>) {
                for (x in xs)
                    println(x)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(xs: List<Int>) {
                for (x in xs) {
                    println(x)
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 9, "The body of a 'for' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun singleLineWhileBody_isBraced() {
        val code =
            """
            fun foo(n: Int) {
                var i = n
                while (i > 0) i--
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(n: Int) {
                var i = n
                while (i > 0) {
                    i--
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 19, "The body of a 'while' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun singleLineDoWhileBody_isBracedAndKeepsWhileOnClosingBraceLine() {
        val code =
            """
            fun foo(n: Int) {
                var i = n
                do i-- while (i > 0)
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(n: Int) {
                var i = n
                do {
                    i--
                } while (i > 0)
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 8, "The body of a 'do-while' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun trailingCommentOfLoopBody_movesIntoBlock() {
        val code =
            """
            fun foo(xs: List<Int>) {
                for (x in xs) println(x) // print all
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(xs: List<Int>) {
                for (x in xs) {
                    println(x) // print all
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 19, "The body of a 'for' loop is enclosed in braces")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun singleLineWhenBranches_noViolation() {
        val code =
            """
            fun foo(input: Int): Int {
                return when (input) {
                    0 -> return 1
                    1 -> 2 // comment after a single-line branch
                    else -> {
                        println("other")
                        3
                    }
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun whenBranchWithExpressionOnNextLine_isBraced() {
        val code =
            """
            fun foo(input: Int) {
                when (input) {
                    0 ->
                        println("zero")
                    else -> return
                }
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(input: Int) {
                when (input) {
                    0 -> {
                        println("zero")
                    }
                    else -> return
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                4,
                13,
                "A 'when' branch that does not fit on one line is enclosed in braces"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun multiLineElseBranch_isBraced() {
        val code =
            """
            fun foo(input: Int) {
                when (input) {
                    0 -> return
                    else ->
                        println("other")
                }
            }
            """.trimIndent()
        val formattedCode =
            """
            fun foo(input: Int) {
                when (input) {
                    0 -> return
                    else -> {
                        println("other")
                    }
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                5,
                13,
                "A 'when' branch that does not fit on one line is enclosed in braces"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun whenBranchWithMultiLineExpression_isReported() {
        val code =
            """
            fun foo(input: Int) {
                when (input) {
                    0 -> println(
                        "zero"
                    )
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                3,
                14,
                "A 'when' branch that does not fit on one line is enclosed in braces"
            )
    }

    @Test
    fun ifStatement_isNotThisRulesJob() {
        val code =
            """
            fun foo(input: Int) {
                if (input > 0) println(input)
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }
}
