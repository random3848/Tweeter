package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * KDoc is laid out as the guide's Formatting and Block tags sections describe.
 *
 * The single-line form has no block tags. The multi-line form has its opening and closing
 * delimiters on their own lines, and every line between starts with `*`. One blank line separates
 * paragraphs and precedes the block tags. The standard block tags are in the guide's order, have
 * a description, and their continuation lines are indented 4 spaces from the `@`. Only KDoc
 * attached to a declaration is checked; `standard:kdoc` reports dangling KDoc. Lines in fenced
 * code blocks are not checked for tags, blank lines, or indentation.
 */
internal class KdocFormatRule : AndroidStyleRule("kdoc-format") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != KDOC || node.treeParent?.psi !is KtDeclaration) {
            return
        }
        val text = node.text
        if (text.length < 5 || !text.startsWith("/**") || !text.endsWith("*/")) {
            return
        }
        if ('\n' in text) {
            checkMultiLine(text, node.startOffset, emit)
        } else {
            checkSingleLine(text, node.startOffset, emit)
        }
    }

    private fun checkSingleLine(text: String, start: Int, emit: Emit) {
        val tag = TAG.find(text.substring(3, text.length - 2)) ?: return
        emit(
            start + 3 + tag.atIndex,
            "The single-line KDoc form is only for KDoc without block tags: '@${tag.tagName}'",
            false
        )
    }

    private fun checkMultiLine(text: String, start: Int, emit: Emit) {
        val lines = parseLines(text)
        val first = lines.first()
        if (!first.isBlank) {
            emit(start + first.textStart, "Multi-line KDoc starts with /** on its own line", false)
        }
        lines.subList(1, lines.lastIndex)
            .filter { !it.hasAsterisk }
            .forEach { line ->
                emit(
                    start + line.textStart,
                    "Each line of multi-line KDoc starts with *",
                    false
                )
            }
        if (text.substring(lines.last().lineStart, text.length - 2).isNotBlank()) {
            emit(start + text.length - 2, "Multi-line KDoc ends with */ on its own line", false)
        }
        val body = bodyLines(lines)
        checkParagraphs(body, start, emit)
        checkBlockTags(body, start, emit)
    }

    private fun checkParagraphs(body: List<BodyLine>, start: Int, emit: Emit) {
        body.zipWithNext().forEach { (previous, current) ->
            if (previous.isBlankText && current.isBlankText) {
                emit(
                    start + current.line.markerStart,
                    "One blank line separates KDoc paragraphs",
                    false
                )
            }
        }
        val firstTag = body.indexOfFirst { it.tag != null }
        if (firstTag > 0 &&
            body.subList(0, firstTag).any { !it.line.isBlank } &&
            !body[firstTag - 1].line.isBlank
        ) {
            emit(start + body[firstTag].atOffset, "A blank line comes before the block tags", false)
        }
    }

    private fun checkBlockTags(body: List<BodyLine>, start: Int, emit: Emit) {
        val tagIndices = body.indices.filter { body[it].tag != null }
        checkTagOrder(tagIndices.map { body[it] }, start, emit)
        tagIndices.forEachIndexed { i, tagIndex ->
            val end = tagIndices.getOrElse(i + 1) { body.size }
            val tagLine = body[tagIndex]
            val continuation = body.subList(tagIndex + 1, end).filter { !it.line.isBlank }
            checkTagDescription(tagLine, continuation, start, emit)
            checkContinuationIndent(tagLine, continuation, start, emit)
        }
    }

    private fun checkTagOrder(tags: List<BodyLine>, start: Int, emit: Emit) {
        var latest: BodyLine? = null
        for (tag in tags) {
            val rank = TAG_ORDER[tag.tagName] ?: continue
            if (latest != null && rank < TAG_ORDER.getValue(latest.tagName)) {
                emit(
                    start + tag.atOffset,
                    "Block tags are in the order @constructor, @receiver, @param, @property, " +
                        "@return, @throws, @see: '@${tag.tagName}' follows '@${latest.tagName}'",
                    false
                )
                return
            }
            latest = tag
        }
    }

    private fun checkTagDescription(
        tagLine: BodyLine,
        continuation: List<BodyLine>,
        start: Int,
        emit: Emit
    ) {
        val name = tagLine.tagName
        val words = (listOf(tagLine.textAfterTag) + continuation.map { it.line.content })
            .joinToString(" ")
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
        val message = when (name) {
            in SUBJECT_TAGS -> {
                when (words.size) {
                    0 -> "Block tag @$name has no subject and no description"
                    1 -> "Block tag @$name ${words[0]} has no description"
                    else -> null
                }
            }

            "see" -> if (words.isEmpty()) "Block tag @see has no reference" else null

            in TEXT_TAGS -> if (words.isEmpty()) "Block tag @$name has no description" else null

            else -> null
        }
        message?.let { emit(start + tagLine.atOffset, it, false) }
    }

    private fun checkContinuationIndent(
        tagLine: BodyLine,
        continuation: List<BodyLine>,
        start: Int,
        emit: Emit
    ) {
        val expected = tagLine.atOffset - tagLine.line.lineStart + 4
        continuation
            .filter { !it.isCode || it.isFence }
            .filter { it.line.textStart - it.line.lineStart != expected }
            .forEach { line ->
                emit(
                    start + line.line.textStart,
                    "Block tag continuation lines are indented 4 spaces from the @",
                    false
                )
            }
    }

    /** Keeps the first and last lines only when they hold text, and marks fenced code lines. */
    private fun bodyLines(lines: List<KdocLine>): List<BodyLine> {
        var inFence = false
        return lines
            .filterIndexed { i, line -> (i != 0 && i != lines.lastIndex) || !line.isBlank }
            .map { line ->
                val isFence = FENCE.containsMatchIn(line.content)
                val bodyLine = BodyLine(line, inFence || isFence, isFence)
                if (isFence) {
                    inFence = !inFence
                }
                bodyLine
            }
    }

    /** Splits KDoc text into lines; offsets are relative to the start of the KDoc. */
    private fun parseLines(text: String): List<KdocLine> {
        val lines = mutableListOf<KdocLine>()
        var lineStart = 0
        while (true) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) text.length - 2 else newline
            var hasAsterisk = false
            var contentStart = lineStart
            if (lineStart == 0) {
                contentStart = 3
            } else {
                val lead = (lineStart until lineEnd).firstOrNull { !text[it].isWhitespace() }
                if (lead != null && text[lead] == '*') {
                    hasAsterisk = true
                    contentStart = lead + 1
                }
            }
            lines += KdocLine(
                lineStart,
                contentStart,
                text.substring(contentStart, maxOf(contentStart, lineEnd)),
                hasAsterisk
            )
            if (newline < 0) {
                return lines
            }
            lineStart = newline + 1
        }
    }

    /** One KDoc line; [content] follows the opening delimiter or leading `*`, minus the closer. */
    private class KdocLine(
        val lineStart: Int,
        val contentStart: Int,
        val content: String,
        val hasAsterisk: Boolean
    ) {
        val isBlank: Boolean
            get() = content.isBlank()

        /** Offset of the first non-space character of the content, or the content start. */
        val textStart: Int
            get() = contentStart + content.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)

        /** Offset of the leading `*`, or of the line start when there is none. */
        val markerStart: Int
            get() = if (hasAsterisk) contentStart - 1 else lineStart
    }

    /** A line of the KDoc body, classified as code, fence, block tag, or text. */
    private class BodyLine(val line: KdocLine, val isCode: Boolean, val isFence: Boolean) {
        val tag: MatchResult? = if (isCode) null else TAG.find(line.content)

        val isBlankText: Boolean
            get() = !isCode && line.isBlank

        val tagName: String
            get() = tag?.tagName.orEmpty()

        val atOffset: Int
            get() = line.contentStart + (tag?.atIndex ?: 0)

        val textAfterTag: String
            get() = tag?.let { line.content.substring(it.range.last + 1) }.orEmpty()
    }

    private companion object {
        val TAG = Regex("""^\s*@([A-Za-z]+)""")
        val FENCE = Regex("""^\s*(```|~~~)""")
        val WHITESPACE = Regex("""\s+""")
        val TAG_ORDER = mapOf(
            "constructor" to 0,
            "receiver" to 1,
            "param" to 2,
            "property" to 3,
            "return" to 4,
            "throws" to 5,
            "exception" to 5,
            "see" to 6
        )
        val SUBJECT_TAGS = setOf("param", "property", "throws", "exception")
        val TEXT_TAGS = setOf("return", "constructor", "receiver")

        val MatchResult.tagName: String
            get() = groupValues[1]

        val MatchResult.atIndex: Int
            get() = groups[1]!!.range.first - 1
    }
}
