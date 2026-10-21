package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.BOOLEAN_CONSTANT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CHARACTER_CONSTANT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FLOAT_CONSTANT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.INTEGER_CONSTANT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Constants (top-level or object `val`s holding deeply immutable data) use the `const`
 * modifier when they are scalars, and are named in UPPER_SNAKE_CASE.
 *
 * Only `val`s declared at the top level of a `.kt` file or in an object or companion object
 * are considered; `var`s, overrides, `lateinit`, delegated properties and properties with a
 * custom getter are never constants. Without type resolution, only literal scalars and
 * `listOf`/`setOf`/`mapOf`-style calls over literal scalars are recognized.
 *
 * A scalar `val` is only a constant when marked `const` (the guide lists
 * `val nonConstScalar = "non-const"` as a non-constant), so a missing `const` is only reported
 * when the name is already UPPER_SNAKE_CASE, such as `val NUMBER = 5`.
 */
internal class ConstantRule : AndroidStyleRule("constant") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != PROPERTY) {
            return
        }
        val property = node.psi as KtProperty
        if (!property.isConstantCandidate()) {
            return
        }
        val nameIdentifier = property.nameIdentifier ?: return
        val name = nameIdentifier.text
        val initializer = property.initializer ?: return
        if (initializer.isScalar()) {
            val type = property.typeReference?.text
            if (!property.hasModifier(KtTokens.CONST_KEYWORD) &&
                name.matches(UPPER_SNAKE_CASE) &&
                (type == null || type in CONST_TYPES)
            ) {
                emit(
                    nameIdentifier.node.startOffset,
                    "Scalar constants use the const modifier: '$name'",
                    false
                )
            }
        } else if (initializer.isImmutableCollection() && !name.matches(UPPER_SNAKE_CASE)) {
            emit(
                nameIdentifier.node.startOffset,
                "Deeply immutable values are constants and use UPPER_SNAKE_CASE: '$name'",
                false
            )
        }
    }

    private fun KtProperty.isConstantCandidate(): Boolean {
        val inObject = (parent as? KtClassBody)
            ?.parent
            ?.let { it is KtObjectDeclaration && !it.isObjectLiteral() } == true
        return (isTopLevel || inObject) &&
            !isVar &&
            !hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
            !hasModifier(KtTokens.LATEINIT_KEYWORD) &&
            getter == null &&
            !hasDelegate()
    }

    private fun KtExpression.isScalar(): Boolean = when (this) {
        is KtConstantExpression -> node.elementType in SCALAR_LITERALS

        is KtPrefixExpression -> {
            val base = baseExpression
            operationReference.text == "-" &&
                base is KtConstantExpression &&
                base.node.elementType in NUMERIC_LITERALS
        }

        is KtStringTemplateExpression -> !hasInterpolation()

        else -> false
    }

    private fun KtExpression.isImmutableCollection(): Boolean {
        if (this !is KtCallExpression ||
            calleeExpression?.text !in COLLECTION_FACTORIES ||
            lambdaArguments.isNotEmpty()
        ) {
            return false
        }
        return valueArguments.all { argument ->
            val expression = argument.getArgumentExpression()
            !argument.isSpread && expression != null && expression.isScalarOrScalarPair()
        }
    }

    private fun KtExpression.isScalarOrScalarPair(): Boolean {
        if (this is KtBinaryExpression && operationReference.text == "to") {
            return left?.isScalar() == true && right?.isScalar() == true
        }
        return isScalar()
    }

    private companion object {
        val UPPER_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*")
        val NUMERIC_LITERALS = setOf(INTEGER_CONSTANT, FLOAT_CONSTANT)
        val SCALAR_LITERALS =
            setOf(INTEGER_CONSTANT, FLOAT_CONSTANT, BOOLEAN_CONSTANT, CHARACTER_CONSTANT)
        val CONST_TYPES =
            setOf("Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String")
        val COLLECTION_FACTORIES = setOf(
            "listOf",
            "setOf",
            "mapOf",
            "emptyList",
            "emptySet",
            "emptyMap",
            "listOfNotNull"
        )
    }
}
