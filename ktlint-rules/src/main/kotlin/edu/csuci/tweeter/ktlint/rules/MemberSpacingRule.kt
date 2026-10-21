package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_INITIALIZER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ENUM_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OBJECT_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SECONDARY_CONSTRUCTOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SEMICOLON
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPEALIAS
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isPartOf
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.lastChildLeafOrSelf20
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet

/**
 * A single blank line appears between consecutive members of a class.
 *
 * Applies to properties, functions, constructors, `init` blocks, nested classes and objects
 * (including companion objects) and type aliases in class, interface and object bodies. The blank
 * line is optional between two consecutive properties, and comments between members may sit on
 * either side of it. Enum entries and top-level declarations are not checked.
 */
internal class MemberSpacingRule : AndroidStyleRule("member-spacing") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (!MEMBERS.contains(node.elementType) || node.treeParent?.elementType != CLASS_BODY) {
            return
        }
        // Checked while visiting the later member, so that suppressing it suppresses this rule.
        val previous =
            generateSequence(node.treePrev) { it.treePrev }
                .firstOrNull { MEMBERS.contains(it.elementType) || it.elementType == ENUM_ENTRY }
        if (previous == null || previous.elementType == ENUM_ENTRY) {
            return
        }
        checkSpacing(previous, node, emit)
    }

    private fun checkSpacing(previous: ASTNode, member: ASTNode, emit: Emit) {
        if (previous.elementType == PROPERTY && member.elementType == PROPERTY) {
            return
        }
        val gap = previous.lastChildLeafOrSelf20.leavesUntilDeclarationStart()
        if (gap.any { it.isWhiteSpace20 && it.text.count { char -> char == '\n' } >= 2 }) {
            return
        }
        val memberStart = (gap.lastOrNull() ?: previous.lastChildLeafOrSelf20).nextLeaf ?: return
        // The blank line goes above the comments that lead the member on their own lines.
        val target = gap.firstOrNull { it.isOwnLineComment() } ?: memberStart
        emit(target.startOffset, "A blank line separates consecutive class members", true)
            .ifAutocorrectAllowed {
                val whiteSpace = target.prevLeaf?.takeIf { it.isWhiteSpaceWithNewline20 }
                target.upsertWhitespaceBeforeMe("\n" + (whiteSpace?.text ?: previous.indent20))
            }
    }

    /**
     * The whitespace, comments and semicolons after this leaf, up to the KDoc or first code leaf
     * of the next declaration.
     */
    private fun ASTNode.leavesUntilDeclarationStart(): List<ASTNode> =
        generateSequence(nextLeaf) { it.nextLeaf }
            .takeWhile {
                it.isWhiteSpace20 ||
                    it.elementType == SEMICOLON ||
                    (it.isPartOfComment20 && !it.isPartOf(KDOC))
            }.toList()

    private fun ASTNode.isOwnLineComment(): Boolean =
        isPartOfComment20 && prevLeaf.isWhiteSpaceWithNewline20

    private companion object {
        val MEMBERS: TokenSet = TokenSet.create(
            PROPERTY,
            FUN,
            SECONDARY_CONSTRUCTOR,
            CLASS_INITIALIZER,
            CLASS,
            OBJECT_DECLARATION,
            TYPEALIAS
        )
    }
}
