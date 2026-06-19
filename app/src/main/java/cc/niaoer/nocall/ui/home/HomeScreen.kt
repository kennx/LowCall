package cc.niaoer.nocall.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.nocall.R
import cc.niaoer.nocall.data.model.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToAddRule: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddRule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                HeroSection(
                    totalBlocked = uiState.totalBlocked,
                    todayBlocked = uiState.todayBlocked,
                    weekBlocked = uiState.weekBlocked
                )
            }

            item {
                QuickGrid(
                    onNavigateToRules = onNavigateToRules,
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToTest = onNavigateToTest,
                    onNavigateToAddRule = onNavigateToAddRule
                )
            }

            item {
                Text(
                    text = stringResource(R.string.recent_blocked),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(uiState.recentBlocked, key = { it.id }) { log ->
                RecentBlockedItem(log = log)
            }
        }
    }
}

@Composable
private fun HeroSection(
    totalBlocked: Int,
    todayBlocked: Int,
    weekBlocked: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = totalBlocked.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_stat_suffix),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_today_week, todayBlocked, weekBlocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickGrid(
    onNavigateToRules: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTest: () -> Unit,
    onNavigateToAddRule: () -> Unit
) {
    val items = listOf(
        Triple(stringResource(R.string.quick_rules), stringResource(R.string.quick_rules_hint)) { onNavigateToRules() },
        Triple(stringResource(R.string.quick_history), stringResource(R.string.quick_history_hint)) { onNavigateToHistory() },
        Triple(stringResource(R.string.quick_test), stringResource(R.string.quick_test_hint)) { onNavigateToTest() },
        Triple(stringResource(R.string.quick_add), stringResource(R.string.quick_add_hint)) { onNavigateToAddRule() }
    )

    val icons = listOf(
        Icons.Default.Rule,
        Icons.Default.History,
        Icons.Default.Science,
        Icons.Default.Add
    )

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    val (label, hint, onClick) = items[index]
                    QuickCard(
                        label = label,
                        hint = hint,
                        icon = icons[index],
                        onClick = onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentBlockedItem(log: CallLog) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.matchedRulePattern ?: "未知规则"} · ${dateFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
