package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class KdocSummaryRuleTest {
    private val assertThatCode = assertThatRule { KdocSummaryRule() }

    @Test
    fun guideSingleLineExample_noViolation() {
        val code =
            """
            /** An especially short bit of KDoc. */
            fun foo() = Unit
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun guideMultiLineExampleEndingInEllipsis_noViolation() {
        val code =
            """
            /**
             * Multiple lines of KDoc text are written here,
             * wrapped normally…
             */
            fun method(arg: String) {
                // …
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun summaryFollowedByParagraphsAndTags_noViolation() {
        val code =
            """
            /**
             * Is the foo ready?
             *
             * more text that is not part of the summary
             *
             * @return Whether the foo is ready.
             */
            fun isReady(): Boolean = true
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun summaryStartingWithNonLetter_noViolation() {
        val code =
            """
            /** `foo` returns the bar! */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun articleFragmentWithoutVerb_noViolation() {
        val code =
            """
            /** The foos that are cached. */
            val foos = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun danglingKdoc_isNotChecked() {
        val code =
            """
            fun foo() {
                /** returns nothing */
                println()
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun onlyBlockTags_isReported() {
        val code =
            """
            /**
             * @return The foo.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            1,
            "KDoc begins with a summary fragment"
        )
    }

    @Test
    fun emptyKdoc_isReported() {
        val code =
            """
            /** */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            1,
            "KDoc begins with a summary fragment"
        )
    }

    @Test
    fun codeBlockBeforeSummary_isReported() {
        val code =
            """
            /**
             * ```
             * foo()
             * ```
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            1,
            "KDoc begins with a summary fragment"
        )
    }

    @Test
    fun lowercaseSummary_isReported() {
        val code =
            """
            /** returns the foo. */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "The KDoc summary is capitalized: 'returns'"
        )
    }

    @Test
    fun unpunctuatedSingleLine_isReported() {
        val code =
            """
            /** Returns foo */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            15,
            "The KDoc summary is punctuated like a sentence"
        )
    }

    @Test
    fun unpunctuatedFirstParagraph_isReported() {
        val code =
            """
            /**
             * Returns the foo
             * of the bar
             *
             * More text.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            3,
            13,
            "The KDoc summary is punctuated like a sentence"
        )
    }

    @Test
    fun unpunctuatedSummaryBeforeTag_isReported() {
        val code =
            """
            /**
             * Returns the foo
             * @return The foo.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            18,
            "The KDoc summary is punctuated like a sentence"
        )
    }

    @Test
    fun thisMethodSentence_isReported() {
        val code =
            """
            /** This method returns the foo. */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "The KDoc summary is a fragment, not a sentence like 'This method...'"
        )
    }

    @Test
    fun articleIsSentence_isReported() {
        val code =
            """
            /**
             * A Foo is a widget
             * that does things.
             */
            class Foo
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            4,
            "The KDoc summary is a fragment, not a sentence like 'A Foo is...'"
        )
    }

    @Test
    fun articleWithBacktickedNameIsSentence_isReported() {
        val code =
            """
            /** The `foo bar` are widgets. */
            val fooBar = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "The KDoc summary is a fragment, not a sentence like 'The `foo bar` are...'"
        )
    }
}
