package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPE_PARAMETER
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtTypeParameter

/**
 * Type variables are a single capital letter, optionally followed by a single numeral (`E`, `T`,
 * `X`, `T2`), or a name in the form used for classes followed by the capital letter T
 * (`RequestT`, `FooBarT`).
 *
 * Applies to type parameters of classes, interfaces, functions, properties and type aliases. Type
 * parameters cannot be overridden, so there is nothing to exempt.
 */
internal class TypeParameterNamingRule : AndroidStyleRule("type-parameter-naming") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != TYPE_PARAMETER) {
            return
        }
        val identifier = (node.psi as KtTypeParameter).nameIdentifier ?: return
        val name = identifier.text.removeSurrounding("`")
        if (TYPE_VARIABLE.matches(name)) {
            return
        }
        emit(
            identifier.node.startOffset,
            "Type parameters are a capital letter with an optional numeral (T, T2) or a " +
                "class-style name followed by T (RequestT): '$name'",
            false
        )
    }

    private companion object {
        val TYPE_VARIABLE = Regex("[A-Z][0-9]?|[A-Z][A-Za-z0-9]*[a-z0-9]T")
    }
}
