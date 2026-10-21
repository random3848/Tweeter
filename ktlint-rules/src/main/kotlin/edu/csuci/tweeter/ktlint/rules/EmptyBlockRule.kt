package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.children20
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithoutNewline20
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceAfterMe
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * An empty block is written K&R style, with the closing brace on the line after the opening one.
 *
 * Covers code blocks: function, accessor, constructor and `init` bodies and the bodies of
 * `if`/`else`, `when` branches, loops, `try`, `catch` and `finally`. Lambda bodies and blocks
 * holding a comment are not checked, and neither are class bodies.
 */
internal class EmptyBlockRule : AndroidStyleRule("empty-block") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != BLOCK || node.treeParent?.elementType == FUNCTION_LITERAL) {
            return
        }
        val lbrace = node.firstChildNode ?: return
        if (lbrace.elementType != LBRACE || node.lastChildNode.elementType != RBRACE) {
            return
        }
        val isEmptyOnOneLine = node.children20.all {
            it.elementType == LBRACE || it.elementType == RBRACE || it.isWhiteSpaceWithoutNewline20
        }
        if (!isEmptyOnOneLine) {
            return
        }
        emit(lbrace.startOffset, "An empty block is written K&R style: '${node.text}'", true)
            .ifAutocorrectAllowed {
                lbrace.upsertWhitespaceAfterMe(lbrace.indent20)
            }
    }
}
