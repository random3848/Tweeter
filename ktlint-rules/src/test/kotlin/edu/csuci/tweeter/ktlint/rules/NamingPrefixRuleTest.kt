package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class NamingPrefixRuleTest {
    private val assertThatCode = assertThatRule { NamingPrefixRule() }

    @Test
    fun plainNames_noViolation() {
        val code =
            """
            const val MAX_COUNT = 8

            class Repository(private val cache: Map<String, Int>, timeout: Long) {
                private var name = ""
                private val mapper = Any()
                private val skip = true
                private val kotlinVersion = "2.2"

                fun load(key: String) {
                    val result = cache[key]
                    val (first, _) = Pair(result, timeout)
                    listOf(first).forEach { item -> println(item) }
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun prefixedNames_areReported() {
        val code =
            """
            var sInstance: Any? = null

            class Repository(private val mCache: Map<String, Int>, kTimeout: Long) {
                private var mName = ""

                fun load(sKey: String) {
                    val mResult = mCache[sKey]
                    val (kFirst, second) = Pair(mResult, kTimeout)
                    listOf(second).forEach { mItem -> println(mItem) }
                    println(kFirst + mName)
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            prefixed(1, 5, "sInstance"),
            prefixed(3, 30, "mCache"),
            prefixed(3, 56, "kTimeout"),
            prefixed(4, 17, "mName"),
            prefixed(6, 14, "sKey"),
            prefixed(7, 13, "mResult"),
            prefixed(8, 14, "kFirst"),
            prefixed(9, 34, "mItem")
        )
    }

    @Test
    fun overrides_areSkipped() {
        val code =
            """
            interface Holder {
                val mValue: Int

                fun take(sKey: String)
            }

            class Impl : Holder {
                override val mValue = 1

                override fun take(sKey: String) = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            prefixed(2, 9, "mValue"),
            prefixed(4, 14, "sKey")
        )
    }

    private fun prefixed(line: Int, col: Int, name: String) = LintViolation(
        line,
        col,
        "Names don't use special prefixes such as 'm', 's' or 'k': '$name'"
    )
}
