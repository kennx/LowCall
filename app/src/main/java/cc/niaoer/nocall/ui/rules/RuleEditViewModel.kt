package cc.niaoer.nocall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.looksLikeRegex
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
    val showRegexSuggestion: Boolean = false
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
        _uiState.value = _uiState.value.copy(pattern = pattern)
    }

    fun updateRuleType(ruleType: RuleType) {
        _uiState.value = _uiState.value.copy(ruleType = ruleType)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun save() {
        val state = _uiState.value
        val trimmedPattern = state.pattern.trim()
        if (trimmedPattern.isBlank()) return
        if (state.ruleType == RuleType.WILDCARD && looksLikeRegex(trimmedPattern)) {
            _uiState.value = state.copy(pattern = trimmedPattern, showRegexSuggestion = true)
            return
        }
        persistRule(trimmedPattern)
    }

    fun confirmSwitchToRegex() {
        val state = _uiState.value.copy(ruleType = RuleType.REGEX, showRegexSuggestion = false)
        _uiState.value = state
        persistRule(state.pattern.trim())
    }

    fun dismissRegexSuggestion() {
        _uiState.value = _uiState.value.copy(showRegexSuggestion = false)
        persistRule(_uiState.value.pattern.trim())
    }

    private fun persistRule(pattern: String) {
        val state = _uiState.value
        if (pattern.isBlank()) return
        viewModelScope.launch {
            val rule = editingRule?.copy(
                pattern = pattern,
                ruleType = state.ruleType,
                description = state.description,
                enabled = state.enabled
            ) ?: BlockRule(
                pattern = pattern,
                ruleType = state.ruleType,
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
