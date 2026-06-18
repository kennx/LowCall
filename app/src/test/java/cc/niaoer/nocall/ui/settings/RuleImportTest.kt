package cc.niaoer.nocall.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportTest {

    @Test
    fun filterValidRules_validRegex_isAccepted() {
        val input = listOf(
            ExportRule(pattern = "^138\\d{8}$", ruleType = "REGEX", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
        assertEquals("^138\\d{8}$", result.accepted.first().pattern)
        assertEquals("REGEX", result.accepted.first().ruleType)
    }

    @Test
    fun filterValidRules_invalidRegex_isRejected() {
        val input = listOf(
            ExportRule(pattern = "[0-9", ruleType = "REGEX", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_unknownRuleType_isRejected() {
        val input = listOf(
            ExportRule(pattern = "13800138000", ruleType = "FUZZY", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_blankPattern_isRejected() {
        val input = listOf(
            ExportRule(pattern = "   ", ruleType = "EXACT", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_trimsPatternBeforePersist() {
        val input = listOf(
            ExportRule(pattern = " ^138\\d{8}$\n", ruleType = "REGEX", enabled = true, description = "")
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
            ExportRule(pattern = "138*", ruleType = "WILDCARD", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun filterValidRules_mixedInputCountsRejected() {
        val input = listOf(
            ExportRule(pattern = "^138\\d{8}$", ruleType = "REGEX", enabled = true, description = ""),
            ExportRule(pattern = "[0-9", ruleType = "REGEX", enabled = true, description = ""),
            ExportRule(pattern = "13800138000", ruleType = "EXACT", enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(2, result.accepted.size)
        assertEquals(1, result.rejected)
        assertTrue(result.accepted.all { it.pattern.isNotBlank() })
    }
}
