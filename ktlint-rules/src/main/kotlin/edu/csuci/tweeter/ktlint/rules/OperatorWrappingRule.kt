package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.BINARY_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BINARY_WITH_TYPE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.EQ
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IDENTIFIER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IS_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SAFE_ACCESS
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAfterRule
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAfterRule.Mode
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithoutNewline20
import com.pinterest.ktlint.rule.engine.core.api.lastChildLeafOrSelf20
import com.pinterest.ktlint.rule.engine.core.api.nextCodeLeaf
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.nextSibling20
import com.pinterest.ktlint.rule.engine.core.api.prevCodeSibling20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.remove
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet

/**
 * When a line is broken at an operator, the break comes after the operator; only `.`, `?.` and
 * `::` start the next line.
 *
 * Checks binary operators (including `?:`, `&&`, `||`, ranges, `in`, comparisons and compound
 * assignments), infix function calls, `as`/`as?` and `is`/`!is`, which must end a line rather than
 * start one, and `?.`, which must start a line rather than end one. Plain `=` is left to
 * `standard:no-line-break-before-assignment`, and `.` and `::` to `standard:dot-spacing` and
 * `standard:double-colon-spacing`. Runs after `standard:binary-expression-wrapping` so it fixes
 * the operators that rule wraps.
 */
internal class OperatorWrappingRule :
    AndroidStyleRule(
        "operator-wrapping",
        visitorModifiers = setOf(
            RunAfterRule(
                RuleId("standard:binary-expression-wrapping"),
                Mode.REGARDLESS_WHETHER_RUN_AFTER_RULE_IS_LOADED_OR_DISABLED
            )
        )
    ) {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        when (node.elementType) {
            OPERATION_REFERENCE -> visitOperator(node, emit)
            SAFE_ACCESS -> visitSafeAccess(node, emit)
        }
    }

    private fun visitOperator(operator: ASTNode, emit: Emit) {
        if (!OPERATOR_PARENTS.contains(operator.treeParent?.elementType)) {
            return
        }
        val operatorToken = operator.firstChildNode ?: return
        if (operatorToken.elementType == EQ) {
            return
        }
        val prevLeaf = operator.prevLeaf?.takeIf { it.isWhiteSpaceWithNewline20 } ?: return
        val kind = if (operatorToken.elementType == IDENTIFIER) "infix function" else "operator"
        emit(
            operator.startOffset,
            "Line break goes after the $kind '${operator.text}', not before it",
            true
        ).ifAutocorrectAllowed {
            moveToEndOfPreviousLine(operator, prevLeaf)
        }
    }

    /**
     * Rewrites `<left><"\n"><operator><" "><right>` to `<left><" "><operator><"\n"><right>`, as
     * `standard:chain-wrapping` does for operators that must not begin a line.
     */
    private fun moveToEndOfPreviousLine(operator: ASTNode, prevLeaf: ASTNode) {
        val insertBeforeSibling = operator.prevCodeSibling20?.nextSibling20 ?: return
        val nextLeaf = operator.lastChildLeafOrSelf20.nextLeaf
        val whiteSpaceToBeDeleted = when {
            // Prefer to remove the whitespace before the operator as it holds the indent of the
            // next line.
            nextLeaf.isWhiteSpaceWithNewline20 -> prevLeaf

            nextLeaf.isWhiteSpaceWithoutNewline20 -> nextLeaf

            else -> null
        }
        operator.remove()
        insertBeforeSibling.treeParent.addChild(operator, insertBeforeSibling)
        operator.upsertWhitespaceBeforeMe(" ")
        whiteSpaceToBeDeleted?.remove()
    }

    private fun visitSafeAccess(safeAccess: ASTNode, emit: Emit) {
        val nextLeaf = safeAccess.nextCodeLeaf?.prevLeaf ?: return
        if (!nextLeaf.isWhiteSpaceWithNewline20) {
            return
        }
        emit(safeAccess.startOffset, "Line break goes before '?.', not after it", true)
            .ifAutocorrectAllowed {
                // Rewrites `<left><"?."><comment?><"\n"><right>` to
                // `<left><comment?><"\n"><"?."><right>`.
                safeAccess.remove()
                (nextLeaf as LeafElement).rawInsertAfterMe(safeAccess as LeafElement)
            }
    }

    private companion object {
        val OPERATOR_PARENTS: TokenSet =
            TokenSet.create(BINARY_EXPRESSION, BINARY_WITH_TYPE, IS_EXPRESSION)
    }
}
