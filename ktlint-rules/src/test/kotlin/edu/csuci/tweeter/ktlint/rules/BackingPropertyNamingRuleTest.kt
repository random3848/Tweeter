package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class BackingPropertyNamingRuleTest {
    private val assertThatCode = assertThatRule { BackingPropertyNamingRule() }

    @Test
    fun guideTopLevelExample_noViolation() {
        val code =
            """
            private var _table: Map<String, Int>? = null

            val table: Map<String, Int>
                get() {
                    if (_table == null) {
                        _table = HashMap()
                    }
                    return _table ?: throw AssertionError()
                }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun classAndCompanionBackingProperties_noViolation() {
        val code =
            """
            class Repository {
                private val _items = mutableListOf<String>()
                val items: List<String> get() = _items

                private val _count = 0
                fun getCount() = _count

                companion object {
                    private val _instance = Repository()
                }

                val instance get() = _instance
            }

            class Cache {
                private companion object {
                    val _store = 1
                    val store get() = _store
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun badlyCasedName_isReported() {
        val code =
            """
            private val _Table = 1
            val Table = _Table
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            13,
            "Backing properties start with an underscore followed by lower camel case: '_Table'"
        )
    }

    @Test
    fun nonPrivateBackingProperty_isReported() {
        val code =
            """
            class Repository {
                internal val _items = mutableListOf<String>()
                val items: List<String> get() = _items
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            18,
            "Backing properties are private: '_items'"
        )
    }

    @Test
    fun missingBackedProperty_isReported() {
        val code =
            """
            private val _table = 1

            class Repository {
                private val _items = 1
                fun getItems(limit: Int) = _items
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(
                1,
                13,
                "Backing properties need a matching property or getter function: '_table'"
            ),
            LintViolation(
                4,
                17,
                "Backing properties need a matching property or getter function: '_items'"
            )
        )
    }

    @Test
    fun backedPropertyInOtherClass_isReported() {
        val code =
            """
            class A {
                private val _name = ""
            }

            class B {
                val name = ""
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            2,
            17,
            "Backing properties need a matching property or getter function: '_name'"
        )
    }

    @Test
    fun localUnderscoreProperty_isReported() {
        val code =
            """
            fun load() {
                val _value = 1
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 9, "Backing properties are private: '_value'"),
            LintViolation(
                2,
                9,
                "Backing properties need a matching property or getter function: '_value'"
            )
        )
    }

    @Test
    fun discardAndOverride_areSkipped() {
        val code =
            """
            fun load() {
                val _ = compute()
            }

            class Impl : Base() {
                override val _legacy = 1
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }
}
