package cc.niaoer.lowcall.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    ruleId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: RuleEditViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(ruleId) {
        if (ruleId != null && ruleId > 0) {
            viewModel.loadRule(ruleId)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.loadError) {
        if (state.loadError) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.add_rule)
                        else stringResource(R.string.edit_rule)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back_solid),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_solid),
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.pattern,
                onValueChange = viewModel::updatePattern,
                label = { Text(stringResource(R.string.pattern)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.patternError,
                supportingText = if (state.patternError) {
                    { Text(stringResource(R.string.invalid_regex_pattern)) }
                } else {
                    null
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.rule_type),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            RuleTypeSelector(
                selected = state.ruleType,
                onSelect = viewModel::updateRuleType
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.enabled),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::updateEnabled
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.pattern.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }

            if (state.showRegexSuggestion) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelRegexSuggestion() },
                    title = { Text(stringResource(R.string.regex_suggestion_title)) },
                    text = { Text(stringResource(R.string.regex_suggestion_message)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmSwitchToRegex() }) {
                            Text(stringResource(R.string.regex_suggestion_switch))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.keepWildcardAndSave() }) {
                            Text(stringResource(R.string.regex_suggestion_keep))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RuleTypeSelector(
    selected: RuleType,
    onSelect: (RuleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        RuleType.REGEX to stringResource(R.string.rule_type_regex),
        RuleType.WILDCARD to stringResource(R.string.rule_type_wildcard),
        RuleType.EXACT to stringResource(R.string.rule_type_exact)
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(text = label, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}
