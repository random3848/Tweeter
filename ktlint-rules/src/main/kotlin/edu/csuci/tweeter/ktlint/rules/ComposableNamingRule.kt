package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Functions annotated with `@Composable` that return Unit are PascalCase nouns; those that
 * return a value are camelCase.
 *
 * Overrides and operator functions are skipped because their name is fixed by the overridden
 * declaration or by the language. Functions with an expression body and no declared return type
 * are skipped because their return type is unknown without type resolution.
 */
internal class ComposableNamingRule : AndroidStyleRule("composable-naming") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != FUN) {
            return
        }
        val function = node.psi as KtNamedFunction
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
            function.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
            function.annotationEntries.none { it.shortName?.asString() == "Composable" }
        ) {
            return
        }
        val nameIdentifier = function.nameIdentifier ?: return
        val name = nameIdentifier.text
        val returnType = function.typeReference?.text
        val returnsUnit = when {
            returnType != null -> returnType == "Unit" || returnType == "kotlin.Unit"
            function.bodyExpression == null || function.hasBlockBody() -> true
            else -> return
        }
        if (returnsUnit && !name.matches(PASCAL_CASE)) {
            emit(
                nameIdentifier.node.startOffset,
                "@Composable functions that return Unit are PascalCase nouns: '$name'",
                false
            )
        } else if (!returnsUnit && !name.matches(CAMEL_CASE)) {
            emit(
                nameIdentifier.node.startOffset,
                "@Composable functions that return a value are camelCase: '$name'",
                false
            )
        }
    }

    private companion object {
        val PASCAL_CASE = Regex("[A-Z][A-Za-z0-9]*")
        val CAMEL_CASE = Regex("[a-z][A-Za-z0-9]*")
    }
}
