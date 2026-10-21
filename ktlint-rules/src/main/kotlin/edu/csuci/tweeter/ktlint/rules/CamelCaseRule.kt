package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ENUM_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OBJECT_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPEALIAS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor

/**
 * Camel case treats acronyms as words: `XmlHttpRequest`, `newCustomerId` and `supportsIpv6OnIos`,
 * not `XMLHTTPRequest`, `newCustomerID` and `supportsIPv6OnIOS`.
 *
 * Reports any declared name (class, object, interface, type alias, function, property, value
 * parameter, destructuring entry or enum entry) containing two consecutive uppercase letters.
 * Names entirely in UPPER_SNAKE_CASE (constants, enum entries) are exempt, as are type parameters
 * (owned by `android-style:type-parameter-naming`), backticked names, overrides (and the
 * parameters of overriding functions and property setters), whose names come from a supertype,
 * and the unused-name placeholder `_`. A name that legitimately contains a one-letter word, such
 * as `XAxis`, cannot be told apart from an acronym and must be suppressed with
 * `@Suppress("ktlint:android-style:camel-case")`.
 */
internal class CamelCaseRule : AndroidStyleRule("camel-case") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType !in NAMED_DECLARATIONS) {
            return
        }
        val declaration = node.psi as KtNamedDeclaration
        val identifier = declaration.nameIdentifier ?: return
        val text = identifier.text
        if (text.startsWith("`") || UPPER_SNAKE_CASE.matches(text)) {
            return
        }
        if (!ADJACENT_CAPITALS.containsMatchIn(text) || declaration.isOverride()) {
            return
        }
        emit(
            identifier.node.startOffset,
            "Acronyms are written as words in camel case (XmlHttpRequest, not XMLHTTPRequest): " +
                "'$text'",
            false
        )
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
            ENUM_ENTRY
        )
        val UPPER_SNAKE_CASE = Regex("[A-Z][A-Z0-9_]*")
        val ADJACENT_CAPITALS = Regex("[A-Z]{2}")
    }
}
