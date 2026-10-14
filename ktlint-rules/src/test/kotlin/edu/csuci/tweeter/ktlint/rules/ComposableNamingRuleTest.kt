package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class ComposableNamingRuleTest {
    private val assertThatCode = assertThatRule { ComposableNamingRule() }

    @Test
    fun guideOkayExample_noViolation() {
        val code =
            """
            @Composable
            fun NameTag(name: String) {
                Text(name)
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun unitComposableInCamelCase_isReported() {
        val code =
            """
            @Composable
            fun nameTag(name: String) {
                Text(name)
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "@Composable functions that return Unit are PascalCase nouns: 'nameTag'"
        )
    }

    @Test
    fun explicitUnitReturnType_isReportedWhenCamelCase() {
        val code =
            """
            @Composable
            fun nameTag(): Unit = Text("x")
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "@Composable functions that return Unit are PascalCase nouns: 'nameTag'"
        )
    }

    @Test
    fun abstractComposableWithoutBody_isTreatedAsUnit() {
        val code =
            """
            interface Screen {
                @Composable
                fun content()
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            3,
            9,
            "@Composable functions that return Unit are PascalCase nouns: 'content'"
        )
    }

    @Test
    fun valueComposableInCamelCase_noViolation() {
        val code =
            """
            @Composable
            fun rememberName(): String = remember { "name" }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun valueComposableInPascalCase_isReported() {
        val code =
            """
            @Composable
            fun RememberName(): String = remember { "name" }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            5,
            "@Composable functions that return a value are camelCase: 'RememberName'"
        )
    }

    @Test
    fun expressionBodyWithoutReturnType_isSkipped() {
        val code =
            """
            @Composable
            fun RememberName() = remember { "name" }

            @Composable
            fun nameTag() = Text("x")
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun overrideComposable_isSkipped() {
        val code =
            """
            class Impl : Screen {
                @Composable
                override fun content() {
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun nonComposableFunction_isSkipped() {
        val code =
            """
            fun NameTag() {
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun composableOperatorFunction_isSkipped() {
        val code =
            """
            class Slot(private val content: @Composable () -> Unit) {
                @Composable
                operator fun invoke() {
                    content()
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }
}
