package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.COMMA
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPEN_QUOTE
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY_OFF
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RULE_EXECUTION_PROPERTY_TYPE
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.ktLintRuleExecutionPropertyName
import com.pinterest.ktlint.rule.engine.core.api.isPartOf
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/** The ktlint rule whose `max_line_length` this rule shares. */
private val MAX_LINE_LENGTH_RULE_ID = RuleId("standard:max-line-length")

/** Comment text that opens or closes a fenced code block. */
private val FENCE = Regex("""^(```|~~~)""")

/** What the non-whitespace text of a line consists of. */
private enum class LineContent { BLANK, COMMENT, STRING, OTHER }

/**
 * Comment lines and lines holding only a string literal stay within the column limit, like all
 * other lines.
 *
 * Complements `standard:max-line-length`, which skips KDoc, lines holding only comments, and lines
 * holding only a single string template (optionally followed by a comma); this rule reports exactly
 * those lines, using the same `max_line_length`, and only while `standard:max-line-length` is
 * enabled. As the guide allows, lines containing a URL (`://`) and shell command lines are exempt:
 * lines inside a code block fenced by three backticks or `~~~` in a comment, and comment lines
 * whose text starts with `$ `. Raw string lines are never reported. Each violation is reported
 * while visiting the last leaf of its line, so `@Suppress` on an enclosing declaration applies.
 */
internal class CommentLineLengthRule :
    AndroidStyleRule(
        "comment-line-length",
        usesEditorConfigProperties = setOf(MAX_LINE_LENGTH_PROPERTY)
    ) {
    private var maxLineLength = MAX_LINE_LENGTH_PROPERTY_OFF

    /** Offsets of the violations in the file, by the last leaf of the line they are on. */
    private var violations: Map<ASTNode, List<Int>>? = null

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        violations = null
        val standardRuleExecution =
            editorConfig.getEditorConfigValueOrNull(
                RULE_EXECUTION_PROPERTY_TYPE,
                MAX_LINE_LENGTH_RULE_ID.ktLintRuleExecutionPropertyName()
            )
        maxLineLength =
            if (standardRuleExecution == RuleExecution.enabled) {
                editorConfig[MAX_LINE_LENGTH_PROPERTY]
            } else {
                MAX_LINE_LENGTH_PROPERTY_OFF
            }
        if (maxLineLength == MAX_LINE_LENGTH_PROPERTY_OFF) {
            stopTraversalOfAST()
        }
    }

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        // The whole file is scanned up front, as code fences span lines; each violation is then
        // emitted while visiting a node on its line, which is what suppression is checked against.
        val fileViolations =
            violations ?: findViolations(generateSequence(node) { it.treeParent }.last())
                .also { violations = it }
        fileViolations[node]?.forEach { offset -> emit(offset, message(), false) }
    }

    private fun message(): String = "Exceeded max line length ($maxLineLength)"

    /** The offsets of the violations in [file], keyed by the last leaf of their line. */
    private fun findViolations(file: ASTNode): Map<ASTNode, List<Int>> {
        val violations = mutableMapOf<ASTNode, MutableList<Int>>()
        var lineStart = 0
        var inFencedBlock = false
        for (line in file.text.split('\n')) {
            val lineEnd = lineStart + line.length
            val leaves = file.leavesOnLine(lineStart, lineEnd)
            val isTooLong = line.length > maxLineLength && "://" !in line
            val isViolation =
                when (leaves.lineContent(lineStart, lineEnd)) {
                    LineContent.BLANK -> false

                    LineContent.COMMENT -> {
                        val commentText = line.commentText()
                        if (FENCE.containsMatchIn(commentText)) {
                            inFencedBlock = !inFencedBlock
                            false
                        } else {
                            isTooLong && !inFencedBlock && !commentText.startsWith("$ ")
                        }
                    }

                    LineContent.STRING -> {
                        inFencedBlock = false
                        isTooLong
                    }

                    LineContent.OTHER -> {
                        inFencedBlock = false
                        false
                    }
                }
            if (isViolation) {
                violations.getOrPut(leaves.last()) { mutableListOf() }
                    .add(lineStart + maxLineLength)
            }
            lineStart = lineEnd + 1
        }
        return violations
    }

    /**
     * The leaves of this file with non-whitespace text from [lineStart] (inclusive) to [lineEnd]
     * (exclusive).
     */
    private fun ASTNode.leavesOnLine(lineStart: Int, lineEnd: Int): List<ASTNode> {
        val leaves = mutableListOf<ASTNode>()
        var leaf = if (lineStart < lineEnd) findLeafElementAt(lineStart) else null
        while (leaf != null && leaf.startOffset < lineEnd) {
            val from = maxOf(lineStart, leaf.startOffset) - leaf.startOffset
            val to = minOf(lineEnd, leaf.startOffset + leaf.textLength) - leaf.startOffset
            if (leaf.text.substring(from, to).isNotBlank()) {
                leaves.add(leaf)
            }
            leaf = leaf.nextLeaf
        }
        return leaves
    }

    /** Classifies the line from [lineStart] to [lineEnd] made up of these leaves. */
    private fun List<ASTNode>.lineContent(lineStart: Int, lineEnd: Int): LineContent {
        val isComment = isNotEmpty() &&
            all { it.isPartOfComment20 } &&
            !last().isReportedByKtlint(lineStart)
        return when {
            isEmpty() -> LineContent.BLANK
            isComment -> LineContent.COMMENT
            isSingleStringTemplate(lineEnd) -> LineContent.STRING
            else -> LineContent.OTHER
        }
    }

    /**
     * Whether `standard:max-line-length` checks the line ending in this comment: it skips a line
     * that ends in KDoc, or in a comment that starts the line.
     */
    private fun ASTNode.isReportedByKtlint(lineStart: Int): Boolean {
        if (isPartOf(KDOC) || startOffset < lineStart) {
            return false
        }
        val previous = prevLeaf ?: return false
        return !previous.isWhiteSpaceWithNewline20
    }

    /**
     * Whether these leaves are one `"..."` string template that closes on this line, optionally
     * followed directly by a comma.
     */
    private fun List<ASTNode>.isSingleStringTemplate(lineEnd: Int): Boolean {
        val openQuote = first()
        if (openQuote.elementType != OPEN_QUOTE || openQuote.text != "\"") {
            return false
        }
        val template = openQuote.treeParent
        val closingQuote = template.lastChildNode
        val templateEnd = template.startOffset + template.textLength
        if (templateEnd > lineEnd) {
            return false
        }
        val trailing = filter { it.startOffset >= templateEnd }
        if (trailing.isEmpty()) {
            return true
        }
        val comma = trailing.singleOrNull() ?: return false
        return comma.elementType == COMMA && comma.prevLeaf == closingQuote
    }

    /** The text of a comment line without comment delimiters and surrounding whitespace. */
    private fun String.commentText(): String {
        val text = trim().removeSuffix("*/")
        val prefix = listOf("/**", "/*", "//", "*").firstOrNull { text.startsWith(it) }.orEmpty()
        return text.removePrefix(prefix).trim()
    }
}
