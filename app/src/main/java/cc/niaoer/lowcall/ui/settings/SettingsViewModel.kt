package cc.niaoer.lowcall.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LowCallApplication).appContainer

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
                val jsonArray = JSONArray()
                for (rule in rules) {
                    val jsonObject = JSONObject()
                    jsonObject.put("pattern", rule.pattern)
                    jsonObject.put("ruleType", rule.ruleType.name)
                    jsonObject.put("enabled", rule.enabled)
                    jsonObject.put("description", rule.description)
                    jsonArray.put(jsonObject)
                }
                val json = jsonArray.toString()
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "导出失败", e)
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

                val jsonArray = JSONArray(json)
                val importList = mutableListOf<BlockRule>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val ruleType = RuleType.valueOf(jsonObject.getString("ruleType"))
                    importList.add(
                        BlockRule(
                            pattern = jsonObject.getString("pattern"),
                            ruleType = ruleType,
                            enabled = jsonObject.getBoolean("enabled"),
                            description = jsonObject.optString("description", "")
                        )
                    )
                }

                val filtered = filterValidRules(importList)
                container.blockRuleDao.insertAll(filtered.accepted)
                val msg = if (filtered.rejected > 0) {
                    context.getString(R.string.import_partial, filtered.accepted.size, filtered.rejected)
                } else {
                    context.getString(R.string.import_success, filtered.accepted.size)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "导入失败", e)
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
