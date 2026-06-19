package cc.niaoer.nocall.ui.whitelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.normalizePhone
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(application: Application) : AndroidViewModel(application) {
    private val whitelistDao = (application as NoCallApplication).appContainer.whitelistDao

    private val _allEntries = whitelistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class UiState(
        val entries: List<WhitelistEntry> = emptyList(),
        val searchQuery: String = ""
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            _allEntries.collect { entries ->
                applyFilter(entries)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilter(_allEntries.value)
    }

    private fun applyFilter(allEntries: List<WhitelistEntry>) {
        val query = _uiState.value.searchQuery
        val filtered = if (query.isNotBlank()) {
            allEntries.filter {
                it.phoneNumber.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true)
            }
        } else {
            allEntries
        }
        _uiState.value = _uiState.value.copy(entries = filtered)
    }

    fun add(phoneNumber: String, note: String) {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            whitelistDao.insert(
                WhitelistEntry(
                    phoneNumber = phoneNumber.trim(),
                    normalizedNumber = normalized,
                    note = note.trim()
                )
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            whitelistDao.deleteById(id)
        }
    }
}
