package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class ParameterNamingRuleTest {
    private val assertThatCode = assertThatRule { ParameterNamingRule() }

    @Test
    fun camelCaseParameters_noViolation() {
        val code =
            """
            class Account(val owner: String, private val _balance: Int) {
                var label: String = ""
                    set(newValue) {
                        field = newValue
                    }

                fun deposit(newCustomerId: Int, amount2: Int) {
                    listOf(amount2).forEachIndexed { _, item -> println(item) }
                    val (first, _) = Pair(newCustomerId, amount2)
                    for (index in 0..first) {
                        println(index)
                    }
                    try {
                        println()
                    } catch (e: Exception) {
                        println(e)
                    }
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun nonCamelCaseParameters_areReported() {
        val code =
            """
            class Account(val Owner: String, private val _balance: Int, private val _Hidden: Int) {
                var label: String = ""
                    set(New_value) {
                        field = New_value
                    }

                constructor(Id: Long) : this(Id.toString(), 0, 0)

                fun deposit(Amount: Int, max_amount: Int) {
                    listOf(Amount).forEach { Item -> println(Item) }
                    val (First, second) = Pair(Amount, max_amount)
                    for (Index in 0..second) {
                        println(Index + First)
                    }
                    try {
                        println()
                    } catch (E: Exception) {
                        println(E)
                    }
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            notCamelCase(1, 19, "Owner"),
            notCamelCase(1, 73, "_Hidden"),
            notCamelCase(3, 13, "New_value"),
            notCamelCase(7, 17, "Id"),
            notCamelCase(9, 17, "Amount"),
            notCamelCase(9, 30, "max_amount"),
            notCamelCase(10, 34, "Item"),
            notCamelCase(11, 14, "First"),
            notCamelCase(12, 14, "Index"),
            notCamelCase(17, 18, "E")
        )
    }

    @Test
    fun underscorePrefix_isOnlyAllowedOnPrivateConstructorProperties() {
        val code =
            """
            class Base(val _counter: Int, protected val _total: Int) {
                fun take(_value: Int) = _value
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            notCamelCase(1, 16, "_counter"),
            notCamelCase(1, 45, "_total"),
            notCamelCase(2, 14, "_value")
        )
    }

    @Test
    fun overrides_areSkipped() {
        val code =
            """
            interface Shape {
                val Sides: Int

                fun scale(Factor: Int)
            }

            class Square(override val Sides: Int) : Shape {
                override fun scale(Factor: Int) = Unit
            }

            open class Base {
                open var size: Int = 0
            }

            class Sized : Base() {
                override var size: Int = 0
                    set(New) {
                        field = New
                    }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            4,
            15,
            "Parameter names are camelCase: 'Factor'"
        )
    }

    private fun notCamelCase(line: Int, col: Int, name: String) =
        LintViolation(line, col, "Parameter names are camelCase: '$name'")
}
