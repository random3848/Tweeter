package edu.csuci.tweeter.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs the complete setup (ktlint's standard rules, this rule set, and the repository's
 * .editorconfig) over the examples in the Android Kotlin style guide: every "Okay" example passes
 * and every "WRONG" example is reported by the rule that owns it.
 */
class StyleGuideTest {
    @TempDir
    lateinit var projectDir: Path

    private val engine = KtLintRuleEngine(
        ruleProviders = StandardRuleSetProvider().getRuleProviders() +
            AndroidStyleRuleSetProvider().getRuleProviders()
    )

    @Test
    fun okayExamples_haveNoViolations() {
        val failures = OKAY_EXAMPLES.mapNotNull { example ->
            val errors = lint(example.path, example.code)
            if (errors.isEmpty()) {
                null
            } else {
                "${example.path}: ${errors.joinToString()}"
            }
        }

        assertThat(failures).isEmpty()
    }

    @Test
    fun wrongExamples_areReportedByTheirRule() {
        val failures = WRONG_EXAMPLES.mapNotNull { example ->
            val ruleIds = lint(example.path, example.code)
            if (example.ruleId in ruleIds) {
                null
            } else {
                "${example.path}: expected ${example.ruleId}, got $ruleIds"
            }
        }

        assertThat(failures).isEmpty()
    }

    @Test
    fun nonUtf8File_isReported() {
        val file = projectDir.resolve("src/main/kotlin/Latin1.kt")
        Files.createDirectories(file.parent)
        Files.write(
            file,
            "package com.example\n\nval greeting = \"café\"\n".toByteArray(Charsets.ISO_8859_1)
        )
        copyEditorConfig()

        val ruleIds = mutableListOf<String>()
        engine.lint(Code.fromFile(file.toFile())) { ruleIds += it.ruleId.value }

        assertThat(ruleIds).contains("android-style:source-encoding")
    }

