package cc.niaoer.nocall.data

import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType

class RuleMatcher {

    fun match(phoneNumber: String, rules: List<BlockRule>): BlockRule? {
        return rules.firstOrNull { rule ->
            rule.enabled && when (rule.ruleType) {
                RuleType.EXACT -> phoneNumber == rule.pattern
                RuleType.WILDCARD -> wildcardToRegex(rule.pattern).matches(phoneNumber)
                RuleType.REGEX -> runCatching {
                    Regex(rule.pattern).matches(phoneNumber)
                }.getOrDefault(false)
            }
        }
    }

    private fun wildcardToRegex(pattern: String): Regex {
        val escaped = StringBuilder()
        for (ch in pattern) {
            when (ch) {
                '*' -> escaped.append(".*")
                '?' -> escaped.append('.')
                '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> {
                    escaped.append('\\')
                    escaped.append(ch)
                }
                else -> escaped.append(ch)
            }
        }
        return Regex("^${escaped}$")
    }
}
