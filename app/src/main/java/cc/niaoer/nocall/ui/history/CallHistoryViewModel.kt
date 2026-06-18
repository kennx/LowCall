package cc.niaoer.nocall.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.normalizePhone
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NoCallApplication).appContainer
    private val callLogDao = container.callLogDao
    private val whitelistDao = container.whitelistDao

    val logs: StateFlow<List<cc.niaoer.nocall.data.model.CallLog>> = callLogDao.getAllOrdered()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelistedNumbers: StateFlow<Set<String>> = whitelistDao.getAll()
        .map { entries -> entries.map { it.normalizedNumber }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun isWhitelisted(phoneNumber: String): Boolean =
        normalizePhone(phoneNumber) in whitelistedNumbers.value

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

    fun clearAll() {
        viewModelScope.launch {
            callLogDao.deleteAll()
        }
    }
}
