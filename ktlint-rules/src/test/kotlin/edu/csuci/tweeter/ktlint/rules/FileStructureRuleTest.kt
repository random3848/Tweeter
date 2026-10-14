package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

private const val LINE_COMMENT_HEADER =
    "A copyright or license header is a /* */ block comment, not // comments"
private const val HEADER_THEN_PACKAGE =
    "Exactly one blank line separates the copyright/license header from the package statement"
private const val PACKAGE_THEN_IMPORTS =
    "Exactly one blank line separates the package statement from the imports"
private const val PACKAGE_THEN_DECLARATIONS =
    "Exactly one blank line separates the package statement from the top-level declarations"
private const val IMPORTS_THEN_DECLARATIONS =
    "Exactly one blank line separates the imports from the top-level declarations"

class FileStructureRuleTest {
    private val assertThatCode = assertThatRule { FileStructureRule() }

    @Test
    fun allSectionsSeparatedByBlankLines_noViolation() {
        val code =
            """
            /*
             * Copyright 2026 Tweeter
             */

            @file:JvmName("Foo")

            package com.example

            import com.example.a.Bar
            import com.example.b.Baz

            /** A foo. */
            class Foo(bar: Bar, baz: Baz)
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun lineCommentHeader_isReported() {
        val code =
            """
            // Copyright 2026 Tweeter
            // Licensed under the Apache License.

            package com.example
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(1, 1, LINE_COMMENT_HEADER)
    }

    @Test
    fun kdocHeader_noViolation() {
        val code =
            """
            /** Copyright 2026 Tweeter */

            package com.example
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun headerWithoutBlankLine_isReported() {
        val code =
            """
            /* Copyright 2026 Tweeter */
            package com.example
            """.trimIndent()
        val formattedCode =
            """
            /* Copyright 2026 Tweeter */

            package com.example
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 1, HEADER_THEN_PACKAGE)
            .isFormattedAs(formattedCode)
    }

    @Test
    fun packageThenImportsWithoutBlankLine_isReported() {
        val code =
            """
            package com.example
            import com.example.a.Bar

            class Foo(bar: Bar)
            """.trimIndent()
        val formattedCode =
            """
            package com.example

            import com.example.a.Bar

            class Foo(bar: Bar)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 1, PACKAGE_THEN_IMPORTS)
            .isFormattedAs(formattedCode)
    }

    @Test
    fun packageThenDeclarationWithoutBlankLine_isReported() {
        val code =
            """
            package com.example
            class Foo
            """.trimIndent()
        val formattedCode =
            """
            package com.example

            class Foo
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(2, 1, PACKAGE_THEN_DECLARATIONS)
            .isFormattedAs(formattedCode)
    }

    @Test
    fun importsThenDocumentedDeclarationWithoutBlankLine_isReported() {
        val code =
            """
            package com.example

            import com.example.a.Bar
            /** A foo. */
            class Foo(bar: Bar)
            """.trimIndent()
        val formattedCode =
            """
            package com.example

            import com.example.a.Bar

            /** A foo. */
            class Foo(bar: Bar)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolation(4, 1, IMPORTS_THEN_DECLARATIONS)
            .isFormattedAs(formattedCode)
    }

    @Test
    fun everySectionWithoutBlankLine_isReported() {
        val code =
            """
            /* Copyright 2026 Tweeter */
            package com.example
            import com.example.a.Bar
            class Foo(bar: Bar)
            """.trimIndent()
        val formattedCode =
            """
            /* Copyright 2026 Tweeter */

            package com.example

            import com.example.a.Bar

            class Foo(bar: Bar)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolations(
                LintViolation(2, 1, HEADER_THEN_PACKAGE),
                LintViolation(3, 1, PACKAGE_THEN_IMPORTS),
                LintViolation(4, 1, IMPORTS_THEN_DECLARATIONS)
            ).isFormattedAs(formattedCode)
    }

    @Test
    fun fileAnnotationThenPackageWithoutBlankLine_noViolation() {
        val code =
            """
            @file:JvmName("Foo")
            package com.example
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun commentOnFirstDeclarationWithoutPackage_noViolation() {
        val code =
            """
            // Says hello.
            fun hello() = Unit
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun wrappedPackageStatement_isReported() {
        val code =
            """
            package com
                .example
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(1, 1, "The package statement is never line-wrapped")
    }

    @Test
    fun wrappedImportStatement_isReported() {
        val code =
            """
            package com.example

            import com.example.a.Bar
            import com.example.b
                .Baz

            class Foo(bar: Bar, baz: Baz)
            """.trimIndent()
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(4, 1, "Import statements are never line-wrapped")
    }

    @Test
    fun script_noViolation() {
        val code =
            """
            // Build script.
            import com.example.a.Bar
            val bar = Bar()
            """.trimIndent()
        assertThatCode(code).asKotlinScript().hasNoLintViolations()
    }
}
