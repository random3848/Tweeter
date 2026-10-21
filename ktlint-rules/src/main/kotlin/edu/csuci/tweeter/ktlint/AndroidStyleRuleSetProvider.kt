package edu.csuci.tweeter.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId
import edu.csuci.tweeter.ktlint.rules.AnnotationArrayRule
import edu.csuci.tweeter.ktlint.rules.AsciiIdentifierRule
import edu.csuci.tweeter.ktlint.rules.BackingPropertyNamingRule
import edu.csuci.tweeter.ktlint.rules.CamelCaseRule
import edu.csuci.tweeter.ktlint.rules.CommentLineLengthRule
import edu.csuci.tweeter.ktlint.rules.ComposableNamingRule
import edu.csuci.tweeter.ktlint.rules.ConstantRule
import edu.csuci.tweeter.ktlint.rules.EmptyBlockRule
import edu.csuci.tweeter.ktlint.rules.EnumEntrySpacingRule
import edu.csuci.tweeter.ktlint.rules.FileNameRule
import edu.csuci.tweeter.ktlint.rules.FileStructureRule
import edu.csuci.tweeter.ktlint.rules.KdocFormatRule
import edu.csuci.tweeter.ktlint.rules.KdocRequiredRule
import edu.csuci.tweeter.ktlint.rules.KdocSummaryRule
import edu.csuci.tweeter.ktlint.rules.MandatoryBracesRule
import edu.csuci.tweeter.ktlint.rules.MemberSpacingRule
import edu.csuci.tweeter.ktlint.rules.NamingPrefixRule
import edu.csuci.tweeter.ktlint.rules.OperatorWrappingRule
import edu.csuci.tweeter.ktlint.rules.PackageNameRule
import edu.csuci.tweeter.ktlint.rules.ParameterNamingRule
import edu.csuci.tweeter.ktlint.rules.SourceEncodingRule
import edu.csuci.tweeter.ktlint.rules.TestClassNamingRule
import edu.csuci.tweeter.ktlint.rules.TypeParameterNamingRule
import edu.csuci.tweeter.ktlint.rules.UnicodeEscapeRule
import edu.csuci.tweeter.ktlint.rules.WhitespaceCharactersRule

/**
 * Rules for the parts of the Android Kotlin style guide that ktlint's standard rules miss.
 *
 * ktlint finds this class through `META-INF/services`.
 */
public class AndroidStyleRuleSetProvider : RuleSetProviderV3(RuleSetId(RULE_SET_ID)) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        // Source files
        RuleProvider { FileNameRule() },
        RuleProvider { SourceEncodingRule() },
        RuleProvider { WhitespaceCharactersRule() },
        RuleProvider { UnicodeEscapeRule() },
        RuleProvider { FileStructureRule() },
        // Formatting
        RuleProvider { MandatoryBracesRule() },
        RuleProvider { EmptyBlockRule() },
        RuleProvider { CommentLineLengthRule() },
        RuleProvider { OperatorWrappingRule() },
        RuleProvider { MemberSpacingRule() },
        RuleProvider { EnumEntrySpacingRule() },
        RuleProvider { AnnotationArrayRule() },
        // Naming
        RuleProvider { AsciiIdentifierRule() },
        RuleProvider { NamingPrefixRule() },
        RuleProvider { PackageNameRule() },
        RuleProvider { TestClassNamingRule() },
        RuleProvider { ComposableNamingRule() },
        RuleProvider { ConstantRule() },
        RuleProvider { ParameterNamingRule() },
        RuleProvider { BackingPropertyNamingRule() },
        RuleProvider { TypeParameterNamingRule() },
        RuleProvider { CamelCaseRule() },
        // Documentation
        RuleProvider { KdocFormatRule() },
        RuleProvider { KdocSummaryRule() },
        RuleProvider { KdocRequiredRule() }
    )
}
