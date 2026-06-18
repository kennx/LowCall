package cc.niaoer.nocall.ui.rules

import cc.niaoer.nocall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEditDecisionTest {

    @Test
    fun decideRuleSave_invalidRegex_returnsValidationError() {
        assertEquals(
            RuleEditDecision.InvalidRegex(pattern = "[0-9"),
            decideRuleSave(pattern = "[0-9", ruleType = RuleType.REGEX)
        )
    }

    @Test
    fun decideRuleSave_regexShapedWildcard_requestsSuggestion() {
        assertEquals(
            RuleEditDecision.ShowRegexSuggestion(pattern = "138.*"),
            decideRuleSave(pattern = " 138.* ", ruleType = RuleType.WILDCARD)
        )
    }

    @Test
    fun decideRuleSave_validRegex_returnsPersist() {
        assertEquals(
            RuleEditDecision.Persist(pattern = "^138\\d{8}$", ruleType = RuleType.REGEX),
            decideRuleSave(pattern = " ^138\\d{8}$\n", ruleType = RuleType.REGEX)
        )
    }

    @Test
    fun decideRegexSuggestion_cancel_returnsCancelWithoutPersistence() {
        assertEquals(
            RuleEditDecision.CancelSuggestion,
            decideRegexSuggestion(
                pattern = "138.*",
                action = RegexSuggestionAction.CANCEL
            )
        )
    }

    @Test
    fun decideRegexSuggestion_keepWildcard_returnsWildcardPersist() {
        assertEquals(
            RuleEditDecision.Persist(pattern = "138.*", ruleType = RuleType.WILDCARD),
            decideRegexSuggestion(
                pattern = "138.*",
                action = RegexSuggestionAction.KEEP_WILDCARD
            )
        )
    }

    @Test
    fun decideRegexSuggestion_switchInvalidRegex_returnsValidationError() {
        assertEquals(
            RuleEditDecision.InvalidRegex(pattern = "[0-9"),
            decideRegexSuggestion(
                pattern = "[0-9",
                action = RegexSuggestionAction.SWITCH_TO_REGEX
            )
        )
    }

    @Test
    fun decideRegexSuggestion_switchValidRegex_returnsRegexPersist() {
        assertEquals(
            RuleEditDecision.Persist(pattern = "138.*", ruleType = RuleType.REGEX),
            decideRegexSuggestion(
                pattern = "138.*",
                action = RegexSuggestionAction.SWITCH_TO_REGEX
            )
        )
    }
}
