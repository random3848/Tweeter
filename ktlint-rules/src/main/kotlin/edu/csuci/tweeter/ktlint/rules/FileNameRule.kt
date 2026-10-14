package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.FILE
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import edu.csuci.tweeter.ktlint.filePath
import edu.csuci.tweeter.ktlint.isInScript
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/** A file name in PascalCase, such as `Map`. */
private val PASCAL_CASE = Regex("[A-Z][A-Za-z0-9]*")

/** A plural file name in camelCase, such as `extensions`. */
private val PLURAL_CAMEL_CASE = Regex("[a-z][A-Za-z0-9]*s")

/**
 * A source file holding a single top-level class is named after that class; any other source file
 * is named in PascalCase, or in camelCase when the name is plural (`extensions.kt`).
 *
 * Replaces ktlint's `standard:filename`, which rejects the guide's plural camelCase names. Only
 * `.kt` files are checked: scripts, code without a file, and files without top-level declarations
 * are skipped. Any name ending in `s` counts as plural. Like `standard:filename`, only the part
 * of the file name before the first `.` is compared, so `Platform.android.kt` may hold
 * `class Platform`.
 */
internal class FileNameRule : AndroidStyleRule("file-name") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != FILE) {
            return
        }
        stopTraversalOfAST()
        val path = node.filePath
        if (path == null || node.isInScript || !path.endsWith(".kt")) {
            return
        }
        val fileName = path.substringAfterLast('/').substringBefore('.')
        val declarations = (node.psi as KtFile).declarations
        val singleDeclaration = declarations.singleOrNull()
        if (singleDeclaration is KtClassOrObject) {
            val className = singleDeclaration.name
            if (className != null && fileName != className) {
                emit(
                    0,
                    "A file holding a single class is named after it: '$fileName.kt' should be " +
                        "'$className.kt'",
                    false
                )
            }
        } else if (declarations.isNotEmpty() && !fileName.isPascalCaseOrPluralCamelCase()) {
            emit(
                0,
                "File names are PascalCase, or camelCase if plural: '$fileName.kt'",
                false
            )
        }
    }

    private fun String.isPascalCaseOrPluralCamelCase(): Boolean =
        PASCAL_CASE.matches(this) || PLURAL_CAMEL_CASE.matches(this)
}
