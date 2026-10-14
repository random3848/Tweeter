package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnicodeEscapeRuleTest {
    private val assertThatCode = assertThatRule { UnicodeEscapeRule() }

    /**
     * Asserts that [code] has exactly the given violation, flagged as not autocorrectable, and that
     * formatting leaves [code] unchanged. ktlint-test's `hasLintViolationWithoutAutoCorrect` checks
     * neither.
     */
    private fun assertReportedWithoutAutoCorrect(code: String, col: Int, detail: String) {
        assertThatCode(code).hasLintViolations(LintViolation(1, col, detail, false))
        val engine = KtLintRuleEngine(ruleProviders = setOf(RuleProvider { UnicodeEscapeRule() }))
        val formatted = engine.format(Code.fromSnippet(code)) {
            AutocorrectDecision.ALLOW_AUTOCORRECT
        }
        assertThat(formatted).isEqualTo(code)
    }

    @Test
    fun guideGoodExamples_noViolation() {
        val code =
            """
            val unitAbbrev = "μs"

            fun withBom(content: String): String = "\ufeff" + content
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun nonPrintableEscapes_noViolation() {
        val code =
            """
            val figureSpace = '\u2007'
            val noBreakSpace = "\u00a0"
            val nul = "\u0000"
            val emoji = "\ud83d\ude00"
            val combiningAcute = "e\u0301"
            val replacement = "\ufffd"
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun printableCharacterEscape_isReplacedWithCharacter() {
        val code =
            """
            val symbol = "\u03bcs"
            """.trimIndent()
        val formattedCode =
            """
            val symbol = "μs"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                1,
                15,
                "Use the character 'μ' instead of its Unicode escape '\\u03bc'"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun severalEscapesInString_areAllReplaced() {
        val code =
            """
            val summer = "\u00e9t\u00E9"
            """.trimIndent()
        val formattedCode =
            """
            val summer = "été"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolations(
                LintViolation(
                    1,
                    15,
                    "Use the character 'é' instead of its Unicode escape '\\u00e9'"
                ),
                LintViolation(
                    1,
                    22,
                    "Use the character 'é' instead of its Unicode escape '\\u00E9'"
                )
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun printableCharacterLiteralEscape_isReplacedWithCharacter() {
        val code =
            """
            val a = '\u0041'
            """.trimIndent()
        val formattedCode =
            """
            val a = 'A'
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                1,
                10,
                "Use the character 'A' instead of its Unicode escape '\\u0041'"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun newlineEscape_isReplacedWithSpecialEscape() {
        val code =
            """
            val lines = "a\u000ab"
            """.trimIndent()
        val formattedCode =
            """
            val lines = "a\nb"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(1, 15, "Use the special escape sequence '\\n' instead of '\\u000a'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun dollarEscape_isReplacedWithSpecialEscape() {
        val code =
            """
            val price = "\u0024"
            """.trimIndent()
        val formattedCode =
            """
            val price = "\$"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(1, 14, "Use the special escape sequence '\\$' instead of '\\u0024'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun quoteEscapeInCharacterLiteral_isReplacedWithSpecialEscape() {
        val code =
            """
            val quote = '\u0027'
            """.trimIndent()
        val formattedCode =
            """
            val quote = '\''
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(1, 14, "Use the special escape sequence '\\'' instead of '\\u0027'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun escapeAfterLiteralDollar_isReportedWithoutAutoCorrect() {
        val code =
            """
            val notATemplate = "$\u0041"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(
                1,
                22,
                "Use the character 'A' instead of its Unicode escape '\\u0041'"
            )
    }

    @Test
    fun backtickEscapeAfterLiteralDollar_isReportedWithoutAutoCorrect() {
        val code =
            """
            val notATemplate = "$\u0060a b`"
            """.trimIndent()
        assertReportedWithoutAutoCorrect(
            code,
            22,
            "Use the character '`' instead of its Unicode escape '\\u0060'"
        )
    }

    @Test
    fun letterEscapeAfterShortTemplate_isReportedWithoutAutoCorrect() {
        val code =
            """
            fun label(name: String, names: List<String>): String = "${'$'}name\u0073 of ${'$'}names"
            """.trimIndent()
        assertReportedWithoutAutoCorrect(
            code,
            62,
            "Use the character 's' instead of its Unicode escape '\\u0073'"
        )
    }

    @Test
    fun digitEscapeAfterShortTemplate_isReportedWithoutAutoCorrect() {
        val code =
            """
            fun price(amount: Int) = "${'$'}amount\u0030"
            """.trimIndent()
        assertReportedWithoutAutoCorrect(
            code,
            34,
            "Use the character '0' instead of its Unicode escape '\\u0030'"
        )
    }

    @Test
    fun escapeAfterBracedTemplate_isReplacedWithCharacter() {
        val code =
            """
            fun plural(name: String) = "${'$'}{name}\u0073"
            """.trimIndent()
        val formattedCode =
            """
            fun plural(name: String) = "${'$'}{name}s"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                1,
                36,
                "Use the character 's' instead of its Unicode escape '\\u0073'"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun asciiSpaceEscapeInString_isReplacedWithSpace() {
        val code =
            """
            const val SPACED = "a\u0020b"
            """.trimIndent()
        val formattedCode =
            """
            const val SPACED = "a b"
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                1,
                22,
                "Use the character ' ' instead of its Unicode escape '\\u0020'"
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun asciiSpaceEscapeInCharacterLiteral_isReplacedWithSpace() {
        val code =
            """
            val space = '\u0020'
            """.trimIndent()
        val formattedCode =
            """
            val space = ' '
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(
                1,
                14,
                "Use the character ' ' instead of its Unicode escape '\\u0020'"
            ).isFormattedAs(formattedCode)
    }
}
