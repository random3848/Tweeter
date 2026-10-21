package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class ConstantRuleTest {
    private val assertThatCode = assertThatRule { ConstantRule() }

    @Test
    fun guideOkayExamples_noViolation() {
        val code =
            """
            const val NUMBER = 5
            val NAMES = listOf("Alice", "Bob")
            val AGES = mapOf("Alice" to 35, "Bob" to 32)

            object Config {
                const val TIMEOUT = 30L
            }

            class Holder {
                companion object {
                    const val EMPTY = ""
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun scalarsWithoutConst_areReported() {
        val code =
            """
            val NUMBER = 5
            val NEGATIVE = -1.5
            val ENABLED = true
            val LETTER = 'a'
            val NAME: String = "Alice"
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(1, 5, "Scalar constants use the const modifier: 'NUMBER'"),
            LintViolation(2, 5, "Scalar constants use the const modifier: 'NEGATIVE'"),
            LintViolation(3, 5, "Scalar constants use the const modifier: 'ENABLED'"),
            LintViolation(4, 5, "Scalar constants use the const modifier: 'LETTER'"),
            LintViolation(5, 5, "Scalar constants use the const modifier: 'NAME'")
        )
    }

    @Test
    fun scalarInObjectWithoutConst_isReported() {
        val code =
            """
            class Holder {
                companion object {
                    val TAG = "Holder"
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            3,
            13,
            "Scalar constants use the const modifier: 'TAG'"
        )
    }

    @Test
    fun nonConstantProperties_areSkipped() {
        val code =
            """
            var counter = 0
            val greeting = "Hello, ${'$'}name"
            val nothing = null
            val number: Number = 5
            val nullable: String? = "a"
            val lazyValue by lazy { 5 }
            val computed get() = 5
            lateinit var late: String
            override val overridden = 5

            class Holder {
                val size = 5
                val names = listOf("a")
            }

            fun local() {
                val count = 5
                val list = listOf(1)
            }

            val anonymous = object {
                val inner = 5
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun immutableCollectionsNotInUpperSnakeCase_areReported() {
        val code =
            """
            val names = listOf("Alice", "Bob")
            val ages = mapOf("Alice" to 35, "Bob" to -32)
            val none = emptySet<String>()

            object Defaults {
                val ids = listOfNotNull(1, 2)
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(
                1,
                5,
                "Deeply immutable values are constants and use UPPER_SNAKE_CASE: 'names'"
            ),
            LintViolation(
                2,
                5,
                "Deeply immutable values are constants and use UPPER_SNAKE_CASE: 'ages'"
            ),
            LintViolation(
                3,
                5,
                "Deeply immutable values are constants and use UPPER_SNAKE_CASE: 'none'"
            ),
            LintViolation(
                6,
                9,
                "Deeply immutable values are constants and use UPPER_SNAKE_CASE: 'ids'"
            )
        )
    }

    @Test
    fun mutableOrNonScalarCollections_areSkipped() {
        val code =
            """
            val mutable = mutableListOf(1, 2)
            val nested = listOf(listOf(1))
            val users = listOf(User("Alice"))
            val templated = listOf("${'$'}prefix-a")
            val spread = listOf(*array)
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun guideNonConstantScalars_noViolation() {
        val code =
            """
            val defaultUserName = "Guest"

            object NonConstantsSnippet {
                val variable = "var"
                val nonConstScalar = "non-const"
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun upperSnakeScalarWithoutConst_isReported() {
        assertThatCode("val NUMBER = 5\n").hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "Scalar constants use the const modifier: 'NUMBER'"
        )
    }
}
