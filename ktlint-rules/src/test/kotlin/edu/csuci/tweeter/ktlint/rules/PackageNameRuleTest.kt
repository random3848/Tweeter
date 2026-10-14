package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class PackageNameRuleTest {
    private val assertThatCode = assertThatRule { PackageNameRule() }

    @Test
    fun lowercaseSegments_noViolation() {
        assertThatCode("package com.example.deepspace\n").hasNoLintViolations()
    }

    @Test
    fun camelCaseSegment_isReported() {
        assertThatCode("package com.example.deepSpace\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                21,
                "Package names are all lowercase: 'com.example.deepSpace'"
            )
    }
}
