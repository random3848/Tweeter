package edu.csuci.tweeter.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import edu.csuci.tweeter.ktlint.AndroidStyleRule
import edu.csuci.tweeter.ktlint.Emit
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Test classes end with `Test` (`HashIntegrationTest`).
 *
 * A class counts as a test class when it declares a function annotated with `@Test`,
 * `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory`, matched by the annotation's short name
 * so both JUnit 4 and JUnit 5 are covered. Only functions declared directly in the class body are
 * considered; nested classes are checked on their own.
 */
internal class TestClassNamingRule : AndroidStyleRule("test-class-naming") {
    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != CLASS) {
            return
        }
        val testClass = node.psi as KtClass
        val identifier = testClass.nameIdentifier ?: return
        val name = identifier.text.removeSurrounding("`")
        val declaresTest = testClass.body?.functions.orEmpty().any { it.isTest() }
        if (!declaresTest || name.endsWith("Test")) {
            return
        }
        emit(identifier.node.startOffset, "Test class names end with Test: '$name'", false)
    }

    private fun KtNamedFunction.isTest(): Boolean =
        annotationEntries.any { it.shortName?.asString() in TEST_ANNOTATIONS }

    private companion object {
        val TEST_ANNOTATIONS = setOf("Test", "ParameterizedTest", "RepeatedTest", "TestFactory")
    }
}
