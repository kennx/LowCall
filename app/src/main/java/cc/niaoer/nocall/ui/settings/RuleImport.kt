package cc.niaoer.nocall.ui.settings

import cc.niaoer.nocall.data.isValidRegex
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType

data class ImportFilterResult(
    val accepted: List<BlockRule>,
    val rejected: Int
)

/**
 * Filters exported rules before persistence so invalid REGEX, unknown rule types,
 * and blank patterns cannot re-enter the database through the import path.
 *
 * Wildcard and Exact patterns are not regex-compiled by [cc.niaoer.nocall.data.match],
 * so they are accepted without a regex compile check; only REGEX is validated.
 * Patterns are trimmed to avoid trailing-whitespace silent-match failures.
 */
fun filterValidRules(rules: List<BlockRule>): ImportFilterResult {
    val accepted = mutableListOf<BlockRule>()
    var rejected = 0
    for (rule in rules) {
        val pattern = rule.pattern.trim()
        if (pattern.isBlank()) {
            rejected++
            continue
        }
        if (rule.ruleType == RuleType.REGEX && !isValidRegex(pattern)) {
            rejected++
            continue
        }
        accepted.add(rule.copy(pattern = pattern))
    }
    return ImportFilterResult(accepted = accepted, rejected = rejected)
}
