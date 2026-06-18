package cc.niaoer.nocall.ui.whitelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.normalizePhone
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(application: Application) : AndroidViewModel(application) {
    private val whitelistDao = (application as NoCallApplication).appContainer.whitelistDao

    val entries: StateFlow<List<WhitelistEntry>> = whitelistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
