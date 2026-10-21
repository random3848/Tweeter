package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class KdocFormatRuleTest {
    private val assertThatCode = assertThatRule { KdocFormatRule() }

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
    fun guideMultiLineExample_noViolation() {
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
    fun wellFormedBlockTags_noViolation() {
        val code =
            """
            /**
             * Returns the absolute value.
             *
             * A second paragraph.
             *
             * @receiver The number.
             * @param value The value to return the absolute value for, which has a description
             *     that is wrapped onto a second line.
             * @sample samples.abs
             * @return The absolute value.
             * @throws IllegalStateException When the value is negative.
             * @see kotlin.math.abs
             * @author Somebody
             */
            fun Int.abs(value: Int): Int = value
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun indentedKdocWithContinuation_noViolation() {
        val code =
            """
            class Foo {
                /**
                 * Returns the bar.
                 *
                 * @param bar The bar, which has a description that is
                 *     wrapped correctly.
                 */
                fun foo(bar: Int) = bar
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun onlyBlockTags_noBlankLineNeeded() {
        val code =
            """
            /**
             * @return The foo.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun descriptionOnContinuationLine_noViolation() {
        val code =
            """
            /**
             * Returns the bar.
             *
             * @param bar
             *     The bar.
             */
            fun foo(bar: Int) = bar
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun fencedCodeBlock_isNotChecked() {
        val code =
            """
            /**
             * Returns foo.
             *
             * ```
             * @param notATag
             *
             *
             * ```
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun danglingKdoc_isNotChecked() {
        val code =
            """
            fun foo() {
                /** @return Dangling. */
                println()
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun singleLineWithBlockTag_isReported() {
        val code =
            """
            /** @return The value. */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "The single-line KDoc form is only for KDoc without block tags: '@return'"
        )
    }

    @Test
    fun textOnOpeningLine_isReported() {
        val code =
            """
            /** Returns foo.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "Multi-line KDoc starts with /** on its own line"
        )
    }

    @Test
    fun textOnClosingLine_isReported() {
        val code =
            """
            /**
             * Returns foo. */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            17,
            "Multi-line KDoc ends with */ on its own line"
        )
    }

    @Test
    fun lineWithoutAsterisk_isReported() {
        val code =
            """
            /**
             * Returns foo, which has a description that is
               wrapped without an asterisk.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            3,
            4,
            "Each line of multi-line KDoc starts with *"
        )
    }

    @Test
    fun twoBlankLines_isReported() {
        val code =
            """
            /**
             * Returns foo.
             *
             *
             * More text.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            4,
            2,
            "One blank line separates KDoc paragraphs"
        )
    }

    @Test
    fun noBlankLineBeforeBlockTags_isReported() {
        val code =
            """
            /**
             * Returns foo.
             * @return The foo.
             */
            fun foo() = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            3,
            4,
            "A blank line comes before the block tags"
        )
    }

    @Test
    fun paramAfterReturn_isReportedOnce() {
        val code =
            """
            /**
             * Returns foo.
             *
             * @return The foo.
             * @param bar The bar.
             * @constructor Not a constructor.
             */
            fun foo(bar: Int): Int = bar
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            5,
            4,
            "Block tags are in the order @constructor, @receiver, @param, @property, @return, " +
                "@throws, @see: '@param' follows '@return'"
        )
    }

    @Test
    fun exceptionAfterSee_isReported() {
        val code =
            """
            /**
             * Returns foo.
             *
             * @see Bar
             * @exception IllegalStateException When it fails.
             */
            fun foo(): Int = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            5,
            4,
            "Block tags are in the order @constructor, @receiver, @param, @property, @return, " +
                "@throws, @see: '@exception' follows '@see'"
        )
    }

    @Test
    fun emptyBlockTags_areReported() {
        val code =
            """
            /**
             * Creates foo.
             *
             * @constructor
             * @receiver
             * @param bar
             * @property
             * @return
             * @throws
             * @see
             */
            fun foo(bar: Int): Int = bar
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(4, 4, "Block tag @constructor has no description"),
            LintViolation(5, 4, "Block tag @receiver has no description"),
            LintViolation(6, 4, "Block tag @param bar has no description"),
            LintViolation(7, 4, "Block tag @property has no subject and no description"),
            LintViolation(8, 4, "Block tag @return has no description"),
            LintViolation(9, 4, "Block tag @throws has no subject and no description"),
            LintViolation(10, 4, "Block tag @see has no reference")
        )
    }

    @Test
    fun misindentedContinuationLines_areReported() {
        val code =
            """
            /**
             * Returns foo.
             *
             * @param bar The bar, which has a description that is
             * wrapped without indentation.
             * @return The foo, which has a description that is
             *         wrapped too far.
             */
            fun foo(bar: Int): Int = bar
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(5, 4, "Block tag continuation lines are indented 4 spaces from the @"),
            LintViolation(7, 12, "Block tag continuation lines are indented 4 spaces from the @")
        )
    }
}
