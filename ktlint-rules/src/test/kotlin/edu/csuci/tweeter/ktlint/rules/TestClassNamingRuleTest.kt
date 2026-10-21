package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class TestClassNamingRuleTest {
    private val assertThatCode = assertThatRule { TestClassNamingRule() }

    @Test
    fun testClassesEndingInTest_noViolation() {
        val code =
            """
            class HashTest {
                @Test
                fun hash_isStable() = Unit
            }

            class HashIntegrationTest {
                @org.junit.jupiter.api.Test
                fun hash_matchesServer() = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun classesWithoutTests_noViolation() {
        val code =
            """
            class Hash {
                @Testing
                fun compute() = Unit
            }

            class TestHelpers {
                fun fixture() = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun testClassWithoutTestSuffix_isReported() {
        val code =
            """
            class HashTests {
                @Test
                fun hash_isStable() = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            7,
            "Test class names end with Test: 'HashTests'"
        )
    }

    @Test
    fun everyTestAnnotation_marksTestClass() {
        val code =
            """
            class QualifiedSpec {
                @org.junit.Test
                fun works() = Unit
            }

            class ParameterizedSpec {
                @ParameterizedTest
                fun works(value: Int) = Unit
            }

            class RepeatedSpec {
                @RepeatedTest(3)
                fun works() = Unit
            }

            class FactorySpec {
                @TestFactory
                fun works() = listOf<Any>()
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(1, 7, "Test class names end with Test: 'QualifiedSpec'"),
            LintViolation(6, 7, "Test class names end with Test: 'ParameterizedSpec'"),
            LintViolation(11, 7, "Test class names end with Test: 'RepeatedSpec'"),
            LintViolation(16, 7, "Test class names end with Test: 'FactorySpec'")
        )
    }

    @Test
    fun nestedClass_isCheckedOnItsOwn() {
        val code =
            """
            class HashTest {
                inner class WhenEmpty {
                    @Test
                    fun hash_isZero() = Unit
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            17,
            "Test class names end with Test: 'WhenEmpty'"
        )
    }
}
