package cc.niaoer.lowcall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.isValidRegex
import cc.niaoer.lowcall.data.looksLikeRegex
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleEditUiState(
    val pattern: String = "",
    val ruleType: RuleType = RuleType.REGEX,
    val description: String = "",
    val enabled: Boolean = true,
    val isNew: Boolean = true,
    val saved: Boolean = false,
    val showRegexSuggestion: Boolean = false,
    val patternError: Boolean = false,
    val loadError: Boolean = false
)

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as LowCallApplication).appContainer.blockRuleDao

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
            } else {
                _uiState.value = RuleEditUiState(loadError = true)
            }
        }
    }

    fun updatePattern(pattern: String) {
        _uiState.value = _uiState.value.copy(pattern = pattern, patternError = false)
    }

    fun updateRuleType(ruleType: RuleType) {
        _uiState.value = _uiState.value.copy(ruleType = ruleType, patternError = false)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun save() {
        val trimmedPattern = _uiState.value.pattern.trim()
        if (trimmedPattern.isBlank()) return

        when (_uiState.value.ruleType) {
            RuleType.EXACT -> persistRule(trimmedPattern, RuleType.EXACT)
            RuleType.WILDCARD -> if (looksLikeRegex(trimmedPattern)) {
                _uiState.value = _uiState.value.copy(
                    pattern = trimmedPattern,
                    showRegexSuggestion = true,
                    patternError = false
                )
            } else {
                persistRule(trimmedPattern, RuleType.WILDCARD)
            }
            RuleType.REGEX -> if (isValidRegex(trimmedPattern)) {
                persistRule(trimmedPattern, RuleType.REGEX)
            } else {
                _uiState.value = _uiState.value.copy(
                    pattern = trimmedPattern,
                    ruleType = RuleType.REGEX,
                    showRegexSuggestion = false,
                    patternError = true
                )
            }
        }
    }

    fun confirmSwitchToRegex() {
        val trimmedPattern = _uiState.value.pattern.trim()
        if (isValidRegex(trimmedPattern)) {
            persistRule(trimmedPattern, RuleType.REGEX)
        } else {
            _uiState.value = _uiState.value.copy(
                pattern = trimmedPattern,
                ruleType = RuleType.REGEX,
                showRegexSuggestion = false,
                patternError = true
            )
        }
    }

    fun keepWildcardAndSave() {
        val trimmedPattern = _uiState.value.pattern.trim()
        persistRule(trimmedPattern, RuleType.WILDCARD)
    }

    fun cancelRegexSuggestion() {
        _uiState.value = _uiState.value.copy(showRegexSuggestion = false)
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
            _uiState.value = _uiState.value.copy(
                pattern = pattern,
                ruleType = ruleType,
                showRegexSuggestion = false,
                patternError = false,
                saved = true
            )
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
