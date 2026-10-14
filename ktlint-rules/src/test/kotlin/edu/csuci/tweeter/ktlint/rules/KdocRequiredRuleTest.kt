package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class KdocRequiredRuleTest {
    private val assertThatCode = assertThatRule { KdocRequiredRule() }

    @Test
    fun documentedPublicApiAndExemptions_noViolation() {
        val code =
            """
            /**
             * Widget.
             *
             * @property name The name.
             */
            open class Foo(
                /** The id. */
                val id: Int,
                val name: String,
                override val size: Int,
                private val secret: String,
                internal val hidden: Int,
                plain: Int
            ) : Sized {
                /** Bar. */
                fun bar() = Unit

                /** Baz. */
                protected val baz = 1

                private fun hiddenFun() = Unit

                internal val hiddenVal = 1

                override fun toString() = "Foo"

                /** Qux. */
                fun qux() {
                    val local = 1
                    fun localFun() = Unit
                    class LocalType
                }

                /** Listener. */
                val listener = object : Runnable {
                    fun extra() = Unit

                    override fun run() = Unit
                }

                companion object {
                    /** Max. */
                    const val MAX = 1
                }
            }

            fun topLevel() = Unit

            val topLevelVal = 1

            private class Hidden {
                fun member() = Unit
            }

            internal class AlsoHidden(val id: Int) {
                fun member() = Unit
            }

            private typealias HiddenAlias = Int

            /** Colors. */
            enum class Color {
                RED,
                GREEN {
                    fun extra() = Unit
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun undocumentedTypes_areReported() {
        val code =
            """
            class Foo
            interface Bar
            object Baz
            enum class Qux { A }
            annotation class Ann
            typealias Alias = Int
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(1, 7, "Public type Foo has no KDoc"),
            LintViolation(2, 11, "Public type Bar has no KDoc"),
            LintViolation(3, 8, "Public type Baz has no KDoc"),
            LintViolation(4, 12, "Public type Qux has no KDoc"),
            LintViolation(5, 18, "Public type Ann has no KDoc"),
            LintViolation(6, 11, "Public type Alias has no KDoc")
        )
    }

    @Test
    fun undocumentedMembers_areReported() {
        val code =
            """
            /** Foo. */
            open class Foo(val id: Int, protected var count: Int) {
                fun bar() = Unit
                protected val baz = 1
                class Nested
                companion object {
                    const val MAX = 1
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 20, "Public property id has no KDoc or @property tag"),
            LintViolation(2, 43, "Public property count has no KDoc or @property tag"),
            LintViolation(3, 9, "Public member bar has no KDoc"),
            LintViolation(4, 19, "Public member baz has no KDoc"),
            LintViolation(5, 11, "Public type Nested has no KDoc"),
            LintViolation(7, 19, "Public member MAX has no KDoc")
        )
    }

    @Test
    fun interfaceMembersDefaultToPublic_areReported() {
        val code =
            """
            /** Foo. */
            interface Foo {
                fun bar()
                val baz: Int
                private fun hidden() = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            LintViolation(3, 9, "Public member bar has no KDoc"),
            LintViolation(4, 9, "Public member baz has no KDoc")
        )
    }

    @Test
    fun memberOfProtectedNestedType_isReported() {
        val code =
            """
            /** Outer. */
            open class Outer {
                /** Inner. */
                protected open class Inner {
                    fun member() = Unit
                }

                private class Hidden {
                    fun member() = Unit
                }

                private companion object {
                    const val MAX = 1
                }
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            5,
            13,
            "Public member member has no KDoc"
        )
    }

    @Test
    fun propertyTagForOtherName_isReported() {
        val code =
            """
            /**
             * Foo.
             *
             * @property other Not the id.
             */
            class Foo(val id: Int)
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            6,
            15,
            "Public property id has no KDoc or @property tag"
        )
    }

    @Test
    fun testSource_isExempt() {
        val code =
            """
            class Foo {
                fun bar() = Unit
            }
            """.trimIndent()
        assertThatCode(code)
            .asFileWithPath("/project/src/test/kotlin/Foo.kt")
            .hasNoLintViolations()
    }

    @Test
    fun kotlinScript_isExempt() {
        val code =
            """
            class Foo {
                fun bar() = Unit
            }
            """.trimIndent()
        assertThatCode(code).asKotlinScript().hasNoLintViolations()
    }

    @Test
    fun suppressedMember_noViolation() {
        val code =
            """
            /** Foo. */
            class Foo {
                @Suppress("ktlint:android-style:kdoc-required")
                fun getFoo() = 1
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }
}
