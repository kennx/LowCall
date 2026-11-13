package cc.niaoer.lowcall.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.CallLog
import cc.niaoer.lowcall.data.model.RuleType
import cc.niaoer.lowcall.data.model.WhitelistEntry
import cc.niaoer.lowcall.data.normalizePhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CallHistoryUiState(
    val logs: List<CallLog> = emptyList(),
    val whitelistedNumbers: Set<String> = emptySet(),
    val exactBlockPatterns: Set<String> = emptySet(),
    val feedback: CallHistoryFeedback? = null
)

enum class CallHistoryFeedback { BLACKLIST_ADDED, BLACKLIST_REACTIVATED, BLACKLIST_EXISTS, BLACKLIST_FAILED }

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LowCallApplication).appContainer
    private val callLogDao = container.callLogDao
    private val whitelistDao = container.whitelistDao
    private val blockRuleDao = container.blockRuleDao
    private val feedback = MutableStateFlow<CallHistoryFeedback?>(null)

    private val logs = callLogDao.getAllOrdered()
        .map { entries ->
            entries.map { log ->
                if (log.location != null) {
                    log
                } else {
                    val attribution = container.phoneAttribution.lookupAny(log.phoneNumber)
                    log.copy(
                        location = attribution?.let { "${it.province}${it.city}" },
                        carrier = attribution?.carrier?.ifBlank { null }
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO)

    val uiState: StateFlow<CallHistoryUiState> = combine(
        logs,
        whitelistDao.getAll(),
        blockRuleDao.getAll(),
        feedback
    ) { entries, whitelist, rules, currentFeedback ->
        CallHistoryUiState(
            logs = entries,
            whitelistedNumbers = whitelist.map { it.normalizedNumber }.toSet(),
            exactBlockPatterns = rules
                .filter { it.enabled && it.ruleType == RuleType.EXACT }
                .map { it.pattern }
                .toSet(),
            feedback = currentFeedback
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallHistoryUiState())

    fun addToWhitelist(phoneNumber: String) {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            whitelistDao.insert(
                WhitelistEntry(
                    phoneNumber = phoneNumber,
                    normalizedNumber = normalized,
                    note = ""
                )
            )
        }
    }

    fun addToBlacklist(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        viewModelScope.launch {
            try {
                val existing = blockRuleDao.findExact(phoneNumber)
                feedback.value = when {
                    existing == null -> {
                        blockRuleDao.insert(
                            BlockRule(
                                pattern = phoneNumber,
                                ruleType = RuleType.EXACT,
                                enabled = true
                            )
                        )
                        CallHistoryFeedback.BLACKLIST_ADDED
                    }
                    existing.enabled -> CallHistoryFeedback.BLACKLIST_EXISTS
                    else -> {
                        blockRuleDao.update(existing.copy(enabled = true))
                        CallHistoryFeedback.BLACKLIST_REACTIVATED
                    }
                }
            } catch (_: Exception) {
                feedback.value = CallHistoryFeedback.BLACKLIST_FAILED
            }
        }
    }

    fun consumeFeedback() {
        feedback.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            callLogDao.deleteAll()
        }
    }
}
