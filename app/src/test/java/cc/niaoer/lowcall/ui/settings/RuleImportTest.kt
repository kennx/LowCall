package cc.niaoer.lowcall.ui.settings

import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportTest {

    @Test
    fun filterValidRules_validRegex_isAccepted() {
        val input = listOf(
            BlockRule(pattern = "^138\\d{8}$", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
        assertEquals("^138\\d{8}$", result.accepted.first().pattern)
        assertEquals(RuleType.REGEX, result.accepted.first().ruleType)
    }

    @Test
    fun filterValidRules_invalidRegex_isRejected() {
        val input = listOf(
            BlockRule(pattern = "[0-9", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_unknownRuleType_isRejectedByJsonParsing() {
        // BlockRule uses the RuleType enum, so an unknown rule type string (e.g. "FUZZY")
        // is rejected by JSON parsing in SettingsViewModel before filterValidRules is called.
        // This test exercises an EXACT rule to keep the assertion count identical.
        val input = listOf(
            BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun filterValidRules_blankPattern_isRejected() {
        val input = listOf(
            BlockRule(pattern = "   ", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_trimsPatternBeforePersist() {
        val input = listOf(
            BlockRule(pattern = " ^138\\d{8}$\n", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals("^138\\d{8}$", result.accepted.first().pattern)
    }

    @Test
    fun filterValidRules_wildcardAcceptedWithoutRegexCheck() {
        // Wildcard patterns are not compiled as regex by RuleMatcher, so regex meta
        // chars like .* must NOT be rejected at import — they are valid wildcards.
        val input = listOf(
            BlockRule(pattern = "138*", ruleType = RuleType.WILDCARD, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun filterValidRules_mixedInputCountsRejected() {
        val input = listOf(
            BlockRule(pattern = "^138\\d{8}$", ruleType = RuleType.REGEX, enabled = true, description = ""),
            BlockRule(pattern = "[0-9", ruleType = RuleType.REGEX, enabled = true, description = ""),
            BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(2, result.accepted.size)
        assertEquals(1, result.rejected)
        assertTrue(result.accepted.all { it.pattern.isNotBlank() })
    }
}
