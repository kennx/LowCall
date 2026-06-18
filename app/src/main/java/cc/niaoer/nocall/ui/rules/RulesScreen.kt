package cc.niaoer.nocall.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.nocall.R
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onTestRule: () -> Unit,
    onWhitelist: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onTestRule) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = stringResource(R.string.rule_test)
                        )
                    }
                    IconButton(onClick = onWhitelist) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = stringResource(R.string.whitelist_title)
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
            }
        }
    ) { paddingValues ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_rules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleEnabled(rule) },
                        onClick = { onEditRule(rule.id) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: BlockRule,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuleTypeChip(rule.ruleType)
                    if (rule.description.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun RuleTypeChip(type: RuleType) {
    val label = when (type) {
        RuleType.EXACT -> "精确"
        RuleType.WILDCARD -> "通配"
        RuleType.REGEX -> "正则"
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}
