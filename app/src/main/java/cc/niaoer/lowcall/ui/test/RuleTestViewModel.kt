package cc.niaoer.lowcall.ui.test

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.match
import cc.niaoer.lowcall.data.model.BlockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleTestUiState(
    val phoneNumber: String = "",
    val matchedRule: BlockRule? = null,
    val tested: Boolean = false
)

class RuleTestViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LowCallApplication).appContainer

    private val _uiState = MutableStateFlow(RuleTestUiState())
    val uiState: StateFlow<RuleTestUiState> = _uiState.asStateFlow()

    fun updatePhoneNumber(number: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = number, tested = false)
    }

    fun test() {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank()) return
        viewModelScope.launch {
            val rules = container.blockRuleDao.getEnabledList()
            val matched = match(phone, rules)
            _uiState.value = _uiState.value.copy(matchedRule = matched, tested = true)
        }
    }
}
