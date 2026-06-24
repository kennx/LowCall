package cc.niaoer.lowcall.data

import android.util.Log
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType

fun looksLikeRegex(pattern: String): Boolean {
    return pattern.startsWith("^") ||
        pattern.endsWith("$") ||
        pattern.contains("\\") ||
        pattern.contains("|") ||
        pattern.contains("[") ||
        pattern.contains("{") ||
        pattern.contains("(") ||
        pattern.contains(")") ||
        pattern.contains(".*") ||
        pattern.contains(".+")
}

fun isValidRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

fun match(phoneNumber: String, rules: List<BlockRule>): BlockRule? =
    rules.firstOrNull { rule ->
        rule.enabled && when (rule.ruleType) {
            RuleType.EXACT -> phoneNumber == rule.pattern
            RuleType.WILDCARD -> wildcardToRegex(rule.pattern).matches(phoneNumber)
            RuleType.REGEX -> runCatching {
                Regex(rule.pattern).matches(phoneNumber)
            }.onFailure { e ->
                Log.w("RuleMatcher", "Invalid regex pattern: ${rule.pattern}", e)
            }.getOrDefault(false)
        }
    }

private fun wildcardToRegex(pattern: String): Regex =
    Regex("^${pattern.map { ch ->
        when (ch) {
            '*' -> ".*"
            '?' -> "."
            in ".+()[]{}^$|\\" -> "\\$ch"
            else -> ch.toString()
        }
    }.joinToString("")}$")
