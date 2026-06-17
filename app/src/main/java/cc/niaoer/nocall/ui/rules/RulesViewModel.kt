package cc.niaoer.nocall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.model.BlockRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as NoCallApplication).appContainer.blockRuleDao

    val rules: StateFlow<List<BlockRule>> = blockRuleDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
