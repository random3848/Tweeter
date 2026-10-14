package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class MemberSpacingRuleTest {
    private val assertThatCode = assertThatRule { MemberSpacingRule() }

    @Test
    fun membersSeparatedByBlankLines_noViolation() {
        val code =
            """
            class Foo(private val x: Int) {
                val a = 1
                val b = 2

                val c = 3

                init {
                    println(a)
                }

                constructor() : this(0)

                fun bar() = 1
                // A note on bar, above the blank line.

                // A note on baz, below the blank line.
                /** Returns two. */
                fun baz() = 2

                class Nested

                companion object {
                    const val D = 4
                }
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun adjacentFunctions_areSeparated() {
        val code =
            """
            class Foo {
                fun a() = 1
                fun b() = 2
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun a() = 1

                fun b() = 2
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun propertyFollowedByFunction_isReported() {
        val code =
            """
            class Foo {
                val a = 1
                fun b() = a
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                val a = 1

                fun b() = a
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun functionFollowedByProperty_isReported() {
        val code =
            """
            class Foo {
                fun a() = 1
                val b = 2
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun a() = 1

                val b = 2
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun initBlockFollowedByCompanionObject_isReported() {
        val code =
            """
            class Foo {
                init {
                    println()
                }
                companion object {
                    const val A = 1
                }
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                init {
                    println()
                }

                companion object {
                    const val A = 1
                }
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(5, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun leadingComment_getsBlankLineAboveIt() {
        val code =
            """
            class Foo {
                fun a() = 1
                // About b.
                fun b() = 2
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun a() = 1

                // About b.
                fun b() = 2
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun kdoc_getsBlankLineAboveIt() {
        val code =
            """
            class Foo {
                fun a() = 1
                /** Returns two. */
                fun b() = 2
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun a() = 1

                /** Returns two. */
                fun b() = 2
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun trailingCommentOfPreviousMember_staysOnItsLine() {
        val code =
            """
            class Foo {
                fun a() = 1 // About a.
                fun b() = 2
            }
            """.trimIndent()
        val formattedCode =
            """
            class Foo {
                fun a() = 1 // About a.

                fun b() = 2
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(3, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }

    @Test
    fun topLevelDeclarations_noViolation() {
        val code =
            """
            val a = 1
            fun b() = 2
            fun c() = 3
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun enumEntries_noViolation() {
        val code =
            """
            enum class Answer {
                YES,
                NO;
                fun isYes() = this == YES
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun suppressedMember_noViolation() {
        val code =
            """
            class A {
                fun b() = 2
                @Suppress("ktlint:android-style:member-spacing")
                fun c() = 3
            }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun memberAfterSuppressedMember_isReported() {
        val code =
            """
            class A {
                @Suppress("ktlint:android-style:member-spacing")
                fun b() = 2
                fun c() = 3
            }
            """.trimIndent()
        val formattedCode =
            """
            class A {
                @Suppress("ktlint:android-style:member-spacing")
                fun b() = 2

                fun c() = 3
            }
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(4, 5, "A blank line separates consecutive class members")
            .isFormattedAs(formattedCode)
    }
}
