package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor

/**
 * Parameter names are written in camel case (`newCustomerId`).
 *
 * Covers function, constructor, lambda, setter, catch and for-loop parameters as well as
 * destructuring entries. Primary-constructor `val`/`var` parameters are included because
 * `standard:property-naming` does not check them; a private one named `_` followed by a camelCase
 * name is a backing property and is allowed. Overrides (and the parameters of overriding functions
 * and property setters) are skipped because their names come from a supertype, as is the
 * unused-name placeholder `_`.
 */
internal class ParameterNamingRule : AndroidStyleRule("parameter-naming") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType !in PARAMETERS) {
            return
        }
        val declaration = node.psi as KtNamedDeclaration
        val identifier = declaration.nameIdentifier ?: return
        val name = identifier.text.removeSurrounding("`")
        if (name == "_" || CAMEL_CASE.matches(name)) {
            return
        }
        if (declaration.isBackingProperty(name) || declaration.isOverride()) {
            return
        }
        emit(identifier.node.startOffset, "Parameter names are camelCase: '$name'", false)
    }

    private fun KtNamedDeclaration.isBackingProperty(name: String): Boolean {
        if (!BACKING_PROPERTY.matches(name)) {
            return false
        }
        return this is KtParameter && hasValOrVar() && hasModifier(KtTokens.PRIVATE_KEYWORD)
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
        val PARAMETERS = setOf(VALUE_PARAMETER, DESTRUCTURING_DECLARATION_ENTRY)
        val CAMEL_CASE = Regex("[a-z][A-Za-z0-9]*")
        val BACKING_PROPERTY = Regex("_[a-z][A-Za-z0-9]*")
    }
}
