package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CHARACTER_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LITERAL_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.STRING_TEMPLATE
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isLeaf20
import com.pinterest.ktlint.rule.engine.core.api.isPartOfString20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Apart from line terminators, the ASCII space is the only whitespace character in a source file,
 * in code, strings, and comments alike.
 *
 * Tabs in the leading indentation of a line outside string literals are left to
 * `standard:indent`. In a `"..."` string or a character literal the character is replaced with its
 * escape (`\t`, otherwise `\uXXXX`); in raw strings and comments it is only reported.
 */
internal class WhitespaceCharactersRule : AndroidStyleRule("whitespace-characters") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (!node.isLeaf20 || node.textLength == 0) {
            return
        }
        val text = node.text
        val canEscape = node.isEscapableLiteral()
        val isInString = node.isPartOfString20
        val escaped = mutableSetOf<Int>()
        for ((index, char) in text.withIndex()) {
            if (!char.isForbiddenWhitespace()) {
                continue
            }
            if (char == '\t' && !isInString && node.isInLeadingIndentation(index)) {
                continue
            }
            emit(
                node.startOffset + index,
                "ASCII space is the only whitespace character allowed: found U+" +
                    "%04X".format(char.code),
                canEscape
            ).ifAutocorrectAllowed {
                if (canEscape) {
                    escaped.add(index)
                }
            }
        }
        if (escaped.isNotEmpty()) {
            node.replaceTextWith(
                buildString {
                    for ((index, char) in text.withIndex()) {
                        append(if (index in escaped) char.escape() else char.toString())
                    }
                }
            )
        }
    }

    /** Whether this leaf is the text of a `"..."` string or a character literal. */
    private fun ASTNode.isEscapableLiteral(): Boolean {
        if (elementType == CHARACTER_LITERAL) {
            return true
        }
        val entry = treeParent?.takeIf { it.elementType == LITERAL_STRING_TEMPLATE_ENTRY }
        val template = entry?.treeParent?.takeIf { it.elementType == STRING_TEMPLATE }
        return template?.firstChildNode?.text == "\""
    }

    /** Whether only spaces and tabs precede the character at [index] on its line. */
    private fun ASTNode.isInLeadingIndentation(index: Int): Boolean {
        var leaf: ASTNode? = this
        var end = index
        while (leaf != null) {
            val text = leaf.text
            for (i in end - 1 downTo 0) {
                val char = text[i]
                if (char == '\n' || char == '\r') {
                    return true
                }
                if (char != ' ' && char != '\t') {
                    return false
                }
            }
            leaf = leaf.prevLeaf
            end = leaf?.textLength ?: 0
        }
        return true
    }

    private fun Char.isForbiddenWhitespace(): Boolean =
        (Character.isWhitespace(this) || Character.isSpaceChar(this)) &&
            this != ' ' &&
            this != '\n' &&
            this != '\r'

    private fun Char.escape(): String = if (this == '\t') "\\t" else "\\u%04x".format(code)
}
