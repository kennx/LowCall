package cc.niaoer.lowcall.data

import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    @Test
    fun match_exact_sameNumber_returnsRule() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_exact_differentNumber_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = match("13800138001", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_starMatchesAny() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_wildcard_starNoMatch_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_questionMatchesSingleChar() {
        val rule = BlockRule(id = 1, pattern = "138????????", ruleType = RuleType.WILDCARD)
        val result = match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_validPattern_matches() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_invalidInput_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_disabledRule_isSkipped() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT, enabled = false)
        val result = match("13800138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_multipleRules_returnsFirstMatch() {
        val rule1 = BlockRule(id = 1, pattern = "139*", ruleType = RuleType.WILDCARD)
        val rule2 = BlockRule(id = 2, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = match("13800138000", listOf(rule1, rule2))
        assertEquals(rule2, result)
    }

    @Test
    fun match_emptyRules_returnsNull() {
        val result = match("13800138000", emptyList())
        assertNull(result)
    }

    @Test
    fun match_wildcard_specialRegexChars_escaped() {
        val rule = BlockRule(id = 1, pattern = "+86*", ruleType = RuleType.WILDCARD)
        val result = match("+8613800138000", listOf(rule))
        assertEquals(rule, result)
    }

    // --- looksLikeRegex tests ---

    @Test
    fun looksLikeRegex_anchoredPattern_returnsTrue() {
        assertTrue(looksLikeRegex("^138\\d{8}$"))
    }

    @Test
    fun looksLikeRegex_digitClass_returnsTrue() {
        assertTrue(looksLikeRegex("\\d{8}"))
    }

    @Test
    fun looksLikeRegex_charClass_returnsTrue() {
        assertTrue(looksLikeRegex("[2-9]\\d{6}"))
    }

    @Test
    fun looksLikeRegex_alternation_returnsTrue() {
        assertTrue(looksLikeRegex("138|139"))
    }

    @Test
    fun looksLikeRegex_quantifierBrace_returnsTrue() {
        assertTrue(looksLikeRegex("13{5,}"))
    }

    @Test
    fun looksLikeRegex_lookahead_returnsTrue() {
        assertTrue(looksLikeRegex("(?!86)\\d"))
    }

    @Test
    fun looksLikeRegex_plainWildcard_returnsFalse() {
        assertFalse(looksLikeRegex("138*"))
    }

    @Test
    fun looksLikeRegex_wildcardWithPlus_returnsFalse() {
        assertFalse(looksLikeRegex("+86*"))
    }

    @Test
    fun looksLikeRegex_questionMarks_returnsFalse() {
        assertFalse(looksLikeRegex("138????????"))
    }

    @Test
    fun looksLikeRegex_exactNumber_returnsFalse() {
        assertFalse(looksLikeRegex("13800138000"))
    }

    @Test
    fun looksLikeRegex_dotStar_returnsTrue() {
        assertTrue(looksLikeRegex("138.*"))
    }

    @Test
    fun looksLikeRegex_groupedPattern_returnsTrue() {
        assertTrue(looksLikeRegex("(138).*"))
    }

    @Test
    fun isValidRegex_validPattern_returnsTrue() {
        assertTrue(isValidRegex("^138\\d{8}$"))
    }

    @Test
    fun isValidRegex_unclosedCharacterClass_returnsFalse() {
        assertFalse(isValidRegex("[0-9"))
    }

    // --- Bug reproduction: regex pattern stored as WILDCARD ---

    @Test
    fun bug_regexPatternAsWildcard_doesNotMatch() {
        val pattern = "^(00|\\+)(95|855|63|44|852|853|886|678|257)[\\s\\-]*\\d{5,}$"
        val rule = BlockRule(id = 6, pattern = pattern, ruleType = RuleType.WILDCARD)
        val result = match("0085245995523", listOf(rule))
        assertNull(result)
    }

    @Test
    fun bug_regexPatternAsRegex_matches() {
        val pattern = "^(00|\\+)(95|855|63|44|852|853|886|678|257)[\\s\\-]*\\d{5,}$"
        val rule = BlockRule(id = 6, pattern = pattern, ruleType = RuleType.REGEX)
        val result = match("0085245995523", listOf(rule))
        assertEquals(rule, result)
    }

    // --- Bug reproduction: trailing newline in pattern ---

    @Test
    fun bug_patternWithTrailingNewline_doesNotMatch() {
        val rule = BlockRule(id = 3, pattern = "^1(4\\d|6[0257]|7[01]|9[12])\\d{8}$\n", ruleType = RuleType.REGEX)
        val result = match("16292117270", listOf(rule))
        assertNull(result)
    }

    @Test
    fun bug_patternWithoutTrailingNewline_matches() {
        val rule = BlockRule(id = 3, pattern = "^1(4\\d|6[0257]|7[01]|9[12])\\d{8}$", ruleType = RuleType.REGEX)
        val result = match("16292117270", listOf(rule))
        assertEquals(rule, result)
    }
}
