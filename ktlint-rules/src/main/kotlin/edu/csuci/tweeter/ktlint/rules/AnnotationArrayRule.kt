package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.ANNOTATION
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtAnnotation

/**
 * The `@[...]` annotation syntax is used only with an explicit use-site target, and only to
 * combine two or more annotations without arguments (`@field:[JvmStatic Volatile]`).
 *
 * Each broken condition is reported separately; none is corrected automatically, since splitting
 * or merging annotations is left to the author. Annotations written one per `@` are not checked.
 */
internal class AnnotationArrayRule : AndroidStyleRule("annotation-array") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ANNOTATION) {
            return
        }
        val annotation = node.psi as KtAnnotation
        val offset = node.startOffset
        if (annotation.useSiteTarget == null) {
            emit(
                offset,
                "The '@[...]' syntax is used only with a use-site target: '${node.text}'",
                false
            )
        }
        if (annotation.entries.size < 2) {
            emit(
                offset,
                "The '@[...]' syntax combines two or more annotations: '${node.text}'",
                false
            )
        }
        annotation.entries
            .firstOrNull { it.valueArguments.isNotEmpty() }
            ?.let { entry ->
                emit(
                    offset,
                    "The '@[...]' syntax combines only annotations without arguments: " +
                        "'${entry.text}'",
                    false
                )
            }
    }
}
