package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class EnumEntrySpacingRuleTest {
    private val assertThatCode = assertThatRule { EnumEntrySpacingRule() }

    @Test
    fun entriesWithoutBodies_noViolation() {
        val code =
            """
            enum class Answer {
                YES,
                NO,
                MAYBE
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun singleLineEnum_noViolation() {
        val code =
            """
            enum class Answer { YES, NO, MAYBE }

            enum class Mood { HAPPY, SAD { override fun toString() = "sad" } }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun entryWithBodySeparatedByBlankLine_noViolation() {
        val code =
            """
            enum class Answer {
                YES,
                NO,

                MAYBE {
                    override fun toString() = "maybe"
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun entryWithBodyWithoutBlankLine_isReported() {
        val code =
            """
            enum class Answer {
                YES,
                NO,
                MAYBE {
                    override fun toString() = "maybe"
                }
            }
            """.trimIndent()
        val formattedCode =
            """
            enum class Answer {
                YES,
                NO,

                MAYBE {
                    override fun toString() = "maybe"
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(4, 5, "A blank line separates enum entries with a body: 'MAYBE'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun entryAfterEntryWithBody_isReported() {
        val code =
            """
            enum class Answer {
                MAYBE {
                    override fun toString() = "maybe"
                },
                YES
            }
            """.trimIndent()
        val formattedCode =
            """
            enum class Answer {
                MAYBE {
                    override fun toString() = "maybe"
                },

                YES
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(5, 5, "A blank line separates enum entries with a body: 'YES'")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun leadingComment_getsBlankLineAboveIt() {
        val code =
            """
            enum class Answer {
                YES,
                // Undecided.
                MAYBE {
                    override fun toString() = "maybe"
                }
            }
            """.trimIndent()
        val formattedCode =
            """
            enum class Answer {
                YES,

                // Undecided.
                MAYBE {
                    override fun toString() = "maybe"
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates enum entries with a body: 'MAYBE'")
            .isFormattedAs(formattedCode)
    }
}
