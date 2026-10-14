package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class TypeParameterNamingRuleTest {
    private val assertThatCode = assertThatRule { TypeParameterNamingRule() }

    @Test
    fun guideStyleTypeParameters_noViolation() {
        val code =
            """
            class Cache<K, V>

            fun <E, T, X, T2> pick(first: T, second: T2): T = first

            inline fun <reified T> cast(value: Any): T = value as T

            interface Mapper<in RequestT, out FooBarT, Foo2T>

            typealias Handler<T> = (T) -> Unit
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun otherTypeParameters_areReported() {
        val code =
            """
            class Cache<Key, TT>

            fun <T12, item> pick(first: T12, second: item) = first

            interface Mapper<in REQUESTT, out Response>

            typealias Handler<Request> = (Request) -> Unit
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            badTypeParameter(1, 13, "Key"),
            badTypeParameter(1, 18, "TT"),
            badTypeParameter(3, 6, "T12"),
            badTypeParameter(3, 11, "item"),
            badTypeParameter(5, 21, "REQUESTT"),
            badTypeParameter(5, 35, "Response"),
            badTypeParameter(7, 19, "Request")
        )
    }

    private fun badTypeParameter(line: Int, col: Int, name: String) = LintViolation(
        line,
        col,
        "Type parameters are a capital letter with an optional numeral (T, T2) or a " +
            "class-style name followed by T (RequestT): '$name'"
    )
}
