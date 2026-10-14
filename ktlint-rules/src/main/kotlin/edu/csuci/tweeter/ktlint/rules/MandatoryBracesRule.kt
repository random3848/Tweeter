package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.ARROW
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DO_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DO_WHILE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RPAR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SEMICOLON
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHEN_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHILE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHILE_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.IndentConfig
import com.pinterest.ktlint.rule.engine.core.api.KtlintKotlinCompiler
import com.pinterest.ktlint.rule.engine.core.api.children20
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithoutNewline20
import com.pinterest.ktlint.rule.engine.core.api.nextSibling
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceAfterMe
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.leaves
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Braces enclose the body of every loop and every `when` branch that does not fit on one line.
 *
 * Covers `for`, `while` and `do`-`while` bodies, even single-line and empty ones, and `when`
 * branches (including `else ->`) whose condition and expression span more than one line. An
 * empty body such as `for (x in xs);` or `do while (x > 0)` becomes an empty K&R block.
 * Single-line `when` branches such as `0 -> return` and trailing comments are not affected;
 * `if`/`else` is left to `standard:multiline-if-else`.
 */
internal class MandatoryBracesRule :
    AndroidStyleRule(
        "mandatory-braces",
        setOf(INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY)
    ) {
    private var indentConfig = IndentConfig.DEFAULT_INDENT_CONFIG

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        indentConfig = IndentConfig(
            indentStyle = editorConfig[INDENT_STYLE_PROPERTY],
            tabWidth = editorConfig[INDENT_SIZE_PROPERTY]
        )
    }

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        when (node.elementType) {
            BODY -> visitLoopBody(node, emit)
            DO_WHILE -> visitDoWhileWithoutBody(node, emit)
            WHEN_ENTRY -> visitWhenEntry(node, emit)
        }
    }

    private fun visitLoopBody(body: ASTNode, emit: Emit) {
        val loop = when (body.treeParent?.elementType) {
            FOR -> "for"
            WHILE -> "while"
            DO_WHILE -> "do-while"
            else -> return
        }
        val statement = body.children20.firstOrNull { it.isCode }
        if (statement == null) {
            visitEmptyLoopBody(body, loop, emit)
            return
        }
        if (statement.elementType == BLOCK) {
            return
        }
        emit(statement.startOffset, loopMessage(loop), true)
            .ifAutocorrectAllowed {
                val isDoWhile = body.treeParent.elementType == DO_WHILE
                wrapInBlock(body, statement, if (isDoWhile) DO_KEYWORD else RPAR)
                if (isDoWhile) {
                    // Keep `while (...)` on the line of the closing brace.
                    body
                        .nextSibling { !it.isPartOfComment20 }
                        ?.upsertWhitespaceBeforeMe(" ")
                }
            }
    }

    /**
     * Reports the empty [body] of `for (x in xs);` or `while (x > 0);`, and replaces it and the
     * semicolon ending the loop with an empty K&R block.
     */
    private fun visitEmptyLoopBody(body: ASTNode, loop: String, emit: Emit) {
        val loopNode = body.treeParent
        val canBeAutoCorrected = loopNode
            .children20
            .dropWhile { it.elementType != RPAR }
            .drop(1)
            .takeWhile { it != body }
            .all { it.isWhiteSpace20 }
        emit(body.startOffset, loopMessage(loop), canBeAutoCorrected)
            .ifAutocorrectAllowed {
                val newBody = createEmptyBody(loopNode.indent20) ?: return@ifAutocorrectAllowed
                loopNode.replaceChild(body, newBody)
                newBody.upsertWhitespaceBeforeMe(" ")
                removeSemicolonAfter(loopNode)
            }
    }

    /** Reports and braces the missing body of `do while (x > 0)`. */
    private fun visitDoWhileWithoutBody(loop: ASTNode, emit: Emit) {
        if (loop.findChildByType(BODY) != null) {
            return
        }
        val doKeyword = loop.findChildByType(DO_KEYWORD) ?: return
        val whileKeyword = loop.findChildByType(WHILE_KEYWORD) ?: return
        val canBeAutoCorrected = doKeyword
            .siblings()
            .takeWhile { it != whileKeyword }
            .all { it.isWhiteSpace20 }
        emit(whileKeyword.startOffset, loopMessage("do-while"), canBeAutoCorrected)
            .ifAutocorrectAllowed {
                val newBody = createEmptyBody(loop.indent20) ?: return@ifAutocorrectAllowed
                loop.addChild(newBody, whileKeyword)
                newBody.upsertWhitespaceBeforeMe(" ")
                whileKeyword.upsertWhitespaceBeforeMe(" ")
            }
    }

    private fun loopMessage(loop: String) = "The body of a '$loop' loop is enclosed in braces"

    /** Creates a loop body holding an empty block whose closing brace follows [indent]. */
    private fun createEmptyBody(indent: String): ASTNode? = KtlintKotlinCompiler
        .createASTNodeFromText("do {$indent} while (true)")
        ?.findChildByType(DO_WHILE)
        ?.findChildByType(BODY)

    /**
     * Removes the semicolon that ended the empty-bodied [loop]; code that followed it on the same
     * line moves to the next line.
     */
    private fun removeSemicolonAfter(loop: ASTNode) {
        val semicolon = loop.treeNext?.takeIf { it.elementType == SEMICOLON } ?: return
        loop.treeParent.removeChild(semicolon)
        val next = loop.treeNext ?: return
        val isFollowedByCodeOnSameLine = if (next.isWhiteSpace20) {
            next.isWhiteSpaceWithoutNewline20 && next.treeNext?.isPartOfComment20 == false
        } else {
            !next.isPartOfComment20
        }
        if (isFollowedByCodeOnSameLine) {
            loop.upsertWhitespaceAfterMe(loop.indent20)
        }
    }

    private fun visitWhenEntry(entry: ASTNode, emit: Emit) {
        val expression = (entry.psi as KtWhenEntry).expression?.node ?: return
        if (expression.elementType == BLOCK) {
            return
        }
        val branchStart = entry.children20.firstOrNull { it.isCode } ?: return
        val branchEnd = expression.startOffset + expression.textLength
        val branchText = entry.text.substring(
            branchStart.startOffset - entry.startOffset,
            branchEnd - entry.startOffset
        )
        if (!branchText.contains('\n')) {
            return
        }
        emit(
            expression.startOffset,
            "A 'when' branch that does not fit on one line is enclosed in braces",
            true
        ).ifAutocorrectAllowed {
            wrapInBlock(entry, expression, ARROW)
        }
    }

    /**
     * Replaces [statement], a child of [container], with a block holding it, in the way
     * `standard:multiline-if-else` does: comments between [stopType] and the statement, and
     * trailing comments on its last line, move into the block.
     */
    private fun wrapInBlock(container: ASTNode, statement: ASTNode, stopType: IElementType) {
        val prevLeaves = statement
            .leaves(forward = false)
            .takeWhile { it.elementType != stopType }
            .toList()
            .reversed()
        val nextLeaves = statement
            .leaves(forward = true)
            .takeWhile { it.isWhiteSpaceWithoutNewline20 || it.isPartOfComment20 }
            .toList()
            .dropLastWhile { it.isWhiteSpaceWithoutNewline20 }

        prevLeaves
            .firstOrNull()
            .takeIf { it.isWhiteSpace20 }
            ?.replaceTextWith(" ")
        KtBlockExpression(null).apply {
            container.replaceChild(statement, this)
            addChild(LeafPsiElement(LBRACE, "{"))
            addChild(PsiWhiteSpaceImpl(indentConfig.childIndentOf(container)))
            prevLeaves
                .dropWhile { it.isWhiteSpace20 }
                .forEach(::addChild)
            addChild(statement)
            nextLeaves.forEach(::addChild)
            addChild(PsiWhiteSpaceImpl(container.indent20))
            addChild(LeafPsiElement(RBRACE, "}"))
        }
    }
}
