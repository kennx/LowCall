package cc.niaoer.nocall.ui.rules

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.nocall.R
import cc.niaoer.nocall.data.model.RuleType

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete() }) {
                            Icon(
                                Icons.Default.Delete,
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.pattern,
                onValueChange = viewModel::updatePattern,
                label = { Text(stringResource(R.string.pattern)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.patternError != null,
                supportingText = if (state.patternError != null) {
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
        RuleType.REGEX to "正则",
        RuleType.WILDCARD to "通配",
        RuleType.EXACT to "精确"
    )
    val shape = RoundedCornerShape(4.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (type, label) ->
                val isSelected = selected == type
                Surface(
                    onClick = { onSelect(type) },
                    modifier = Modifier.weight(1f),
                    shape = shape,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
