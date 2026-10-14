package edu.csuci.tweeter.ktlint.rules

import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import edu.csuci.tweeter.ktlint.isInScript
import edu.csuci.tweeter.ktlint.isInTestSource
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

/**
 * KDoc is present for every public type and every public or protected member of such a type.
 *
 * Types are classes, interfaces, enum and annotation classes, object declarations, and type
 * aliases; a companion object is not a type of its own, its members belong to the enclosing type.
 * A primary-constructor `val` or `var` is documented by its own KDoc or by a `@property` tag in
 * the class KDoc. Top-level functions and properties, `override` declarations, enum entries,
 * local declarations, test sources, and Kotlin scripts are exempt. The guide also exempts
 * self-explanatory members such as a simple `getFoo`; suppress those individually with
 * `@Suppress("ktlint:android-style:kdoc-required")`.
 */
internal class KdocRequiredRule : AndroidStyleRule("kdoc-required") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        val declaration = node.psi as? KtNamedDeclaration ?: return
        val message = when (declaration) {
            is KtClassOrObject, is KtTypeAlias -> typeMessage(declaration)
            is KtNamedFunction, is KtProperty -> memberMessage(declaration)
            is KtParameter -> constructorPropertyMessage(declaration)
            else -> null
        } ?: return
        if (node.isInScript || node.isInTestSource) {
            return
        }
        emit(declaration.nameIdentifier?.node?.startOffset ?: node.startOffset, message, false)
    }

    private fun typeMessage(type: KtNamedDeclaration): String? {
        if (type is KtEnumEntry ||
            (type is KtObjectDeclaration && (type.isCompanion() || type.isObjectLiteral())) ||
            !type.isExposed() ||
            type.docComment != null
        ) {
            return null
        }
        return "Public type ${type.name} has no KDoc"
    }

    private fun memberMessage(member: KtNamedDeclaration): String? {
        if (member.parent !is KtClassBody ||
            member.hasModifier(OVERRIDE_KEYWORD) ||
            !member.isExposed() ||
            member.docComment != null
        ) {
            return null
        }
        return "Public member ${member.name} has no KDoc"
    }

    private fun constructorPropertyMessage(parameter: KtParameter): String? {
        val constructor = parameter.parent?.parent as? KtPrimaryConstructor ?: return null
        val owner = constructor.getContainingClassOrObject()
        val name = parameter.name ?: return null
        if (!parameter.hasValOrVar() ||
            parameter.hasModifier(OVERRIDE_KEYWORD) ||
            parameter.isHidden() ||
            !owner.isExposed() ||
            parameter.docComment != null ||
            owner.docComment.hasPropertyTag(name)
        ) {
            return null
        }
        return "Public property $name has no KDoc or @property tag"
    }

    /** Whether this declaration is private or internal. */
    private fun KtDeclaration.isHidden(): Boolean =
        hasModifier(PRIVATE_KEYWORD) || hasModifier(INTERNAL_KEYWORD)

    /**
     * Whether this declaration is public at top level, or public or protected in a type that is
     * itself exposed; members of enum entries, object literals, and local types are not exposed.
     */
    private fun KtDeclaration.isExposed(): Boolean {
        if (isHidden()) {
            return false
        }
        return when (val container = parent) {
            is KtFile -> true

            is KtClassBody -> {
                val owner = container.parent as? KtClassOrObject
                owner != null &&
                    owner !is KtEnumEntry &&
                    !(owner is KtObjectDeclaration && owner.isObjectLiteral()) &&
                    owner.isExposed()
            }

            else -> false
        }
    }

    /** Whether this KDoc has a `@property` tag for [name]. */
    private fun KDoc?.hasPropertyTag(name: String): Boolean = this != null &&
        text.lineSequence().any { line ->
            PROPERTY_TAG.find(line)?.groupValues?.get(1)?.removeSurrounding("[", "]") == name
        }

    private companion object {
        val PROPERTY_TAG = Regex("""^\s*(?:/\*\*|\*)?\s*@property\s+(\S+)""")
    }
}
