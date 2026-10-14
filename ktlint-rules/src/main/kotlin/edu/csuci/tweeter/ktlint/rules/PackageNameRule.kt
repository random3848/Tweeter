package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.PACKAGE_DIRECTIVE
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtPackageDirective

/**
 * Package names are all lowercase, with words concatenated (`com.example.deepspace`).
 *
 * ktlint's `standard:package-name` already rejects underscores; this rule rejects uppercase
 * letters, which ktlint allows.
 */
internal class PackageNameRule : AndroidStyleRule("package-name") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != PACKAGE_DIRECTIVE) {
            return
        }
        val directive = node.psi as KtPackageDirective
        directive.packageNames
            .firstOrNull { name -> name.getReferencedName().any { it.isUpperCase() } }
            ?.let { name ->
                emit(
                    name.node.startOffset,
                    "Package names are all lowercase: '${directive.qualifiedName}'",
                    false
                )
            }
    }
}
