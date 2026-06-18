package cc.niaoer.nocall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleEditUiState(
    val pattern: String = "",
    val ruleType: RuleType = RuleType.EXACT,
    val description: String = "",
    val enabled: Boolean = true,
    val isNew: Boolean = true,
    val saved: Boolean = false,
    val showRegexSuggestion: Boolean = false,
    val patternError: RulePatternError? = null
)

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as NoCallApplication).appContainer.blockRuleDao

    private val _uiState = MutableStateFlow(RuleEditUiState())
    val uiState: StateFlow<RuleEditUiState> = _uiState.asStateFlow()

    private var editingRule: BlockRule? = null

    fun loadRule(ruleId: Long) {
        viewModelScope.launch {
            val rule = blockRuleDao.getById(ruleId)
            if (rule != null) {
                editingRule = rule
                _uiState.value = RuleEditUiState(
                    pattern = rule.pattern,
                    ruleType = rule.ruleType,
                    description = rule.description,
                    enabled = rule.enabled,
                    isNew = false
                )
            }
        }
    }

    fun updatePattern(pattern: String) {
        _uiState.value = _uiState.value.copy(pattern = pattern, patternError = null)
    }

    fun updateRuleType(ruleType: RuleType) {
        _uiState.value = _uiState.value.copy(ruleType = ruleType, patternError = null)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun save() {
        val state = _uiState.value
        applyDecision(decideRuleSave(pattern = state.pattern, ruleType = state.ruleType))
    }

    fun confirmSwitchToRegex() {
        applyDecision(
            decideRegexSuggestion(
                pattern = _uiState.value.pattern,
                action = RegexSuggestionAction.SWITCH_TO_REGEX
            )
        )
    }

    fun keepWildcardAndSave() {
        applyDecision(
            decideRegexSuggestion(
                pattern = _uiState.value.pattern,
                action = RegexSuggestionAction.KEEP_WILDCARD
            )
        )
    }

    fun cancelRegexSuggestion() {
        applyDecision(
            decideRegexSuggestion(
                pattern = _uiState.value.pattern,
                action = RegexSuggestionAction.CANCEL
            )
        )
    }

    private fun applyDecision(decision: RuleEditDecision) {
        when (decision) {
            RuleEditDecision.NoOp -> Unit
            RuleEditDecision.CancelSuggestion -> {
                _uiState.value = _uiState.value.copy(showRegexSuggestion = false)
            }
            is RuleEditDecision.ShowRegexSuggestion -> {
                _uiState.value = _uiState.value.copy(
                    pattern = decision.pattern,
                    showRegexSuggestion = true,
                    patternError = null
                )
            }
            is RuleEditDecision.InvalidRegex -> {
                _uiState.value = _uiState.value.copy(
                    pattern = decision.pattern,
                    ruleType = RuleType.REGEX,
                    showRegexSuggestion = false,
                    patternError = RulePatternError.INVALID_REGEX
                )
            }
            is RuleEditDecision.Persist -> {
                _uiState.value = _uiState.value.copy(
                    pattern = decision.pattern,
                    ruleType = decision.ruleType,
                    showRegexSuggestion = false,
                    patternError = null
                )
                persistRule(pattern = decision.pattern, ruleType = decision.ruleType)
            }
        }
    }

    private fun persistRule(pattern: String, ruleType: RuleType) {
        val state = _uiState.value
        if (pattern.isBlank()) return
        viewModelScope.launch {
            val rule = editingRule?.copy(
                pattern = pattern,
                ruleType = ruleType,
                description = state.description,
                enabled = state.enabled
            ) ?: BlockRule(
                pattern = pattern,
                ruleType = ruleType,
                description = state.description,
                enabled = state.enabled
            )
            blockRuleDao.insert(rule)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    fun delete() {
        val rule = editingRule ?: return
        viewModelScope.launch {
            blockRuleDao.delete(rule)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
