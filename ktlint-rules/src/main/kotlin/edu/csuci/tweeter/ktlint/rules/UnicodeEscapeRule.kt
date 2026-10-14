package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CHARACTER_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ESCAPE_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LITERAL_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SHORT_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/** A Unicode escape such as `\u000a`. */
private val UNICODE_ESCAPE = Regex("""\\u([0-9A-Fa-f]{4})""")

/** Characters that have a special escape sequence, mapped to that sequence. */
private val SPECIAL_ESCAPES = mapOf(
    '\b' to "\\b",
    '\t' to "\\t",
    '\n' to "\\n",
    '\r' to "\\r",
    '\'' to "\\'",
    '"' to "\\\"",
    '\\' to "\\\\",
    '$' to "\\$"
)

/**
 * Types of characters that are not printable, so a Unicode escape is the clearest spelling. The
 * ASCII space is the one space separator that is printable.
 */
private val NON_PRINTABLE_TYPES = setOf(
    Character.CONTROL,
    Character.FORMAT,
    Character.SURROGATE,
    Character.PRIVATE_USE,
    Character.UNASSIGNED,
    Character.SPACE_SEPARATOR,
    Character.LINE_SEPARATOR,
    Character.PARAGRAPH_SEPARATOR,
    Character.NON_SPACING_MARK,
    Character.ENCLOSING_MARK,
    Character.COMBINING_SPACING_MARK
).map { it.toInt() }.toSet()

/** The replacement character, which in a source file is indistinguishable from a decoding error. */
private const val REPLACEMENT_CHARACTER = '\ufffd'

/**
 * A character with a special escape sequence (`\b`, `\t`, `\n`, `\r`, `\'`, `\"`, `\\`, `\$`) is
 * written with that sequence, and a printable character (including the ASCII space `\u0020`) is
 * written as itself, not as a Unicode escape.
 *
 * Escapes of non-printable characters (controls, format characters such as `\ufeff`, surrogates,
 * private-use and unassigned code points, whitespace other than the ASCII space, and combining
 * marks) are kept. `\ufffd` is kept too, because `source-encoding` reports a literal U+FFFD as
 * undecodable bytes.
 *
 * Writing the character as itself is reported without autocorrect when it would change how the
 * string template is parsed: an identifier character directly after a short template such as
 * `$name` would extend the template's identifier, and an identifier start, `` ` `` or `{`
 * directly after a literal `$` would turn the `$` into the start of a template.
 */
internal class UnicodeEscapeRule : AndroidStyleRule("unicode-escape") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        when (node.elementType) {
            ESCAPE_STRING_TEMPLATE_ENTRY -> {
                node.firstChildNode?.let { visitEscapes(it, inCharacterLiteral = false, emit) }
            }

            CHARACTER_LITERAL -> {
                visitEscapes(node, inCharacterLiteral = true, emit)
            }
        }
    }

    private fun visitEscapes(leaf: ASTNode, inCharacterLiteral: Boolean, emit: Emit) {
        val text = leaf.text
        val replacements = mutableMapOf<IntRange, String>()
        for (match in UNICODE_ESCAPE.findAll(text)) {
            val escape = match.value
            val char = Char(match.groupValues[1].toInt(16))
            val offset = leaf.startOffset + match.range.first
            val special = SPECIAL_ESCAPES[char]
            if (special != null) {
                emit(
                    offset,
                    "Use the special escape sequence '$special' instead of '$escape'",
                    true
                ).ifAutocorrectAllowed { replacements[match.range] = special }
            } else if (char.isPrintable()) {
                val canAutoCorrect = inCharacterLiteral || !leaf.wouldJoinTemplate(char)
                emit(
                    offset,
                    "Use the character '$char' instead of its Unicode escape '$escape'",
                    canAutoCorrect
                ).ifAutocorrectAllowed {
                    if (canAutoCorrect) {
                        replacements[match.range] = char.toString()
                    }
                }
            }
        }
        if (replacements.isNotEmpty()) {
            var newText = text
            for ((range, replacement) in replacements.entries.sortedByDescending { it.key.first }) {
                newText = newText.replaceRange(range, replacement)
            }
            leaf.replaceTextWith(newText)
        }
    }

    private fun Char.isPrintable(): Boolean {
        if (this == ' ') return true
        return this != REPLACEMENT_CHARACTER && Character.getType(this) !in NON_PRINTABLE_TYPES
    }

    /**
     * Whether writing [char] as itself in place of this escape would join it to a string template:
     * either extending the identifier of a preceding short template (`$name`), or turning a
     * preceding literal `$` into the start of a template.
     */
    private fun ASTNode.wouldJoinTemplate(char: Char): Boolean {
        val previous = treeParent?.treePrev ?: return false
        return when (previous.elementType) {
            SHORT_STRING_TEMPLATE_ENTRY -> {
                !previous.text.endsWith('`') && char.mayContinueIdentifier()
            }

            LITERAL_STRING_TEMPLATE_ENTRY -> {
                previous.text.endsWith('$') && (char.mayStartIdentifier() || char == '{')
            }

            else -> {
                false
            }
        }
    }

    /**
     * Whether the lexer may read this character as part of an identifier. Deliberately broad: a
     * false positive only costs the autocorrect.
     */
    private fun Char.mayContinueIdentifier(): Boolean =
        Character.isJavaIdentifierPart(this) || isLetterOrDigit()

    /** Whether the lexer may read this character as the start of a (possibly backticked) name. */
    private fun Char.mayStartIdentifier(): Boolean =
        Character.isJavaIdentifierStart(this) || isLetter() || this == '`'
}
