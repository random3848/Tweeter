package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.FILE
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/** The character a UTF-8 decoder substitutes for bytes that are not valid UTF-8. */
private const val REPLACEMENT_CHARACTER = '\ufffd'

/**
 * Source files are encoded in UTF-8.
 *
 * The ktlint CLI reads a file with `File.readText()`, which decodes it as UTF-8 and replaces each
 * malformed byte sequence with U+FFFD, so this rule reports the first U+FFFD in the file. A U+FFFD
 * that is really in the file is reported as well; write it as the escape `\ufffd` instead.
 */
internal class SourceEncodingRule : AndroidStyleRule("source-encoding") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != FILE) {
            return
        }
        stopTraversalOfAST()
        val offset = node.text.indexOf(REPLACEMENT_CHARACTER)
        if (offset >= 0) {
            emit(
                offset,
                "Source files are UTF-8; this file has bytes that are not valid UTF-8",
                false
            )
        }
    }
}
