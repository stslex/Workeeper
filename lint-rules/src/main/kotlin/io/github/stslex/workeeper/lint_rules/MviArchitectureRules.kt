package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Custom Detekt rules for MVI architecture compliance in Workeeper project
 */
class MviArchitectureRuleSet : RuleSetProvider {
    override val ruleSetId: String = "mvi-architecture"

    override fun instance(config: Config): RuleSet = RuleSet(
        id = ruleSetId,
        rules = listOf(
            MviStateImmutabilityRule(config),
            MviActionNamingRule(config),
            MviEventNamingRule(config),
            MviHandlerNamingRule(config),
            MviStoreExtensionRule(config),
            MviHandlerConstructorRule(config),
            MviStoreStateRule(config),
            MetroScopeRule(config),
            ContributesBindingScopeRule(config),
            ContributesToScopeRule(config),
            ScreenInjectionRule(config),
            ComposableStateRule(config),
            DomainLayerNoUiRule(config),
            DomainLayerPurityRule(config),
            UiLayerNoDataRule(config),
            DiscardedScopeResultRule(config),
            NoActualForExpectSuppressionRule(config),
            NumericFontFamilyOnLocalizedTextRule(config),
        ),
    )
}
