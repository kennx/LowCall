package cc.niaoer.lowcall.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.formatLocationForDisplay
import cc.niaoer.lowcall.data.formatPhoneForDisplay
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import cc.niaoer.lowcall.data.normalizePhone
import cc.niaoer.lowcall.ui.common.ListGroup
import cc.niaoer.lowcall.ui.common.PhoneNumberText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    viewModel: CallHistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingBlacklist by remember { mutableStateOf<String?>(null) }

    val feedbackMessage = state.feedback?.let { current ->
        stringResource(
            when (current) {
                CallHistoryFeedback.BLACKLIST_ADDED -> R.string.blacklist_added
                CallHistoryFeedback.BLACKLIST_REACTIVATED -> R.string.blacklist_reactivated
                CallHistoryFeedback.BLACKLIST_EXISTS -> R.string.blacklist_exists
                CallHistoryFeedback.BLACKLIST_FAILED -> R.string.blacklist_failed
            }
        )
    }
    LaunchedEffect(state.feedback) {
        if (state.feedback != null && feedbackMessage != null) {
            snackbarHostState.showSnackbar(feedbackMessage)
            viewModel.consumeFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (state.logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_history),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = groupByDate(
                logs = state.logs,
                todayLabel = stringResource(R.string.today),
                yesterdayLabel = stringResource(R.string.yesterday)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp)
            ) {
                grouped.forEach { (dateLabel, dateLogs) ->
                    item(key = "header-$dateLabel") {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    item(key = "group-$dateLabel") {
                        ListGroup(modifier = Modifier.padding(bottom = 16.dp)) {
                            dateLogs.forEachIndexed { index, log ->
                                CallLogItem(
                                    log = log,
                                    isWhitelisted = normalizePhone(log.phoneNumber) in state.whitelistedNumbers,
                                    onAddToWhitelist = { viewModel.addToWhitelist(log.phoneNumber) },
                                    onAddToBlacklist = { pendingBlacklist = log.phoneNumber }
                                )
                                if (index < dateLogs.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingBlacklist?.let { phoneNumber ->
        val display = formatPhoneForDisplay(phoneNumber)
        val formatted = display.extension?.let { extension ->
            stringResource(R.string.phone_with_extension, display.number, extension)
        } ?: display.number
        AlertDialog(
            onDismissRequest = { pendingBlacklist = null },
            title = { Text(stringResource(R.string.blacklist_confirm_title)) },
            text = { Text(stringResource(R.string.blacklist_confirm_message, formatted)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addToBlacklist(phoneNumber)
                    pendingBlacklist = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlacklist = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun groupByDate(
    logs: List<CallLog>,
    todayLabel: String,
    yesterdayLabel: String
): List<Pair<String, List<CallLog>>> {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dateFormat = SimpleDateFormat("M月d日", Locale.getDefault())

    return logs.groupBy { log ->
        val logDay = Calendar.getInstance().apply {
            timeInMillis = log.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when {
            logDay.timeInMillis == today.timeInMillis -> todayLabel
            logDay.timeInMillis == yesterday.timeInMillis -> yesterdayLabel
            else -> dateFormat.format(Date(log.timestamp))
        }
    }.toList()
}

@Composable
private fun CallLogItem(
    log: CallLog,
    isWhitelisted: Boolean,
    onAddToWhitelist: () -> Unit,
    onAddToBlacklist: () -> Unit
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
    val isBlocked = log.action == CallAction.BLOCKED

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (isBlocked) R.drawable.ic_blocked_solid else R.drawable.ic_allowed_solid
                ),
                contentDescription = null,
                tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                PhoneNumberText(phoneNumber = log.phoneNumber)
                val attribution = listOfNotNull(
                    log.location?.let { formatLocationForDisplay(it) },
                    log.carrier
                ).filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (attribution.isNotBlank()) {
                    Text(
                        text = attribution,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (isBlocked) {
                        log.matchedRulePattern ?: stringResource(R.string.unknown_rule)
                    } else {
                        allowReason(log)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = if (isBlocked) onAddToWhitelist else onAddToBlacklist,
            enabled = if (isBlocked) !isWhitelisted else true,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 8.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isBlocked) R.drawable.ic_allowed_solid else R.drawable.ic_blocked_solid
                ),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (isBlocked) {
                        if (isWhitelisted) R.string.added_to_whitelist else R.string.add_to_whitelist
                    } else {
                        R.string.add_to_blacklist
                    }
                )
            )
        }
    }
}

@Composable
private fun allowReason(log: CallLog): String = when (log.allowReason) {
    "whitelist" -> stringResource(R.string.allowed_whitelist)
    "contacts" -> stringResource(R.string.allowed_contacts)
    "no_match" -> stringResource(R.string.allowed_no_match)
    else -> stringResource(R.string.allowed)
}
