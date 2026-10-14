package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class WhitespaceCharactersRuleTest {
    private val assertThatCode = assertThatRule { WhitespaceCharactersRule() }

    @Test
    fun spacesAndEscapes_noViolation() {
        val code =
            """
            // A comment with spaces.
            val a = "x\ty\u00a0z"
            val b = '\t'
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun tabInLeadingIndentation_noViolation() {
        assertThatCode("fun f() {\n\treturn\n}\n").hasNoLintViolations()
    }

    @Test
    fun tabInString_isEscaped() {
        assertThatCode("val a = \"x\ty\"\n")
            .hasLintViolation(
                1,
                11,
                "ASCII space is the only whitespace character allowed: found U+0009"
            ).isFormattedAs("val a = \"x\\ty\"\n")
    }

    @Test
    fun severalCharactersInString_areEscaped() {
        assertThatCode("val a = \"a\tb\u2003c\"\n")
            .hasLintViolations(
                LintViolation(
                    1,
                    11,
                    "ASCII space is the only whitespace character allowed: found U+0009"
                ),
                LintViolation(
                    1,
                    13,
                    "ASCII space is the only whitespace character allowed: found U+2003"
                )
            ).isFormattedAs("val a = \"a\\tb\\u2003c\"\n")
    }

    @Test
    fun noBreakSpaceInCharacterLiteral_isEscaped() {
        assertThatCode("val c = '\u00a0'\n")
            .hasLintViolation(
                1,
                10,
                "ASCII space is the only whitespace character allowed: found U+00A0"
            ).isFormattedAs("val c = '\\u00a0'\n")
    }

    @Test
    fun tabInRawString_isReportedWithoutAutoCorrect() {
        assertThatCode("val a =\n    \"\"\"\n\tx\ty\n    \"\"\"\n")
            .hasLintViolationsWithoutAutoCorrect(
                LintViolation(
                    3,
                    1,
                    "ASCII space is the only whitespace character allowed: found U+0009"
                ),
                LintViolation(
                    3,
                    3,
                    "ASCII space is the only whitespace character allowed: found U+0009"
                )
            )
    }

    @Test
    fun noBreakSpaceInComment_isReportedWithoutAutoCorrect() {
        assertThatCode("// a\u00a0b\nval a = 1\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                5,
                "ASCII space is the only whitespace character allowed: found U+00A0"
            )
    }

    @Test
    fun tabBetweenTokens_isReportedWithoutAutoCorrect() {
        assertThatCode("val a =\t1\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                8,
                "ASCII space is the only whitespace character allowed: found U+0009"
            )
    }

    @Test
    fun formFeedInCode_isReportedWithoutAutoCorrect() {
        assertThatCode("val a = 1\n\u000c\nval b = 2\n")
            .hasLintViolationWithoutAutoCorrect(
                2,
                1,
                "ASCII space is the only whitespace character allowed: found U+000C"
            )
    }
}
