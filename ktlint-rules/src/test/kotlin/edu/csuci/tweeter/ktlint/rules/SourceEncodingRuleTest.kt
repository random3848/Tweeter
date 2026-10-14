package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SourceEncodingRuleTest {
    private val assertThatCode = assertThatRule { SourceEncodingRule() }

    @TempDir
    lateinit var tempDir: File

    /** Reads [bytes] back the way the ktlint CLI reads a source file. */
    private fun readLikeKtlint(bytes: ByteArray): String {
        val file = File(tempDir, "Source.kt")
        file.writeBytes(bytes)
        return file.readText()
    }

    @Test
    fun utf8File_noViolation() {
        val code = readLikeKtlint("val s = \"café\"\n".toByteArray(Charsets.UTF_8))
        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun latin1File_isReported() {
        val code = readLikeKtlint("val s = \"café\"\n".toByteArray(Charsets.ISO_8859_1))
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(
                1,
                13,
                "Source files are UTF-8; this file has bytes that are not valid UTF-8"
            )
    }

    @Test
    fun onlyFirstMalformedSequence_isReported() {
        val code =
            readLikeKtlint("// ok\nval a = \"é\"\nval b = \"é\"\n".toByteArray(Charsets.ISO_8859_1))
        assertThatCode(code)
            .hasLintViolationWithoutAutoCorrect(
                2,
                10,
                "Source files are UTF-8; this file has bytes that are not valid UTF-8"
            )
    }
}
