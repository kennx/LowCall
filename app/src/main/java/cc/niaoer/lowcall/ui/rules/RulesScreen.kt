package cc.niaoer.lowcall.ui.rules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import cc.niaoer.lowcall.ui.common.ListGroup

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearching) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text(stringResource(R.string.rules_search_placeholder)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.rules_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            painter = painterResource(
                                if (uiState.isSearching) R.drawable.ic_close_solid
                                else R.drawable.ic_search_solid
                            ),
                            contentDescription = stringResource(
                                if (uiState.isSearching) R.string.close else R.string.search
                            )
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_solid),
                    contentDescription = stringResource(R.string.add_rule)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val tabs = listOf(null to stringResource(R.string.rules_all)) +
                RuleType.entries.map { it to typeLabel(it) }
            val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }

            PrimaryScrollableTabRow(
                selectedTabIndex = if (selectedIndex >= 0) selectedIndex else 0,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, (type, label) ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.selectTab(type) },
                        text = { Text(label) }
                    )
                }
            }

            if (uiState.rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 88.dp
                    )
                ) {
                    item {
                        ListGroup {
                            uiState.rules.forEachIndexed { index, rule ->
                                RuleRow(
                                    rule = rule,
                                    onToggle = { viewModel.toggleEnabled(rule) },
                                    onClick = { onEditRule(rule.id) }
                                )
                                if (index < uiState.rules.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
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
}

@Composable
private fun RuleRow(
    rule: BlockRule,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = rule.description.ifBlank { rule.pattern },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column {
                    if (rule.description.isNotBlank()) {
                        Text(text = rule.pattern, maxLines = 2)
                    }
                    Text(text = typeLabel(rule.ruleType))
                }
            },
            trailingContent = {
                Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun typeLabel(type: RuleType): String = stringResource(
    when (type) {
        RuleType.EXACT -> R.string.rule_type_exact
        RuleType.WILDCARD -> R.string.rule_type_wildcard
        RuleType.REGEX -> R.string.rule_type_regex
    }
)
