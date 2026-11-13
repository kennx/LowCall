package cc.niaoer.lowcall.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LowCallApplication).appContainer
    private val callLogDao = container.callLogDao

    data class UiState(
        val totalBlocked: Int = 0,
        val todayBlocked: Int = 0,
        val weekBlocked: Int = 0,
        val recentBlocked: List<CallLog> = emptyList()
    )

    private val _midnightStart = MutableStateFlow(getMidnightStart())
    private val recentBlocked = callLogDao.getRecentBlockedFlow(3)
        .map { logs ->
            logs.map { log ->
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = _midnightStart.flatMapLatest { midnight ->
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = midnight
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis

        combine(
            callLogDao.getTotalBlockedCountFlow(),
            callLogDao.getBlockedCountSinceFlow(midnight),
            callLogDao.getBlockedCountSinceFlow(weekStart),
            recentBlocked
        ) { total, today, week, recent ->
            UiState(
                totalBlocked = total,
                todayBlocked = today,
                weekBlocked = week,
                recentBlocked = recent
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun refreshMidnight() {
        _midnightStart.value = getMidnightStart()
    }

    private fun getMidnightStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
