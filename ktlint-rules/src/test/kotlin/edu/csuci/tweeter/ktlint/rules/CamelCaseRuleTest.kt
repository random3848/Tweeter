package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class CamelCaseRuleTest {
    private val assertThatCode = assertThatRule { CamelCaseRule() }

    @Test
    fun guideCamelCaseNames_noViolation() {
        val code =
            """
            const val MAX_COUNT = 8
            const val ID = 1

            class XmlHttpRequest(val newCustomerId: Int) {
                private val innerStopwatch = Any()

                fun supportsIpv6OnIos(): Boolean = true
            }

            class YouTubeImporter

            enum class Status { OK, NOT_FOUND }
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun acronymsInCapitals_areReported() {
        val code =
            """
            class XMLHTTPRequest(val newCustomerID: Int) {
                fun supportsIPv6OnIOS(userID: Long): Boolean {
                    val parsedURL = userID.toString()
                    val (rawJSON, _) = Pair(parsedURL, newCustomerID)
                    return rawJSON.isEmpty()
                }
            }

            object HTTPClient

            interface IOHandler

            typealias JSONMap = Map<String, Any>

            enum class Status { OK, NotFOUND }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            acronym(1, 7, "XMLHTTPRequest"),
            acronym(1, 26, "newCustomerID"),
            acronym(2, 9, "supportsIPv6OnIOS"),
            acronym(2, 27, "userID"),
            acronym(3, 13, "parsedURL"),
            acronym(4, 14, "rawJSON"),
            acronym(9, 8, "HTTPClient"),
            acronym(11, 11, "IOHandler"),
            acronym(13, 11, "JSONMap"),
            acronym(15, 25, "NotFOUND")
        )
    }

    @Test
    fun typeParametersAndBacktickedNames_areSkipped() {
        val code =
            """
            class Cache<KT, RequestT>

            fun `getURL`(): String = ""
            """.trimIndent()
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun overrides_areSkipped() {
        val code =
            """
            interface Loader {
                val baseURL: String

                fun load(requestID: Int)
            }

            class Impl : Loader {
                override val baseURL = ""

                override fun load(requestID: Int) = Unit
            }
            """.trimIndent()
        assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
            acronym(2, 9, "baseURL"),
            acronym(4, 14, "requestID")
        )
    }

    @Test
    fun oneLetterWord_isReportedUnlessSuppressed() {
        val code =
            """
            val XAxis = 0

            @Suppress("ktlint:android-style:camel-case")
            val YAxis = 1
            """.trimIndent()
        assertThatCode(code).hasLintViolationWithoutAutoCorrect(
            1,
            5,
            "Acronyms are written as words in camel case (XmlHttpRequest, not XMLHTTPRequest): " +
                "'XAxis'"
        )
    }

    private fun acronym(line: Int, col: Int, name: String) = LintViolation(
        line,
        col,
        "Acronyms are written as words in camel case (XmlHttpRequest, not XMLHTTPRequest): " +
            "'$name'"
    )
}
