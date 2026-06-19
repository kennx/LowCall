package cc.niaoer.nocall.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.model.CallLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val callLogDao = (application as NoCallApplication).appContainer.callLogDao

    data class UiState(
        val totalBlocked: Int = 0,
        val todayBlocked: Int = 0,
        val weekBlocked: Int = 0,
        val recentBlocked: List<CallLog> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val total = callLogDao.getTotalBlockedCount()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val today = callLogDao.getBlockedCountSince(todayStart)

            val weekStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val week = callLogDao.getBlockedCountSince(weekStart)

            val recent = callLogDao.getRecentBlocked(3)

            _uiState.value = UiState(
                totalBlocked = total,
                todayBlocked = today,
                weekBlocked = week,
                recentBlocked = recent
            )
        }
    }
}
