package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * Each KDoc begins with a brief summary fragment, capitalized and punctuated like a sentence.
 *
 * The summary is the first paragraph of the description: the lines before the first blank line,
 * block tag, or fenced code block. It ends with `.`, `?`, `!`, or `…`, and is not a sentence
 * about the element such as `This method returns…` or `A Foo is a…`. Only KDoc attached to a
 * declaration is checked; `standard:kdoc` reports dangling KDoc.
 */
internal class KdocSummaryRule : AndroidStyleRule("kdoc-summary") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != KDOC || node.treeParent?.psi !is KtDeclaration) {
            return
        }
        val text = node.text
        if (text.length < 5 || !text.startsWith("/**") || !text.endsWith("*/")) {
            return
        }
        val start = node.startOffset
        val summary = summaryLines(text)
        if (summary.isEmpty()) {
            emit(start, "KDoc begins with a summary fragment", false)
            return
        }
        val first = summary.first()
        val firstText = first.content.trim()
        if (firstText.first().isLowerCase()) {
            emit(
                start + first.textStart,
                "The KDoc summary is capitalized: '${firstText.substringBefore(' ')}'",
                false
            )
        }
        val last = summary.last()
        val lastText = last.content.trimEnd()
        if (lastText.last() !in TERMINAL_PUNCTUATION) {
            emit(
                start + last.contentStart + lastText.length - 1,
                "The KDoc summary is punctuated like a sentence",
                false
            )
        }
        val joined = summary.joinToString(" ") { it.content.trim() }
        (THIS_ELEMENT.find(joined) ?: ARTICLE_IS.find(joined))?.let { sentence ->
            emit(
                start + first.textStart,
                "The KDoc summary is a fragment, not a sentence like '${sentence.value.trim()}...'",
                false
            )
        }
    }

    /** Returns the lines of the first description paragraph, or none when it is missing. */
    private fun summaryLines(text: String): List<KdocLine> {
        val lines = parseLines(text)
        val first = lines.indexOfFirst { it.content.isNotBlank() }
        if (first < 0 || TAG.containsMatchIn(lines[first].content)) {
            return emptyList()
        }
        return lines
            .drop(first)
            .takeWhile { line ->
                line.content.isNotBlank() &&
                    !TAG.containsMatchIn(line.content) &&
                    !FENCE.containsMatchIn(line.content)
            }
    }

    /** Splits KDoc text into lines; offsets are relative to the start of the KDoc. */
    private fun parseLines(text: String): List<KdocLine> {
        val lines = mutableListOf<KdocLine>()
        var lineStart = 0
        while (true) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) text.length - 2 else newline
            var contentStart = lineStart
            if (lineStart == 0) {
                contentStart = 3
            } else {
                val lead = (lineStart until lineEnd).firstOrNull { !text[it].isWhitespace() }
                if (lead != null && text[lead] == '*') {
                    contentStart = lead + 1
                }
            }
            val content = text.substring(contentStart, maxOf(contentStart, lineEnd))
            lines += KdocLine(contentStart, content)
            if (newline < 0) {
                return lines
            }
            lineStart = newline + 1
        }
    }

    /** One KDoc line; [content] follows the opening delimiter or leading `*`, minus the closer. */
    private class KdocLine(val contentStart: Int, val content: String) {
        /** Offset of the first non-space character of the content. */
        val textStart: Int
            get() = contentStart + content.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    }

    private companion object {
        val TAG = Regex("""^\s*@[A-Za-z]""")
        val FENCE = Regex("""^\s*(```|~~~)""")
        val TERMINAL_PUNCTUATION = setOf('.', '?', '!', '…')
        val THIS_ELEMENT = Regex(
            """^This (method|function|class|property|object|interface|constructor|field|enum|""" +
                """file)\b"""
        )
        val ARTICLE_IS = Regex("""^(A|An|The) (`[^`]+`|[^ ]+) (is|are) """)
    }
}
