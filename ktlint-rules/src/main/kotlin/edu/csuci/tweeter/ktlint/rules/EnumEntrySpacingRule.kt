package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ENUM_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IDENTIFIER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isPartOf
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.lastChildLeafOrSelf20
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevCodeSibling20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Enum entries on separate lines need no blank line between them, except next to an entry that
 * defines a body.
 *
 * Two adjacent entries on separate lines where either one has a body need a blank line between
 * them; comments between the entries may sit on either side of it. Entries on the same line, as
 * in `enum class Answer { YES, NO, MAYBE }`, are not checked.
 */
internal class EnumEntrySpacingRule : AndroidStyleRule("enum-entry-spacing") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ENUM_ENTRY) {
            return
        }
        val previous = node.prevCodeSibling20?.takeIf { it.elementType == ENUM_ENTRY } ?: return
        if (!previous.hasBody() && !node.hasBody()) {
            return
        }
        val gap = generateSequence(previous.lastChildLeafOrSelf20.nextLeaf) { it.nextLeaf }
            .takeWhile { it.isWhiteSpace20 || (it.isPartOfComment20 && !it.isPartOf(KDOC)) }
            .toList()
        val newlines = gap
            .filter { it.isWhiteSpace20 }
            .map { whiteSpace -> whiteSpace.text.count { it == '\n' } }
        if (newlines.none { it > 0 } || newlines.any { it >= 2 }) {
            return
        }
        val entryStart = (gap.lastOrNull() ?: previous.lastChildLeafOrSelf20).nextLeaf ?: return
        // The blank line goes above the comments that lead the entry on their own lines.
        val target = gap.firstOrNull { it.isOwnLineComment() } ?: entryStart
        val name = node.findChildByType(IDENTIFIER)?.text
        emit(target.startOffset, "A blank line separates enum entries with a body: '$name'", true)
            .ifAutocorrectAllowed {
                val whiteSpace = target.prevLeaf?.takeIf { it.isWhiteSpaceWithNewline20 }
                target.upsertWhitespaceBeforeMe("\n" + whiteSpace?.text.orEmpty())
            }
    }

    private fun ASTNode.hasBody(): Boolean = findChildByType(CLASS_BODY) != null

    private fun ASTNode.isOwnLineComment(): Boolean =
        isPartOfComment20 && prevLeaf.isWhiteSpaceWithNewline20
}
