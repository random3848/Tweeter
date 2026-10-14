package edu.csuci.tweeter.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile

/** Rule set id; rule ids are `android-style:<rule>`. */
internal const val RULE_SET_ID = "android-style"

/** Callback a rule uses to report a violation and to ask whether it may fix it. */
internal typealias Emit = (
    offset: Int,
    errorMessage: String,
    canBeAutoCorrected: Boolean
) -> AutocorrectDecision

/** Base class of every rule in this rule set. */
internal abstract class AndroidStyleRule(
    id: String,
    usesEditorConfigProperties: Set<EditorConfigProperty<*>> = emptySet(),
    visitorModifiers: Set<VisitorModifier> = emptySet()
) : Rule(
    ruleId = RuleId("$RULE_SET_ID:$id"),
    about = About(
        maintainer = "Tweeter",
        repositoryUrl = "https://github.com/random3848/Tweeter",
        issueTrackerUrl = "https://github.com/random3848/Tweeter/issues"
    ),
    visitorModifiers = visitorModifiers,
    usesEditorConfigProperties = usesEditorConfigProperties
),
    RuleAutocorrectApproveHandler

/** Path of the file this node belongs to, with `/` separators, or null for code without a file. */
internal val ASTNode.filePath: String?
    get() = (psi.containingFile as? KtFile)?.virtualFilePath?.replace('\\', '/')

/** Whether this node is in a Kotlin script (`.kts`) rather than a source file (`.kt`). */
internal val ASTNode.isInScript: Boolean
    get() = (psi.containingFile as? KtFile)?.isScript() == true

/** Matches Gradle test source sets: `src/test`, `src/androidTest`, `src/testDebug`, and so on. */
private val TEST_SOURCE_SET = Regex("""(^|/)src/(test|androidTest|testFixtures)[^/]*/""")

/** Whether this node is in a test source set. */
internal val ASTNode.isInTestSource: Boolean
    get() = filePath?.let { TEST_SOURCE_SET.containsMatchIn(it) } == true
