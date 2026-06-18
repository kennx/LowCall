package cc.niaoer.nocall.ui.rules

import cc.niaoer.nocall.data.isValidRegex
import cc.niaoer.nocall.data.looksLikeRegex
import cc.niaoer.nocall.data.model.RuleType

sealed interface RuleEditDecision {
    data object NoOp : RuleEditDecision
    data object CancelSuggestion : RuleEditDecision
    data class ShowRegexSuggestion(val pattern: String) : RuleEditDecision
    data class InvalidRegex(val pattern: String) : RuleEditDecision
    data class Persist(val pattern: String, val ruleType: RuleType) : RuleEditDecision
}

enum class RegexSuggestionAction {
    CANCEL,
    KEEP_WILDCARD,
    SWITCH_TO_REGEX
}

enum class RulePatternError {
    INVALID_REGEX
}

fun decideRuleSave(pattern: String, ruleType: RuleType): RuleEditDecision {
    val normalizedPattern = pattern.trim()
    if (normalizedPattern.isBlank()) return RuleEditDecision.NoOp

    return when (ruleType) {
        RuleType.EXACT -> RuleEditDecision.Persist(normalizedPattern, ruleType)
        RuleType.WILDCARD -> if (looksLikeRegex(normalizedPattern)) {
            RuleEditDecision.ShowRegexSuggestion(normalizedPattern)
        } else {
            RuleEditDecision.Persist(normalizedPattern, ruleType)
        }
        RuleType.REGEX -> if (isValidRegex(normalizedPattern)) {
            RuleEditDecision.Persist(normalizedPattern, ruleType)
        } else {
            RuleEditDecision.InvalidRegex(normalizedPattern)
        }
    }
}

fun decideRegexSuggestion(
    pattern: String,
    action: RegexSuggestionAction
): RuleEditDecision {
    val normalizedPattern = pattern.trim()
    if (normalizedPattern.isBlank()) return RuleEditDecision.NoOp

    return when (action) {
        RegexSuggestionAction.CANCEL -> RuleEditDecision.CancelSuggestion
        RegexSuggestionAction.KEEP_WILDCARD -> {
            RuleEditDecision.Persist(normalizedPattern, RuleType.WILDCARD)
        }
        RegexSuggestionAction.SWITCH_TO_REGEX -> if (isValidRegex(normalizedPattern)) {
            RuleEditDecision.Persist(normalizedPattern, RuleType.REGEX)
        } else {
            RuleEditDecision.InvalidRegex(normalizedPattern)
        }
    }
}