    /** Lints [code] as a file at [path] in the temporary project; returns the rule ids reported. */
    private fun lint(path: String, code: String): List<String> {
        copyEditorConfig()
        val file = projectDir.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, code)
        val ruleIds = mutableListOf<String>()
        engine.lint(Code.fromFile(file.toFile())) { ruleIds += it.ruleId.value }
        Files.delete(file)
        return ruleIds
    }

    private fun copyEditorConfig() {
        val target = projectDir.resolve(".editorconfig")
        if (!Files.exists(target)) {
            val repoRoot = Paths.get(System.getProperty("repoRoot"))
            Files.copy(repoRoot.resolve(".editorconfig"), target)
        }
    }

    private class Example(val path: String, val code: String, val ruleId: String = "")

    private companion object {
        const val MAIN = "src/main/kotlin/com/example"

        /** A WRONG example in package com.example; [code] starts after the package line. */
        fun wrongExample(path: String, code: String, ruleId: String) =
            Example(path, "package com.example\n\n" + code, ruleId)

        val OKAY_EXAMPLES = listOf(
            Example(
                "$MAIN/MyClass.kt",
                """
                package com.example

                /** Example class. */
                class MyClass

                """.trimIndent()
            ),
            Example(
                "$MAIN/Bar.kt",
                """
                package com.example

                /** Example class. */
                class Bar

                fun Runnable.toBar(): Bar = Bar()

                """.trimIndent()
            ),
            Example(
                "$MAIN/Map.kt",
                """
                package com.example

                fun <T, O> Set<T>.map(func: (T) -> O): List<O> = emptyList()
                fun <T, O> List<T>.map(func: (T) -> O): List<O> = emptyList()

                """.trimIndent()
            ),
            Example(
                "$MAIN/extensions.kt",
                """
                package com.example

                fun MyClass.process() = { /* ... */ }
                fun MyResult.print() = { /* ... */ }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Characters.kt",
                """
                package com.example

                fun withUnit(value: Int): String {
                    val unitAbbrev = "μs"
                    return "${'$'}value${'$'}unitAbbrev"
                }

                fun withBom(content: String) = "\ufeff" + content

                """.trimIndent()
            ),
            Example(
                "$MAIN/Licensed.kt",
                """
                /*
                 * Copyright 2017 Google, Inc.
                 *
                 * ...
                 */

                @file:JvmName("Licensed")

                package com.example

                import kotlin.math.abs

                val distance = abs(-1)

                """.trimIndent()
            ),
            Example(
                "$MAIN/Braces.kt",
                """
                package com.example

                fun braces(string: String, value: Int): String {
                    if (string.isEmpty()) return ""

                    val result =
                        if (string.isEmpty()) DEFAULT_VALUE else string

                    when (value) {
                        0 -> return ""
                    }
                    try {
                        doSomething()
                    } catch (e: Exception) {
                    }
                    for (i in 0..1) {
                        println(i)
                    }
                    return result
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Blocks.kt",
                """
                package com.example

                fun blocks(condition: () -> Boolean): Runnable {
                    println("building")
                    return object : MyClass() {
                        override fun foo() {
                            if (condition()) {
                                try {
                                    something()
                                } catch (e: ProblemException) {
                                    recover()
                                }
                            } else if (otherCondition()) {
                                somethingElse()
                            } else {
                                lastThing()
                            }
                        }
                    }
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Expressions.kt",
                """
                package com.example

                fun expressions(string: String): Int {
                    val value = if (string.isEmpty()) 0 else 1 // Okay
                    val other = if (string.isEmpty()) { // Okay
                        0
                    } else {
                        1
                    }
                    return value + other
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Iterables.kt",
                """
                package com.example

                fun <T> Iterable<T>.joinToString(
                    separator: CharSequence = ", ",
                    prefix: CharSequence = "",
                    postfix: CharSequence = ""
                ): String {
                    // ...
                    val joined = prefix.toString() + separator + postfix
                    return joined
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Wrapping.kt",
                """
                package com.example

                fun wrapping(first: String?, second: String, flag: Boolean): Boolean {
                    val result = first ?:
                        second
                    return result.isEmpty() &&
                        flag
                }

                val lengths = listOf("a")
                    .map(String::length)

                """.trimIndent()
            ),
            Example(
                "$MAIN/Properties.kt",
                """
                package com.example

                private val defaultCharset: Charset? =
                    EncodingRegistry.getInstance().getDefaultCharsetForPropertiesFiles(file)

                var directory: File? = null
                    set(value) {
                        // …
                    }

                val defaultExtension: String get() = "kt"

                """.trimIndent()
            ),
            Example(
                "$MAIN/Members.kt",
                """
                package com.example

                /** Example class. */
                class Members(private val name: String) {
                    private val first = 1
                    private val second = 2

                    /** Returns the name. */
                    fun name() = name

                    /** Returns the name twice. */
                    fun twice() = name + name
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Answer.kt",
                """
                package com.example

                /** Possible answers. */
                enum class Answer { YES, NO, MAYBE }

                """.trimIndent()
            ),
            Example(
                "$MAIN/LongAnswer.kt",
                """
                package com.example

                /** Possible answers. */
                enum class LongAnswer {
                    YES,
                    NO,

                    MAYBE {
                        override fun toString() = ""${'"'}¯\_(ツ)_/¯""${'"'}
                    }
                }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Annotations.kt",
                """
                package com.example

                /** Marks global state. */
                @Retention(SOURCE)
                @Target(FUNCTION, PROPERTY_SETTER, FIELD)
                annotation class Global

                @JvmField @Volatile
                var disposable: Disposable? = null

                @Volatile var disposable2: Disposable? = null

                @field:[JvmStatic Volatile]
                var disposable3: Disposable? = null

                """.trimIndent()
            ),
            Example(
                "$MAIN/Constants.kt",
                """
                package com.example

                const val NUMBER = 5
                val NAMES = listOf("Alice", "Bob")
                val AGES = mapOf("Alice" to 35, "Bob" to 32)
                val COMMA_JOINED = NAMES.joinToString(", ")
                val EMPTY_ARRAY = arrayOf<SomeMutableType>()

                """.trimIndent()
            ),
            Example(
                "$MAIN/NonConstants.kt",
                """
                package com.example

                val mutableInstance = Any()
                val mutableInstance2 = Any()

                val variable = "var"
                val nonConstScalar = "non-const"
                val mutableCollection: MutableSet<String> = HashSet()
                val mutableElements = listOf(mutableInstance)
                val mutableValues = mapOf("Alice" to mutableInstance, "Bob" to mutableInstance2)
                val logger = Logger.getLogger(MyClass::class.java.name)
                val nonEmptyArray = arrayOf("these", "can", "change")

                """.trimIndent()
            ),
            Example(
                "$MAIN/Backing.kt",
                """
                package com.example

                private var _table: Map<String, Int>? = null

                val table: Map<String, Int>
                    get() {
                        if (_table == null) {
                            _table = HashMap()
                        }
                        return _table ?: throw AssertionError()
                    }

                """.trimIndent()
            ),
            Example(
                "$MAIN/Generics.kt",
                """
                package com.example

                fun <T : Comparable<T>> max(a: T, b: T): T = if (a > b) a else b

                fun <E, T2, RequestT> choose(e: E, t: T2, request: RequestT) = Triple(e, t, request)

                """.trimIndent()
            ),
            Example(
                "$MAIN/NameTag.kt",
                """
                package com.example

                import androidx.compose.runtime.Composable

                @Composable
                fun NameTag(name: String) {
                    // …
                }

                @Composable
                fun rememberName(): String = "name"

                """.trimIndent()
            ),
            Example(
                "$MAIN/Kdoc.kt",
                """
                package com.example

                /**
                 * Multiple lines of KDoc text are written here,
                 * wrapped normally…
                 */
                fun method(arg: String) {
                    // …
                }

                /** An especially short bit of KDoc. */
                fun short() = Unit

                /**
                 * Adds two numbers.
                 *
                 * @param a The first number, described at length so that the description needs a
                 *     second line.
                 * @param b The second number.
                 * @return The sum.
                 * @throws ArithmeticException Never.
                 * @see Math.addExact
                 */
                fun add(a: Int, b: Int): Int = a + b

                /** Self-explanatory, so the guide's exception applies. */
                @Suppress("ktlint:android-style:kdoc-required")
                class Documented {
                    fun getFoo() = 1
                }

                """.trimIndent()
            ),
            Example(
                "src/test/kotlin/com/example/StackTest.kt",
                """
                package com.example

                import org.junit.Test

                class StackTest {
                    @Test fun pop_emptyStack() {
                        // …
                    }
                }

                """.trimIndent()
            )
        )

        val WRONG_EXAMPLES = listOf(
            // Source files
            wrongExample(
                "$MAIN/Single.kt",
                "/** Doc. */\nclass Other\n",
                "android-style:file-name"
            ),
            wrongExample(
                "$MAIN/Tab.kt",
                "val tabbed = \"a\tb\"\n",
                "android-style:whitespace-characters"
            ),
            wrongExample(
                "$MAIN/Newline.kt",
                "val newline = \"\\u000a\"\n",
                "android-style:unicode-escape"
            ),
            wrongExample(
                "$MAIN/Micro.kt",
                "val unitAbbrev = \"\\u03bcs\"\n",
                "android-style:unicode-escape"
            ),
            Example(
                "$MAIN/KdocHeader.kt",
                "/**\n * Copyright 2017 Google, Inc.\n */\n\npackage com.example\n\nval x = 1\n",
                "standard:kdoc"
            ),
            Example(
                "$MAIN/LineHeader.kt",
                "// Copyright 2017 Google, Inc.\n\npackage com.example\n\nval x = 1\n",
                "android-style:file-structure"
            ),
            Example(
                "$MAIN/NoBlankLine.kt",
                "package com.example\nimport kotlin.math.abs\n\nval x = abs(1)\n",
                "android-style:file-structure"
            ),
            wrongExample(
                "$MAIN/Wildcard.kt",
                "import java.util.*\n\nval x = Date()\n",
                "standard:no-wildcard-imports"
            ),
            wrongExample(
                "$MAIN/ImportOrder.kt",
                "import kotlin.math.max\nimport kotlin.math.abs\n\n" +
                    "val x = abs(max(1, 2))\n",
                "standard:import-ordering"
            ),
            // Braces
            wrongExample(
                "$MAIN/IfWrapped.kt",
                "fun f(s: String) {\n    if (s.isEmpty())\n        return\n}\n",
                "standard:multiline-if-else"
            ),
            wrongExample(
                "$MAIN/IfElse.kt",
                "fun f(s: String) {\n    if (s.isEmpty()) return\n" +
                    "    else println(s)\n}\n",
                "standard:multiline-if-else"
            ),
            wrongExample(
                "$MAIN/IfExpression.kt",
                "fun f(s: String) {\n    val value = if (s.isEmpty())\n" +
                    "        0\n    else\n        1\n    println(value)\n}\n",
                "standard:multiline-if-else"
            ),
            wrongExample(
                "$MAIN/ForBody.kt",
                "fun f(xs: List<Int>) {\n    for (x in xs) println(x)\n}\n",
                "android-style:mandatory-braces"
            ),
            wrongExample(
                "$MAIN/WhileBody.kt",
                "fun f(n: Int) {\n    var i = n\n    while (i > 0) i--\n}\n",
                "android-style:mandatory-braces"
            ),
            wrongExample(
                "$MAIN/WhenBranch.kt",
                "fun f(v: Int) {\n    when (v) {\n        0 ->\n" +
                    "            println(1)\n        else -> println(2)\n    }\n}\n",
                "android-style:mandatory-braces"
            ),
            wrongExample(
                "$MAIN/EmptyBlock.kt",
                "fun f() {\n    try {\n        println()\n" +
                    "    } catch (e: Exception) {}\n}\n",
                "android-style:empty-block"
            ),
            wrongExample(
                "$MAIN/BraceOnOwnLine.kt",
                "fun f(s: String)\n{\n    println(s)\n}\n",
                "standard:curly-spacing"
            ),
            wrongExample(
                "$MAIN/ElseOnNewLine.kt",
                "fun f(b: Boolean) {\n    if (b) {\n        println(1)\n    }\n" +
                    "    else {\n        println(2)\n    }\n}\n",
                "standard:keyword-spacing"
            ),
            // Indentation, statements, line length
            wrongExample(
                "$MAIN/Tabs.kt",
                "fun f() {\n\tprintln()\n}\n",
                "standard:indent"
            ),
            wrongExample(
                "$MAIN/Semicolons.kt",
                "fun f() {\n    println(1); println(2)\n}\n",
                "standard:statement-wrapping"
            ),
            wrongExample(
                "$MAIN/LongLine.kt",
                "val long = \"" + "x".repeat(100) + "\" + \"x\"\n",
                "standard:max-line-length"
            ),
            wrongExample(
                "$MAIN/LongKdoc.kt",
                "/**\n * " + "word ".repeat(24) + "end.\n */\nval x = 1\n",
                "android-style:comment-line-length"
            ),
            // Where to break
            wrongExample(
                "$MAIN/BreakBeforePlus.kt",
                "fun f(a: Int, b: Int) = (\n    a\n        + b\n)\n",
                "android-style:operator-wrapping"
            ),
            wrongExample(
                "$MAIN/BreakBeforeElvis.kt",
                "fun f(a: String?, b: String) = a\n    ?: b\n",
                "android-style:operator-wrapping"
            ),
            wrongExample(
                "$MAIN/BreakBeforeInfix.kt",
                "fun f(a: Boolean, b: Boolean) = (\n    a\n        and b\n)\n",
                "android-style:operator-wrapping"
            ),
            wrongExample(
                "$MAIN/BreakAfterSafeCall.kt",
                "fun f(a: String?) = a?.\n    trim()\n",
                "android-style:operator-wrapping"
            ),
            wrongExample(
                "$MAIN/BreakAfterDot.kt",
                "fun f(a: String) = a.\n    trim()\n",
                "standard:dot-spacing"
            ),
            wrongExample(
                "$MAIN/Signature.kt",
                "fun join(\n    separator: CharSequence, prefix: CharSequence,\n" +
                    "    postfix: CharSequence): String =\n" +
                    "    separator.toString() + prefix + postfix\n",
                "standard:parameter-list-wrapping"
            ),
            wrongExample(
                "$MAIN/PropertyBreak.kt",
                "val defaultName: String\n    = \"name\"\n",
                "standard:no-line-break-before-assignment"
            ),
            // Whitespace
            wrongExample(
                "$MAIN/MemberSpacing.kt",
                "/** Doc. */\nclass MemberSpacing {\n    private fun a() = 1\n" +
                    "    private fun b() = a()\n}\n",
                "android-style:member-spacing"
            ),
            wrongExample(
                "$MAIN/EnumSpacing.kt",
                "/** Doc. */\nenum class EnumSpacing {\n    YES,\n    MAYBE {\n" +
                    "        override fun toString() = \"x\"\n    }\n}\n",
                "android-style:enum-entry-spacing"
            ),
            wrongExample(
                "$MAIN/ForSpace.kt",
                "fun f() {\n    for(i in 0..1) {\n        println(i)\n    }\n}\n",
                "standard:keyword-spacing"
            ),
            wrongExample(
                "$MAIN/ElseSpace.kt",
                "fun f(b: Boolean) {\n    if (b) {\n        println(1)\n" +
                    "    }else {\n        println(2)\n    }\n}\n",
                "standard:curly-spacing"
            ),
            wrongExample(
                "$MAIN/BraceSpace.kt",
                "fun f(b: Boolean) {\n    if (b){\n        println(1)\n    }\n}\n",
                "standard:curly-spacing"
            ),
            wrongExample(
                "$MAIN/Plus.kt",
                "val two = 1+1\n",
                "standard:op-spacing"
            ),
            wrongExample(
                "$MAIN/Arrow.kt",
                "val strings = listOf(1).map { value->value.toString() }\n",
                "standard:op-spacing"
            ),
            wrongExample(
                "$MAIN/MemberReference.kt",
                "val toStr = Any :: toString\n",
                "standard:double-colon-spacing"
            ),
            wrongExample(
                "$MAIN/DotSpace.kt",
                "val strings = listOf(1).map { it . toString() }\n",
                "standard:dot-spacing"
            ),
            wrongExample(
                "$MAIN/Range.kt",
                "val range = 1 .. 4\n",
                "standard:range-spacing"
            ),
            wrongExample(
                "$MAIN/Supertype.kt",
                "/** Doc. */\ninterface Base\n\n/** Doc. */\nclass Foo: Base\n",
                "standard:colon-spacing"
            ),
            wrongExample(
                "$MAIN/Bound.kt",
                "fun <T: Comparable<T>> biggest(a: T, b: T): T = maxOf(a, b)\n",
                "standard:colon-spacing"
            ),
            wrongExample(
                "$MAIN/Where.kt",
                "fun <T> biggest(a: T, b: T): T where T: Comparable<T> =\n" +
                    "    maxOf(a, b)\n",
                "standard:colon-spacing"
            ),
            wrongExample(
                "$MAIN/Comma.kt",
                "val oneAndTwo = listOf(1,2)\n",
                "standard:comma-spacing"
            ),
            wrongExample(
                "$MAIN/Comment.kt",
                "var debugging = false//disabled by default\n",
                "standard:comment-spacing"
            ),
            // Specific constructs
            wrongExample(
                "$MAIN/AnnotationLine.kt",
                "@Suppress(\"unused\") fun annotated() = Unit\n",
                "standard:annotation"
            ),
            wrongExample(
                "$MAIN/AnnotationArray.kt",
                "@[JvmOverloads Synchronized]\nfun bracket(a: Int = 1) = a\n",
                "android-style:annotation-array"
            ),
            // Naming
            wrongExample(
                "$MAIN/NonAscii.kt",
                "fun f() {\n    val café = 1\n    println(café)\n}\n",
                "android-style:ascii-identifiers"
            ),
            wrongExample(
                "$MAIN/Hungarian.kt",
                "/** Doc. */\nclass Hungarian {\n    private val mName = \"n\"\n\n" +
                    "    /** Returns the name. */\n    fun name() = mName\n}\n",
                "android-style:naming-prefix"
            ),
            Example(
                "src/main/kotlin/com/example/deepSpace/DeepSpace.kt",
                "package com.example.deepSpace\n\nval x = 1\n",
                "android-style:package-name"
            ),
            Example(
                "src/main/kotlin/com/example/deep_space/DeepSpace.kt",
                "package com.example.deep_space\n\nval x = 1\n",
                "standard:package-name"
            ),
            wrongExample(
                "$MAIN/Lowercase.kt",
                "/** Doc. */\nclass lowercase\n\nval x = 1\n",
                "standard:class-naming"
            ),
            wrongExample(
                "$MAIN/Upper.kt",
                "fun SendMessage() = Unit\n",
                "standard:function-naming"
            ),
            wrongExample(
                "src/test/kotlin/com/example/SpacesTest.kt",
                "import org.junit.Test\n\nclass SpacesTest {\n    @Test\n" +
                    "    fun `test every possible case`() = Unit\n}\n",
                "android-style:ascii-identifiers"
            ),
            wrongExample(
                "$MAIN/ComposableUnit.kt",
                "import androidx.compose.runtime.Composable\n\n@Composable\n" +
                    "fun nameTag(name: String) {\n    println(name)\n}\n",
                "android-style:composable-naming"
            ),
            wrongExample(
                "$MAIN/ComposableValue.kt",
                "import androidx.compose.runtime.Composable\n\n@Composable\n" +
                    "fun RememberName(): String = \"name\"\n",
                "android-style:composable-naming"
            ),
            wrongExample(
                "$MAIN/ConstName.kt",
                "const val maxLength = 280\n",
                "standard:property-naming"
            ),
            wrongExample(
                "$MAIN/MissingConst.kt",
                "val NUMBER = 5\n",
                "android-style:constant"
            ),
            wrongExample(
                "$MAIN/ImmutableList.kt",
                "val names = listOf(\"Alice\", \"Bob\")\n",
                "android-style:constant"
            ),
            wrongExample(
                "$MAIN/ClassConstant.kt",
                "/** Doc. */\nclass ClassConstant {\n    /** Names. */\n" +
                    "    val NAMES = listOf(\"a\")\n}\n",
                "standard:property-naming"
            ),
            wrongExample(
                "$MAIN/Parameter.kt",
                "fun param(MyParam: Int) = MyParam\n",
                "android-style:parameter-naming"
            ),
            wrongExample(
                "$MAIN/Backing.kt",
                "private val _items = mutableListOf<Int>()\n\n" +
                    "val values: List<Int>\n    get() = _items\n",
                "android-style:backing-property-naming"
            ),
            wrongExample(
                "$MAIN/TypeVariable.kt",
                "fun <Item> first(items: List<Item>): Item = items[0]\n",
                "android-style:type-parameter-naming"
            ),
            wrongExample(
                "$MAIN/XMLHTTPRequest.kt",
                "/** Doc. */\nclass XMLHTTPRequest\n",
                "android-style:camel-case"
            ),
            wrongExample(
                "$MAIN/CustomerId.kt",
                "val newCustomerID = Any()\n",
                "android-style:camel-case"
            ),
            wrongExample(
                "src/test/kotlin/com/example/HashTests.kt",
                "import org.junit.Test\n\nclass HashTests {\n    @Test\n" +
                    "    fun hash_isStable() = Unit\n}\n",
                "android-style:test-class-naming"
            ),
            // Documentation
            wrongExample(
                "$MAIN/Undocumented.kt",
                "class Undocumented\n",
                "android-style:kdoc-required"
            ),
            wrongExample(
                "$MAIN/UndocumentedMember.kt",
                "/** Doc. */\nclass UndocumentedMember {\n" +
                    "    fun send() = Unit\n}\n",
                "android-style:kdoc-required"
            ),
            wrongExample(
                "$MAIN/SummaryCase.kt",
                "/** returns the thing. */\nfun thing() = 1\n",
                "android-style:kdoc-summary"
            ),
            wrongExample(
                "$MAIN/SummaryPunctuation.kt",
                "/** Returns the thing */\nfun thing() = 1\n",
                "android-style:kdoc-summary"
            ),
            wrongExample(
                "$MAIN/SummarySentence.kt",
                "/** This method returns the thing. */\nfun thing() = 1\n",
                "android-style:kdoc-summary"
            ),
            wrongExample(
                "$MAIN/TagOrder.kt",
                "/**\n * Adds.\n *\n * @return The sum.\n * @param a The first.\n */\n" +
                    "fun add(a: Int) = a\n",
                "android-style:kdoc-format"
            ),
            wrongExample(
                "$MAIN/EmptyTag.kt",
                "/**\n * Adds.\n *\n * @param a\n */\nfun add(a: Int) = a\n",
                "android-style:kdoc-format"
            ),
            wrongExample(
                "$MAIN/TagsWithoutBlank.kt",
                "/**\n * Adds.\n * @param a The first.\n */\nfun add(a: Int) = a\n",
                "android-style:kdoc-format"
            ),
            wrongExample(
                "$MAIN/KdocLayout.kt",
                "/** Multiple lines\n * of text. */\nfun text() = 1\n",
                "android-style:kdoc-format"
            )
        )
    }
}
