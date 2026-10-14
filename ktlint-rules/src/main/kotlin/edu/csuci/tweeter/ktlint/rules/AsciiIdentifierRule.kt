package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ENUM_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OBJECT_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPEALIAS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPE_PARAMETER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor

/**
 * Identifiers use only ASCII letters and digits, and in the cases noted by the guide,
 * underscores; function names don't contain spaces.
 *
 * Checks every declared name: classes, objects, interfaces, type aliases, functions, properties
 * (including locals), value parameters (including lambda, catch, for-loop and setter parameters),
 * destructuring entries, type parameters and enum entries. Overrides (and the parameters of
 * overriding functions and property setters) are skipped because their names come from a
 * supertype, as is the unused-name placeholder `_`. Backticked test names, which ktlint allows,
 * are reported too: the guide forbids spaces in function names everywhere.
 */
internal class AsciiIdentifierRule : AndroidStyleRule("ascii-identifiers") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType !in NAMED_DECLARATIONS) {
            return
        }
        val declaration = node.psi as KtNamedDeclaration
        val identifier = declaration.nameIdentifier ?: return
        val name = identifier.text.removeSurrounding("`")
        if (name == "_" || declaration.isOverride() || ASCII_IDENTIFIER.matches(name)) {
            return
        }
        val message = if (node.elementType == FUN && ' ' in name) {
            "Function names don't contain spaces: '$name'"
        } else {
            "Identifiers use only ASCII letters, digits and underscores: '$name'"
        }
        emit(identifier.node.startOffset, message, false)
    }

    private fun KtNamedDeclaration.isOverride(): Boolean {
        if (hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return true
        }
        val owner = (this as? KtParameter)?.ownerFunction
        val ownerDeclaration = if (owner is KtPropertyAccessor) owner.property else owner
        return ownerDeclaration?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true
    }

    private companion object {
        val NAMED_DECLARATIONS = setOf(
            CLASS,
            OBJECT_DECLARATION,
            TYPEALIAS,
            FUN,
            PROPERTY,
            VALUE_PARAMETER,
            DESTRUCTURING_DECLARATION_ENTRY,
            TYPE_PARAMETER,
            ENUM_ENTRY
        )
        val ASCII_IDENTIFIER = Regex("[A-Za-z0-9_]+")
    }
}
