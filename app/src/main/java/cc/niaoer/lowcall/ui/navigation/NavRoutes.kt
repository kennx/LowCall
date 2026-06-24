package cc.niaoer.lowcall.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val RULES = "rules"
    const val RULE_ADD = "rules/add"
    const val RULE_EDIT = "rules/{ruleId}"
    const val RULE_TEST = "rules/test"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val WHITELIST = "whitelist"

    fun ruleEdit(ruleId: Long): String = "rules/$ruleId"
}
