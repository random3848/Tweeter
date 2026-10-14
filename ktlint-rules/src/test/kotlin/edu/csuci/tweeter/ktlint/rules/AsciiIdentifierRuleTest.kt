package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class AsciiIdentifierRuleTest {
    private val assertThatCode = assertThatRule { AsciiIdentifierRule() }

    @Test
    fun asciiNames_noViolation() {
        val code =
            """
            const val MAX_COUNT = 8

            class XmlHttpRequest<T>(val newCustomerId: Int) {
                fun supportsIpv6OnIos(retries: Int): Boolean {
                    val (first, _) = Pair(retries, 2)
                    return listOf(first).any { item -> item > 0 }
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun nonAsciiFunctionLocalsAndParameters_areReported() {
        val code =
            """
            fun größe(länge: Int): Int {
                val wörter = listOf(länge).map { wertë -> wertë }
                val (één, zwei) = Pair(1, 2)
                for (ñ in 0..länge) {
                    println(ñ + één + zwei + wörter.size)
                }
                try {
                    println()
                } catch (ä: Exception) {
                    println(ä)
                }
                return 0
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            nonAscii(1, 5, "größe"),
            nonAscii(1, 11, "länge"),
            nonAscii(2, 9, "wörter"),
            nonAscii(2, 38, "wertë"),
            nonAscii(3, 10, "één"),
            nonAscii(4, 10, "ñ"),
            nonAscii(9, 14, "ä")
        )
    }

    @Test
    fun nonAsciiTypeNames_areReported() {
        val code =
            """
            class Café<Τ>(val naïve: Int) {
                var size: Int = 0
                    set(nëw) {
                        field = nëw
                    }
            }

            object Œuvre

            interface Fünf

            typealias Straße = String

            enum class Color { RED, GRÜN }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            nonAscii(1, 7, "Café"),
            nonAscii(1, 12, "Τ"),
            nonAscii(1, 19, "naïve"),
            nonAscii(3, 13, "nëw"),
            nonAscii(8, 8, "Œuvre"),
            nonAscii(10, 11, "Fünf"),
            nonAscii(12, 11, "Straße"),
            nonAscii(14, 25, "GRÜN")
        )
    }

    @Test
    fun backtickedTestNames_areReportedInTestSources() {
        val code =
            """
            class FooTest {
                @Test
                fun `adds two numbers`() = Unit

                @Test
                fun `a-b`() = Unit

                @Test
                fun addsTwoNumbers() = Unit
            }
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/app/src/test/kotlin/FooTest.kt")
            .hasLintViolationsWithoutAutoCorrect(
                LintViolation(3, 9, "Function names don't contain spaces: 'adds two numbers'"),
                nonAscii(6, 9, "a-b")
            )
    }

    @Test
    fun overrides_areSkipped() {
        val code =
            """
            interface Shape {
                val größe: Int

                fun scale(faktör: Int)
            }

            abstract class Base {
                abstract var wert: Int
            }

            class Square : Base(), Shape {
                override val größe: Int = 1

                override var wert: Int = 0
                    set(nëw) {
                        field = nëw
                    }

                override fun scale(faktör: Int) = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            nonAscii(2, 9, "größe"),
            nonAscii(4, 15, "faktör")
        )
    }

    private fun nonAscii(line: Int, col: Int, name: String) = LintViolation(
        line,
        col,
        "Identifiers use only ASCII letters, digits and underscores: '$name'"
    )
}
