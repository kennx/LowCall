package cc.niaoer.nocall.data

import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMatcherTest {

    private val matcher = RuleMatcher()

    @Test
    fun match_exact_sameNumber_returnsRule() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_exact_differentNumber_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = matcher.match("13800138001", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_starMatchesAny() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_wildcard_starNoMatch_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_questionMatchesSingleChar() {
        val rule = BlockRule(id = 1, pattern = "138????????", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_validPattern_matches() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_invalidInput_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = matcher.match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_disabledRule_isSkipped() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT, enabled = false)
        val result = matcher.match("13800138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_multipleRules_returnsFirstMatch() {
        val rule1 = BlockRule(id = 1, pattern = "139*", ruleType = RuleType.WILDCARD)
        val rule2 = BlockRule(id = 2, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule1, rule2))
        assertEquals(rule2, result)
    }

    @Test
    fun match_emptyRules_returnsNull() {
        val result = matcher.match("13800138000", emptyList())
        assertNull(result)
    }

    @Test
    fun match_wildcard_specialRegexChars_escaped() {
        val rule = BlockRule(id = 1, pattern = "+86*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("+8613800138000", listOf(rule))
        assertEquals(rule, result)
    }
}
