package cc.niaoer.nocall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as NoCallApplication).appContainer.blockRuleDao

    private val _allRules = blockRuleDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class UiState(
        val rules: List<BlockRule> = emptyList(),
        val selectedTab: RuleType? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            _allRules.collect { rules ->
                applyFilter(rules)
            }
        }
    }

    fun selectTab(type: RuleType?) {
        _uiState.value = _uiState.value.copy(selectedTab = type)
        applyFilter(_allRules.value)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilter(_allRules.value)
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = !_uiState.value.isSearching,
            searchQuery = ""
        )
        applyFilter(_allRules.value)
    }

    private fun applyFilter(allRules: List<BlockRule>) {
        val query = _uiState.value.searchQuery
        val tab = _uiState.value.selectedTab

        val filtered = if (query.isNotBlank()) {
            allRules.filter {
                it.pattern.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        } else {
            allRules
        }

        val tabFiltered = if (tab != null) {
            filtered.filter { it.ruleType == tab }
        } else {
            filtered
        }

        _uiState.value = _uiState.value.copy(rules = tabFiltered)
    }

    fun toggleEnabled(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.update(rule.copy(enabled = !rule.enabled))
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.delete(rule)
        }
    }
}
