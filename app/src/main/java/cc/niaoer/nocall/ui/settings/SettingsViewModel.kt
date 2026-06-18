package cc.niaoer.nocall.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.R
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class ExportRule(
    val pattern: String,
    val ruleType: String,
    val enabled: Boolean,
    val description: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NoCallApplication).appContainer
    private val gson = Gson()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val settingsRepository = container.settingsRepository

    val notificationEnabled: StateFlow<Boolean> = settingsRepository.notificationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationEnabled(enabled)
        }
    }

    fun exportRules(uri: Uri) {
        viewModelScope.launch {
            try {
                val rules = container.blockRuleDao.getEnabledList()
                val exportList = rules.map { rule ->
                    ExportRule(
                        pattern = rule.pattern,
                        ruleType = rule.ruleType.name,
                        enabled = rule.enabled,
                        description = rule.description
                    )
                }
                val json = gson.toJson(exportList)
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                _isExporting.value = false
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                _isExporting.value = false
                Toast.makeText(getApplication(), e.message ?: "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importRules(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: return@launch

                val type = object : TypeToken<List<ExportRule>>() {}.type
                val importList: List<ExportRule> = gson.fromJson(json, type)

                val filtered = filterValidRules(importList)
                val rules = filtered.accepted.map { export ->
                    BlockRule(
                        pattern = export.pattern,
                        ruleType = RuleType.valueOf(export.ruleType),
                        enabled = export.enabled,
                        description = export.description
                    )
                }
                container.blockRuleDao.insertAll(rules)
                val msg = if (filtered.rejected > 0) {
                    context.getString(R.string.import_partial, rules.size, filtered.rejected)
                } else {
                    context.getString(R.string.import_success, rules.size)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.import_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun clearHistory(context: Context) {
        viewModelScope.launch {
            container.callLogDao.deleteAll()
            Toast.makeText(
                context,
                context.getString(R.string.history_deleted),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
