package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor

/**
 * Names don't use special prefixes or suffixes like `name_`, `mName`, `s_name` and `kName`.
 *
 * Reports properties (class members, top-level and local), value parameters and destructuring
 * entries whose name is a Hungarian-notation prefix `m`, `s` or `k` followed by an uppercase
 * letter. UPPER_SNAKE_CASE constants never match. Overrides (and the parameters of overriding
 * functions and property setters) are skipped because their names come from a supertype, as is
 * the unused-name placeholder `_`. Underscore prefixes and suffixes are left to
 * `standard:property-naming` and `android-style:parameter-naming`.
 */
internal class NamingPrefixRule : AndroidStyleRule("naming-prefix") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType !in VARIABLES) {
            return
        }
        val declaration = node.psi as KtNamedDeclaration
        val identifier = declaration.nameIdentifier ?: return
        val name = identifier.text.removeSurrounding("`")
        if (!SPECIAL_PREFIX.matches(name) || declaration.isOverride()) {
            return
        }
        emit(
            identifier.node.startOffset,
            "Names don't use special prefixes such as 'm', 's' or 'k': '$name'",
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
        val VARIABLES = setOf(PROPERTY, VALUE_PARAMETER, DESTRUCTURING_DECLARATION_ENTRY)
        val SPECIAL_PREFIX = Regex("[mks][A-Z].*")
    }
}
