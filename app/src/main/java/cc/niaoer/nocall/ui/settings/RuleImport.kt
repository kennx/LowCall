package cc.niaoer.nocall.ui.settings

import cc.niaoer.nocall.data.isValidRegex
import cc.niaoer.nocall.data.model.RuleType

data class ImportFilterResult(
    val accepted: List<ExportRule>,
    val rejected: Int
)

/**
 * Filters exported rules before persistence so invalid REGEX, unknown rule types,
 * and blank patterns cannot re-enter the database through the import path.
 *
 * Wildcard and Exact patterns are not regex-compiled by [cc.niaoer.nocall.data.RuleMatcher],
 * so they are accepted without a regex compile check; only REGEX is validated.
 * Patterns are trimmed to avoid trailing-whitespace silent-match failures.
 */
fun filterValidRules(rules: List<ExportRule>): ImportFilterResult {
    val accepted = mutableListOf<ExportRule>()
    var rejected = 0
    for (rule in rules) {
        val pattern = rule.pattern.trim()
        if (pattern.isBlank()) {
            rejected++
            continue
        }
        val ruleType = runCatching { RuleType.valueOf(rule.ruleType) }.getOrNull()
        if (ruleType == null) {
            rejected++
            continue
        }
        if (ruleType == RuleType.REGEX && !isValidRegex(pattern)) {
            rejected++
            continue
        }
        accepted.add(rule.copy(pattern = pattern))
    }
    return ImportFilterResult(accepted = accepted, rejected = rejected)
}
