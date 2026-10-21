package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript

/**
 * Backing properties are private, named like the property they back with a leading
 * underscore (`_table` backs `table`), in lower camel case.
 *
 * Replaces `standard:backing-property-naming`, which only searches class bodies, so that
 * top-level backing properties (the guide's own example) are accepted. The backed property, or
 * a no-argument `getName` function, is searched in the same class body, in the outer class
 * body for a companion object, and in the same file (or script) for top-level properties.
 * Overrides and the discard name `_` are skipped. Local properties have nothing to back and are
 * reported.
 */
internal class BackingPropertyNamingRule : AndroidStyleRule("backing-property-naming") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != PROPERTY) {
            return
        }
        val property = node.psi as KtProperty
        val identifier = property.nameIdentifier ?: return
        val name = identifier.text
        if (!name.startsWith("_") || name == "_" ||
            property.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        ) {
            return
        }
        val offset = identifier.node.startOffset
        if (!name.matches(BACKING_PROPERTY_NAME)) {
            emit(
                offset,
                "Backing properties start with an underscore followed by lower camel case: " +
                    "'$name'",
                false
            )
        }
        if (!property.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
            property.enclosingCompanion()?.hasModifier(KtTokens.PRIVATE_KEYWORD) != true
        ) {
            emit(offset, "Backing properties are private: '$name'", false)
        }
        if (!property.hasCorrelatedDeclaration(name.removePrefix("_"))) {
            emit(
                offset,
                "Backing properties need a matching property or getter function: '$name'",
                false
            )
        }
    }

    private fun KtProperty.enclosingCompanion(): KtObjectDeclaration? =
        ((parent as? KtClassBody)?.parent as? KtObjectDeclaration)?.takeIf { it.isCompanion() }

    private fun KtProperty.hasCorrelatedDeclaration(propertyName: String): Boolean {
        val getterName = "get" + propertyName.replaceFirstChar { it.uppercase() }
        val scopes = when (val container = parent) {
            is KtFile -> listOf(container.declarations)

            is KtClassBody -> {
                listOfNotNull(
                    container.declarations,
                    enclosingCompanion()?.let { (it.parent as? KtClassBody)?.declarations }
                )
            }

            is KtBlockExpression -> {
                if (container.parent is KtScript) {
                    listOf(container.statements.filterIsInstance<KtDeclaration>())
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }
        return scopes.any { declarations ->
            declarations.any { declaration ->
                (declaration is KtProperty && declaration.name == propertyName) ||
                    (
                        declaration is KtNamedFunction &&
                            declaration.name == getterName &&
                            declaration.valueParameters.isEmpty()
                        )
            }
        }
    }

    private companion object {
        val BACKING_PROPERTY_NAME = Regex("_[a-z][A-Za-z0-9]*")
    }
}
