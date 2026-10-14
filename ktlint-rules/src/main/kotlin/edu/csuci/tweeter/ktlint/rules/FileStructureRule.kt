package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.EOL_COMMENT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FILE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FILE_ANNOTATION_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IMPORT_DIRECTIVE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IMPORT_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PACKAGE_DIRECTIVE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SEMICOLON
import com.pinterest.ktlint.rule.engine.core.api.findParentByType
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isLeaf20
import com.pinterest.ktlint.rule.engine.core.api.isPartOf
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.recursiveChildren20
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import edu.csuci.tweeter.ktlint.isInScript
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/** The sections of a source file, in the order they appear. */
private enum class Section(val description: String) {
    HEADER("the copyright/license header"),
    ANNOTATIONS("the file annotations"),
    PACKAGE("the package statement"),
    IMPORTS("the imports"),
    DECLARATIONS("the top-level declarations")
}

/** Element types that may hold a comment that is not attached to a declaration. */
private val HEADER_PARENT_TYPES = setOf(FILE, PACKAGE_DIRECTIVE, IMPORT_LIST, FILE_ANNOTATION_LIST)

/**
 * A source file consists of, in order: an optional copyright/license header, file annotations, the
 * package statement, import statements, and top-level declarations. The header is a `/* */` block
 * comment, exactly one blank line separates adjacent sections, and neither the package statement
 * nor an import statement is line-wrapped.
 *
 * KDoc headers are left to `standard:kdoc`, extra blank lines to
 * `standard:no-consecutive-blank-lines`, and the gap after file annotations to
 * `standard:annotation`. A comment directly above the first declaration of a file without package
 * statement or imports belongs to that declaration, so it is not taken as a header. Scripts are
 * skipped.
 */
internal class FileStructureRule : AndroidStyleRule("file-structure") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != FILE) {
            return
        }
        stopTraversalOfAST()
        if (node.isInScript) {
            return
        }
        val leaves = node.recursiveChildren20.filter { it.isLeaf20 && it.textLength > 0 }.toList()
        val firstCodeIndex = leaves.indexOfFirst { it.isSectionCode() }
        val leavesBeforeCode = if (firstCodeIndex < 0) leaves else leaves.subList(0, firstCodeIndex)
        val header = leavesBeforeCode.filter { it.isHeaderComment() }
        header
            .firstOrNull { it.elementType == EOL_COMMENT }
            ?.let {
                emit(
                    it.startOffset,
                    "A copyright or license header is a /* */ block comment, not // comments",
                    false
                )
            }

        val firstLeaves = linkedMapOf<Section, ASTNode>()
        val lastLeaves = mutableMapOf<Section, ASTNode>()
        header.lastOrNull()?.let { lastLeaves[Section.HEADER] = it }
        for (leaf in leaves.filter { it.isSectionCode() }) {
            val section = leaf.section()
            firstLeaves.putIfAbsent(section, leaf)
            lastLeaves[section] = leaf
        }
        val adjacentSections = mutableListOf<Pair<Section, Section>>()
        if (header.isNotEmpty()) {
            firstLeaves.keys.firstOrNull()?.let { adjacentSections += Section.HEADER to it }
        }
        if (Section.PACKAGE in firstLeaves) {
            listOf(Section.IMPORTS, Section.DECLARATIONS)
                .firstOrNull { it in firstLeaves }
                ?.let { adjacentSections += Section.PACKAGE to it }
        }
        if (Section.IMPORTS in firstLeaves && Section.DECLARATIONS in firstLeaves) {
            adjacentSections += Section.IMPORTS to Section.DECLARATIONS
        }
        for ((before, after) in adjacentSections) {
            checkBlankLine(
                leaves,
                lastLeaves.getValue(before),
                firstLeaves.getValue(after),
                "Exactly one blank line separates ${before.description} from ${after.description}",
                emit
            )
        }

        node
            .findChildByType(PACKAGE_DIRECTIVE)
            ?.let { checkNotWrapped(it, "The package statement is never line-wrapped", emit) }
        node
            .findChildByType(IMPORT_LIST)
            ?.getChildren(null)
            ?.filter { it.elementType == IMPORT_DIRECTIVE }
            ?.forEach { checkNotWrapped(it, "Import statements are never line-wrapped", emit) }
    }

    /** Reports a missing blank line between the leaves [end] and [start] of adjacent sections. */
    private fun checkBlankLine(
        leaves: List<ASTNode>,
        end: ASTNode,
        start: ASTNode,
        message: String,
        emit: Emit
    ) {
        val gap = leaves.subList(leaves.indexOf(end) + 1, leaves.indexOf(start))
        if (gap.any { it.isWhiteSpace20 && it.text.count { char -> char == '\n' } >= 2 }) {
            return
        }
        val lineBreak = gap.firstOrNull { it.isWhiteSpace20 && it.textContains('\n') }
        if (lineBreak == null) {
            emit(start.startOffset, message, false)
            return
        }
        emit(lineBreak.startOffset + lineBreak.textLength, message, true)
            .ifAutocorrectAllowed { lineBreak.replaceTextWith("\n\n") }
    }

    /** Reports [directive] when the code it consists of spans more than one line. */
    private fun checkNotWrapped(directive: ASTNode, message: String, emit: Emit) {
        val codeLeaves =
            directive
                .recursiveChildren20
                .filter { it.isLeaf20 && it.textLength > 0 && it.isCode }
                .toList()
        val first = codeLeaves.firstOrNull() ?: return
        val last = codeLeaves.last()
        val text =
            directive.text.substring(
                first.startOffset - directive.startOffset,
                last.startOffset + last.textLength - directive.startOffset
            )
        if ('\n' in text) {
            emit(first.startOffset, message, false)
        }
    }

    /** Whether this leaf is code that belongs to one of the sections after the header. */
    private fun ASTNode.isSectionCode(): Boolean = isCode && elementType != SEMICOLON

    private fun ASTNode.section(): Section = when {
        isPartOf(FILE_ANNOTATION_LIST) -> Section.ANNOTATIONS
        isPartOf(PACKAGE_DIRECTIVE) -> Section.PACKAGE
        isPartOf(IMPORT_LIST) -> Section.IMPORTS
        else -> Section.DECLARATIONS
    }

    /** Whether this leaf is part of a comment that is not attached to a declaration. */
    private fun ASTNode.isHeaderComment(): Boolean {
        if (!isPartOfComment20) {
            return false
        }
        val comment = findParentByType(KDOC) ?: this
        return comment.treeParent?.elementType in HEADER_PARENT_TYPES
    }
}
