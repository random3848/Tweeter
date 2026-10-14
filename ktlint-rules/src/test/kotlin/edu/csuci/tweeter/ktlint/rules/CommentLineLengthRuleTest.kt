package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.ruleset.standard.rules.MaxLineLengthRule
import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRuleBuilder
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

private const val EXCEEDED = "Exceeded max line length (40)"

class CommentLineLengthRuleTest {
    private val assertThatCode =
        assertThatRuleBuilder { CommentLineLengthRule() }
            .addAdditionalRuleProvider { MaxLineLengthRule() }
            .withEditorConfigOverride(MAX_LINE_LENGTH_PROPERTY to 40)
            .assertThat()

    @Test
    fun linesWithinLimit_noViolation() {
        val code =
            """
            // A short comment.
            /** Short KDoc. */
            val s = "short"
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun longEndOfLineComment_isReported() {
        val code =
            """
            fun f() {
                // This comment is longer than forty columns.
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(2, 41, EXCEEDED)
    }

    @Test
    fun longKdocLine_isReported() {
        val code =
            """
            /**
             * This KDoc line is longer than forty columns.
             */
            class Foo
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(2, 41, EXCEEDED)
    }

    @Test
    fun longLinesOfBlockComment_areReported() {
        val code =
            """
            /* This first line is longer than forty columns.
               This middle line is longer than forty columns.
               short */
            val a = 1
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationsWithoutAutoCorrect(
                LintViolation(1, 41, EXCEEDED, false),
                LintViolation(2, 41, EXCEEDED, false)
            )
    }

    @Test
    fun longStringOnlyLines_areReported() {
        val code =
            """
            val s =
                "This string is longer than forty columns"
            val list =
                listOf(
                    "This string is longer than forty columns",
                    "b"
                )
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationsWithoutAutoCorrect(
                LintViolation(2, 41, EXCEEDED, false),
                LintViolation(5, 41, EXCEEDED, false)
            )
    }

    @Test
    fun longCodeLines_areLeftToMaxLineLength() {
        val code =
            """
            val x = 1 // A trailing comment that is too long.
            val s = "A string after code that is too long"
            val t = listOf(
                "A string followed by a paren, too long")
            """.trimIndent()
        assertThatCode(code).hasNoLintViolationsExceptInAdditionalRules()
    }

    @Test
    fun longCommentWithUrl_noViolation() {
        val code =
            """
            // See https://example.com/a/very/long/path/to/a/page.
            val a = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun longShellCommandLines_noViolation() {
        val code =
            """
            /**
             * Build with:
             * ```
             * ./gradlew :app:assembleDebug --stacktrace --info
             * ```
             */
            class Foo

            // $ ./gradlew ktlintCheck --continue --stacktrace
            val a = 1
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun longLineAfterFencedBlock_isReported() {
        val code =
            """
            /**
             * ```
             * ./gradlew :app:assembleDebug --stacktrace --info
             * ```
             * This KDoc line is longer than forty columns.
             */
            class Foo
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(5, 41, EXCEEDED)
    }

    @Test
    fun longRawStringLines_noViolation() {
        val code =
            "val s =\n" +
                "    \"\"\"\n" +
                "    This raw string line is longer than forty columns.\n" +
                "    \"\"\"\n" +
                "val t = \"\"\"Another raw string longer than forty columns\"\"\"\n"
        assertThatCode(code).hasNoLintViolationsExceptInAdditionalRules()
    }

    @Test
    fun maxLineLengthOff_noViolation() {
        val code =
            """
            // This comment is longer than forty columns.
            val a = 1
            """.trimIndent()
        assertThatCode(code)
            .withEditorConfigOverride(MAX_LINE_LENGTH_PROPERTY to "off")
            .hasNoLintViolations()
    }

    @Test
    fun standardMaxLineLengthRuleNotLoaded_noViolation() {
        val code =
            """
            // This comment is longer than forty columns.
            val a = 1
            """.trimIndent()
        val assertThatCodeWithoutMaxLineLengthRule = assertThatRule { CommentLineLengthRule() }
        assertThatCodeWithoutMaxLineLengthRule(code)
            .withEditorConfigOverride(MAX_LINE_LENGTH_PROPERTY to 40)
            .hasNoLintViolations()
    }

    @Test
    fun longCommentLineInSuppressedFunction_isNotReported() {
        val code =
            """
            @Suppress("ktlint:android-style:comment-line-length")
            fun f() {
                // This comment is longer than forty columns.
            }

            // This comment is longer than forty columns.
            val a = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(6, 41, EXCEEDED)
    }

    @Test
    fun longStringOnlyLineInSuppressedFunction_isNotReported() {
        val code =
            """
            @Suppress("ktlint:android-style:comment-line-length")
            fun f() =
                listOf(
                    "This string is longer than forty columns"
                )

            val s =
                "This string is longer than forty columns"
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(8, 41, EXCEEDED)
    }

    @Test
    fun longLinesInTildeFencedBlock_areNotReported() {
        val code =
            """
            /**
             * ~~~
             * ./gradlew :app:assembleDebug --stacktrace --info
             * ~~~
             * This KDoc line is longer than forty columns.
             */
            class Foo
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(5, 41, EXCEEDED)
    }
}
