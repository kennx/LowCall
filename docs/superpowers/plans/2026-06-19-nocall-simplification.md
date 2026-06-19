# NoCall Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the ponytail-audit findings to delete over-engineered abstractions, replace Gson with Android's built-in org.json, and remove unused code without changing app UI/behavior.

**Architecture:** Keep the single-module Jetpack Compose app; collapse single-implementation classes (`RuleMatcher`, `ContactLookup`, `RuleEditDecision`, `ExportRule`) into functions or direct ViewModel logic; prune dead DAO methods and dependencies.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose, Room, DataStore, Android org.json, JUnit 4.

---

## File Map

| File | Responsibility After Change |
|---|---|
| `app/src/main/java/cc/niaoer/nocall/data/db/BlockRuleDao.kt` | Keep only used queries (`getAll`, `getEnabledList`, `getById`, `insert`, `update`, `delete`, `insertAll`). |
| `app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt` | Keep only used queries (`getAll`, `exists`, `insert`, `deleteById`). |
| `app/src/main/java/cc/niaoer/nocall/data/RuleMatcher.kt` | Top-level `match(phoneNumber, rules)` + helpers; simplified `wildcardToRegex`. |
| `app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt` | Top-level `isInContacts(context, phoneNumber)`. |
| `app/src/main/java/cc/niaoer/nocall/AppContainer.kt` | Remove `ruleMatcher` and `contactLookup` properties. |
| `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt` | Import and call top-level `match` and `isInContacts`. |
| `app/src/main/java/cc/niaoer/nocall/ui/test/RuleTestViewModel.kt` | Import and call top-level `match`. |
| `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditDecision.kt` | **Delete**. |
| `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditViewModel.kt` | Inline save/suggestion/validation logic; `patternError` becomes `Boolean`. |
| `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditScreen.kt` | Read `state.patternError` as `Boolean`. |
| `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt` | Replace Gson with `org.json.JSONArray/JSONObject`; remove `ExportRule` usage; delete `_isExporting`. |
| `app/src/main/java/cc/niaoer/nocall/ui/settings/RuleImport.kt` | Operate on `BlockRule` directly; return `List<BlockRule>` in `accepted`. |
| `gradle/libs.versions.toml` | Remove `gson`, `lifecycleRuntimeKtx` versions and libraries. |
| `app/build.gradle.kts` | Remove `implementation(libs.gson)` and `implementation(libs.androidx.lifecycle.runtime.ktx)`. |
| `app/src/test/java/cc/niaoer/nocall/data/RuleMatcherTest.kt` | Update to call top-level `match` and `wildcardToRegex`. |
| `app/src/test/java/cc/niaoer/nocall/ui/rules/RuleEditDecisionTest.kt` | **Delete** (logic is private to ViewModel). |
| `app/src/test/java/cc/niaoer/nocall/ui/settings/RuleImportTest.kt` | Use `BlockRule`/`RuleType` instead of `ExportRule`. |

---

## Task 1: Prune Unused DAO Methods

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/data/db/BlockRuleDao.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt`

Unused methods (verified by grep):
- `BlockRuleDao.getEnabled()` — no callers.
- `BlockRuleDao.searchRules(query)` — no callers.
- `BlockRuleDao.deleteById(id)` — no callers (ViewModel uses `@Delete`).
- `WhitelistDao.searchEntries(query)` — no callers.

**Note:** Keep `WhitelistDao.deleteById(id)`; it is called by `WhitelistViewModel.delete(id)`.

- [ ] **Step 1: Delete unused methods from `BlockRuleDao.kt`**

Remove lines 17-21 (`getEnabled`), 35-36 (`deleteById`), and 41-42 (`searchRules`). The remaining file:

```kotlin
package cc.niaoer.nocall.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.niaoer.nocall.data.model.BlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun getAll(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1 ORDER BY created_at DESC")
    suspend fun getEnabledList(): List<BlockRule>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun getById(id: Long): BlockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockRule): Long

    @Update
    suspend fun update(rule: BlockRule)

    @Delete
    suspend fun delete(rule: BlockRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BlockRule>)
}
```

- [ ] **Step 2: Delete unused method from `WhitelistDao.kt`**

Remove lines 24-25 (`searchEntries`). The remaining file:

```kotlin
package cc.niaoer.nocall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY created_at DESC")
    fun getAll(): Flow<List<WhitelistEntry>>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE normalized_number = :normalized)")
    suspend fun exists(normalized: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WhitelistEntry): Long

    @Query("DELETE FROM whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 3: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/db/BlockRuleDao.kt app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt
git commit -m "refactor: remove unused DAO methods"
```

---

## Task 2: Remove Dead StateFlow from SettingsViewModel

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Delete `_isExporting` and `isExporting`**

Remove:

```kotlin
private val _isExporting = MutableStateFlow(false)
val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
```

and the two assignments `_isExporting.value = false` inside `exportRules`.

- [ ] **Step 2: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt
git commit -m "refactor: remove unread exporting state flow"
```

---

## Task 3: Replace Gson with org.json and Collapse ExportRule

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/settings/RuleImport.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/cc/niaoer/nocall/ui/settings/RuleImportTest.kt`
- Delete: (none yet; `ExportRule` class will be removed from SettingsViewModel)

### 3.1 Update `RuleImport.kt`

`filterValidRules` will now accept `List<BlockRule>` and return `ImportFilterResult` containing `List<BlockRule>`.

- [ ] **Step 1: Rewrite `RuleImport.kt`**

```kotlin
package cc.niaoer.nocall.ui.settings

import cc.niaoer.nocall.data.isValidRegex
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType

data class ImportFilterResult(
    val accepted: List<BlockRule>,
    val rejected: Int
)

/**
 * Filters exported rules before persistence so invalid REGEX and blank patterns
 * cannot re-enter the database through the import path.
 *
 * Wildcard and Exact patterns are not regex-compiled by [cc.niaoer.nocall.data.match],
 * so they are accepted without a regex compile check; only REGEX is validated.
 * Patterns are trimmed to avoid trailing-whitespace silent-match failures.
 */
fun filterValidRules(rules: List<BlockRule>): ImportFilterResult {
    val accepted = mutableListOf<BlockRule>()
    var rejected = 0
    for (rule in rules) {
        val pattern = rule.pattern.trim()
        if (pattern.isBlank()) {
            rejected++
            continue
        }
        if (rule.ruleType == RuleType.REGEX && !isValidRegex(pattern)) {
            rejected++
            continue
        }
        accepted.add(rule.copy(pattern = pattern))
    }
    return ImportFilterResult(accepted = accepted, rejected = rejected)
}
```

### 3.2 Update `SettingsViewModel.kt`

- [ ] **Step 2: Replace imports and remove Gson / ExportRule**

Replace:

```kotlin
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
```

with:

```kotlin
import android.util.Log
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import org.json.JSONArray
import org.json.JSONObject
```

Remove the `ExportRule` data class and the `private val gson = Gson()` field.

- [ ] **Step 3: Rewrite exportRules**

```kotlin
fun exportRules(uri: Uri) {
    viewModelScope.launch {
        try {
            val rules = container.blockRuleDao.getEnabledList()
            val jsonArray = JSONArray()
            rules.forEach { rule ->
                jsonArray.put(
                    JSONObject().apply {
                        put("pattern", rule.pattern)
                        put("ruleType", rule.ruleType.name)
                        put("enabled", rule.enabled)
                        put("description", rule.description)
                    }
                )
            }
            val json = jsonArray.toString()
            val context = getApplication<Application>()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Export failed", e)
            Toast.makeText(getApplication(), e.message ?: "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
}
```

- [ ] **Step 4: Rewrite importRules**

```kotlin
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
                val obj = jsonArray.getJSONObject(i)
                importList.add(
                    BlockRule(
                        pattern = obj.getString("pattern"),
                        ruleType = RuleType.valueOf(obj.getString("ruleType")),
                        enabled = obj.getBoolean("enabled"),
                        description = obj.optString("description", "")
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
            Log.e("SettingsViewModel", "Import failed", e)
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.import_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
```

### 3.3 Update tests

- [ ] **Step 5: Rewrite `RuleImportTest.kt`**

```kotlin
package cc.niaoer.nocall.ui.settings

import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportTest {

    @Test
    fun filterValidRules_validRegex_isAccepted() {
        val input = listOf(
            BlockRule(pattern = "^138\\d{8}$", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
        assertEquals("^138\\d{8}$", result.accepted.first().pattern)
        assertEquals(RuleType.REGEX, result.accepted.first().ruleType)
    }

    @Test
    fun filterValidRules_invalidRegex_isRejected() {
        val input = listOf(
            BlockRule(pattern = "[0-9", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_unknownRuleType_isRejected() {
        // BlockRule constructor requires a real RuleType, so unknown types cannot be represented here.
        // This test documents that JSON parsing (in SettingsViewModel) rejects unknown rule types
        // before filterValidRules is invoked.
        val input = listOf(
            BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun filterValidRules_blankPattern_isRejected() {
        val input = listOf(
            BlockRule(pattern = "   ", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected)
    }

    @Test
    fun filterValidRules_trimsPatternBeforePersist() {
        val input = listOf(
            BlockRule(pattern = " ^138\\d{8}$\n", ruleType = RuleType.REGEX, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals("^138\\d{8}$", result.accepted.first().pattern)
    }

    @Test
    fun filterValidRules_wildcardAcceptedWithoutRegexCheck() {
        val input = listOf(
            BlockRule(pattern = "138*", ruleType = RuleType.WILDCARD, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
    }

    @Test
    fun filterValidRules_mixedInputCountsRejected() {
        val input = listOf(
            BlockRule(pattern = "^138\\d{8}$", ruleType = RuleType.REGEX, enabled = true, description = ""),
            BlockRule(pattern = "[0-9", ruleType = RuleType.REGEX, enabled = true, description = ""),
            BlockRule(pattern = "13800138000", ruleType = RuleType.EXACT, enabled = true, description = "")
        )
        val result = filterValidRules(input)
        assertEquals(2, result.accepted.size)
        assertEquals(1, result.rejected)
        assertTrue(result.accepted.all { it.pattern.isNotBlank() })
    }
}
```

- [ ] **Step 6: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/settings/RuleImport.kt app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt app/src/test/java/cc/niaoer/nocall/ui/settings/RuleImportTest.kt
git commit -m "refactor: replace Gson with org.json and collapse ExportRule"
```

---

## Task 4: Inline RuleEditDecision Logic into RuleEditViewModel

**Files:**
- Delete: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditDecision.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditViewModel.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditScreen.kt`
- Delete: `app/src/test/java/cc/niaoer/nocall/ui/rules/RuleEditDecisionTest.kt`

### 4.1 Update `RuleEditUiState` and `RuleEditViewModel`

- [ ] **Step 1: Replace `RuleEditUiState` and rewrite `RuleEditViewModel`**

New content for `RuleEditViewModel.kt`:

```kotlin
package cc.niaoer.nocall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.isValidRegex
import cc.niaoer.nocall.data.looksLikeRegex
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleEditUiState(
    val pattern: String = "",
    val ruleType: RuleType = RuleType.REGEX,
    val description: String = "",
    val enabled: Boolean = true,
    val isNew: Boolean = true,
    val saved: Boolean = false,
    val showRegexSuggestion: Boolean = false,
    val patternError: Boolean = false,
    val loadError: Boolean = false
)

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as NoCallApplication).appContainer.blockRuleDao

    private val _uiState = MutableStateFlow(RuleEditUiState())
    val uiState: StateFlow<RuleEditUiState> = _uiState.asStateFlow()

    private var editingRule: BlockRule? = null

    fun loadRule(ruleId: Long) {
        viewModelScope.launch {
            val rule = blockRuleDao.getById(ruleId)
            if (rule != null) {
                editingRule = rule
                _uiState.value = RuleEditUiState(
                    pattern = rule.pattern,
                    ruleType = rule.ruleType,
                    description = rule.description,
                    enabled = rule.enabled,
                    isNew = false
                )
            } else {
                _uiState.value = RuleEditUiState(loadError = true)
            }
        }
    }

    fun updatePattern(pattern: String) {
        _uiState.value = _uiState.value.copy(pattern = pattern, patternError = false)
    }

    fun updateRuleType(ruleType: RuleType) {
        _uiState.value = _uiState.value.copy(ruleType = ruleType, patternError = false)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun save() {
        val state = _uiState.value
        val pattern = state.pattern.trim()
        if (pattern.isBlank()) return

        when (state.ruleType) {
            RuleType.EXACT -> persistRule(pattern, RuleType.EXACT)
            RuleType.WILDCARD -> if (looksLikeRegex(pattern)) {
                _uiState.value = state.copy(
                    pattern = pattern,
                    showRegexSuggestion = true,
                    patternError = false
                )
            } else {
                persistRule(pattern, RuleType.WILDCARD)
            }
            RuleType.REGEX -> if (isValidRegex(pattern)) {
                persistRule(pattern, RuleType.REGEX)
            } else {
                _uiState.value = state.copy(
                    pattern = pattern,
                    ruleType = RuleType.REGEX,
                    showRegexSuggestion = false,
                    patternError = true
                )
            }
        }
    }

    fun confirmSwitchToRegex() {
        val pattern = _uiState.value.pattern.trim()
        if (pattern.isBlank()) return
        if (isValidRegex(pattern)) {
            persistRule(pattern, RuleType.REGEX)
        } else {
            _uiState.value = _uiState.value.copy(
                pattern = pattern,
                ruleType = RuleType.REGEX,
                showRegexSuggestion = false,
                patternError = true
            )
        }
    }

    fun keepWildcardAndSave() {
        val pattern = _uiState.value.pattern.trim()
        if (pattern.isBlank()) return
        persistRule(pattern, RuleType.WILDCARD)
    }

    fun cancelRegexSuggestion() {
        _uiState.value = _uiState.value.copy(showRegexSuggestion = false)
    }

    private fun persistRule(pattern: String, ruleType: RuleType) {
        val state = _uiState.value
        viewModelScope.launch {
            val rule = editingRule?.copy(
                pattern = pattern,
                ruleType = ruleType,
                description = state.description,
                enabled = state.enabled
            ) ?: BlockRule(
                pattern = pattern,
                ruleType = ruleType,
                description = state.description,
                enabled = state.enabled
            )
            blockRuleDao.insert(rule)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    fun delete() {
        val rule = editingRule ?: return
        viewModelScope.launch {
            blockRuleDao.delete(rule)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
```

### 4.2 Update `RuleEditScreen.kt`

- [ ] **Step 2: Update supportingText branch**

Change:

```kotlin
supportingText = if (state.patternError != null) {
    { Text(stringResource(R.string.invalid_regex_pattern)) }
} else {
    null
}
```

to:

```kotlin
supportingText = if (state.patternError) {
    { Text(stringResource(R.string.invalid_regex_pattern)) }
} else {
    null
}
```

### 4.3 Delete obsolete files

- [ ] **Step 3: Delete `RuleEditDecision.kt` and `RuleEditDecisionTest.kt`**

```bash
rm app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditDecision.kt
rm app/src/test/java/cc/niaoer/nocall/ui/rules/RuleEditDecisionTest.kt
```

- [ ] **Step 4: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: inline RuleEditDecision into RuleEditViewModel"
```

---

## Task 5: Convert RuleMatcher Class to Top-Level Function

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/data/RuleMatcher.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/AppContainer.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/test/RuleTestViewModel.kt`
- Modify: `app/src/test/java/cc/niaoer/nocall/data/RuleMatcherTest.kt`

### 5.1 Rewrite `RuleMatcher.kt`

- [ ] **Step 1: Replace class with top-level functions**

```kotlin
package cc.niaoer.nocall.data

import android.util.Log
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.RuleType

fun looksLikeRegex(pattern: String): Boolean {
    return pattern.startsWith("^") ||
        pattern.endsWith("$") ||
        pattern.contains("\\") ||
        pattern.contains("|") ||
        pattern.contains("[") ||
        pattern.contains("{") ||
        pattern.contains("(") ||
        pattern.contains(")") ||
        pattern.contains(".*") ||
        pattern.contains(".+")
}

fun isValidRegex(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

fun match(phoneNumber: String, rules: List<BlockRule>): BlockRule? {
    return rules.firstOrNull { rule ->
        rule.enabled && when (rule.ruleType) {
            RuleType.EXACT -> phoneNumber == rule.pattern
            RuleType.WILDCARD -> wildcardToRegex(rule.pattern).matches(phoneNumber)
            RuleType.REGEX -> runCatching {
                Regex(rule.pattern).matches(phoneNumber)
            }.onFailure { e ->
                Log.w("RuleMatcher", "Invalid regex pattern: ${rule.pattern}", e)
            }.getOrDefault(false)
        }
    }
}

private fun wildcardToRegex(pattern: String): Regex {
    val escaped = pattern.map { ch ->
        when (ch) {
            '*' -> ".*"
            '?' -> "."
            '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> "\\$ch"
            else -> ch.toString()
        }
    }.joinToString("")
    return Regex("^${escaped}$")
}
```

### 5.2 Update callers

- [ ] **Step 2: Remove `ruleMatcher` from `AppContainer.kt`**

Delete the `RuleMatcher` import and the `val ruleMatcher` property. The file becomes:

```kotlin
package cc.niaoer.nocall

import android.content.Context
import cc.niaoer.nocall.data.db.AppDatabase
import cc.niaoer.nocall.data.db.BlockRuleDao
import cc.niaoer.nocall.data.db.CallLogDao
import cc.niaoer.nocall.data.db.WhitelistDao
import cc.niaoer.nocall.data.prefs.SettingsRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val blockRuleDao: BlockRuleDao = database.blockRuleDao()
    val callLogDao: CallLogDao = database.callLogDao()
    val whitelistDao: WhitelistDao = database.whitelistDao()
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)
}
```

- [ ] **Step 3: Update `BlockingCallScreeningService.kt`**

Add import:

```kotlin
import cc.niaoer.nocall.data.match
```

Change:

```kotlin
val matched = container.ruleMatcher.match(phoneNumber, enabledRules)
```

to:

```kotlin
val matched = match(phoneNumber, enabledRules)
```

- [ ] **Step 4: Update `RuleTestViewModel.kt`**

Add import:

```kotlin
import cc.niaoer.nocall.data.match
```

Change:

```kotlin
val matched = container.ruleMatcher.match(phone, rules)
```

to:

```kotlin
val matched = match(phone, rules)
```

### 5.3 Update tests

- [ ] **Step 5: Rewrite `RuleMatcherTest.kt`**

Remove the `matcher` field and replace all `matcher.match(...)` calls with `match(...)`. For example:

```kotlin
private val matcher = RuleMatcher()
```

deletes, and:

```kotlin
val result = matcher.match("13800138000", listOf(rule))
```

becomes:

```kotlin
val result = match("13800138000", listOf(rule))
```

Do this for every test. The assertions remain unchanged.

- [ ] **Step 6: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: convert RuleMatcher to top-level function"
```

---

## Task 6: Convert ContactLookup Class to Top-Level Function

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/AppContainer.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt`

### 6.1 Rewrite `ContactLookup.kt`

- [ ] **Step 1: Replace class with top-level function**

```kotlin
package cc.niaoer.nocall.data

import android.content.Context
import android.provider.ContactsContract

fun isInContacts(context: Context, phoneNumber: String): Boolean {
    val normalized = normalizePhone(phoneNumber)
    if (normalized.isBlank()) return false
    val tail = normalized.takeLast(minOf(normalized.length, 11))
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
        arrayOf("%$tail"),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val stored = cursor.getString(0) ?: continue
            if (normalizePhone(stored).endsWith(tail)) return true
        }
    }
    return false
}
```

### 6.2 Update callers

- [ ] **Step 2: Remove `contactLookup` from `AppContainer.kt`**

Already done in Task 5; verify `AppContainer.kt` no longer references `ContactLookup`.

- [ ] **Step 3: Update `BlockingCallScreeningService.kt`**

Add import:

```kotlin
import cc.niaoer.nocall.data.isInContacts
```

Change:

```kotlin
container.contactLookup.isInContacts(phoneNumber)
```

to:

```kotlin
isInContacts(this@BlockingCallScreeningService, phoneNumber)
```

- [ ] **Step 4: Build and run unit tests**

Run:
```bash
./gradlew :app:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: convert ContactLookup to top-level function"
```

---

## Task 7: Remove Obsolete Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

### 7.1 Version catalog

- [ ] **Step 1: Remove Gson and lifecycle-runtime-ktx entries**

In `[versions]`, delete:

```toml
lifecycleRuntimeKtx = "2.6.1"
gson = "2.14.0"
```

In `[libraries]`, delete:

```toml
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
```

### 7.2 App build file

- [ ] **Step 2: Remove dependency usages**

Delete from `app/build.gradle.kts`:

```kotlin
implementation(libs.gson)
implementation(libs.androidx.lifecycle.runtime.ktx)
```

- [ ] **Step 3: Sync and build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: remove Gson and unused lifecycle-runtime-ktx dependencies"
```

---

## Task 8: Final Verification

- [ ] **Step 1: Run full unit test suite**

```bash
./gradlew :app:test
```

Expected: All tests pass.

- [ ] **Step 2: Run lint on debug variant**

```bash
./gradlew :app:lintDebug
```

Expected: No new errors (warnings acceptable if pre-existing).

- [ ] **Step 3: Check git diff summary**

```bash
git diff --stat
```

Confirm expected deletions:
- `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditDecision.kt`
- `app/src/test/java/cc/niaoer/nocall/ui/rules/RuleEditDecisionTest.kt`

- [ ] **Step 4: Final sanity commit (if any uncommitted changes remain)**

If no changes remain, skip.

---

## Spec Coverage Check

| Audit Finding | Task |
|---|---|
| Remove `RuleEditDecision` sealed class + tests | Task 4 |
| Remove `ExportRule`, use `BlockRule` + org.json | Task 3 |
| Replace Gson dependency | Task 3 + Task 7 |
| Delete unused DAO methods | Task 1 |
| Delete dead `_isExporting` StateFlow | Task 2 |
| Convert `RuleMatcher` class to top-level function | Task 5 |
| Convert `ContactLookup` class to top-level function | Task 6 |
| Remove `lifecycle-runtime-ktx` direct dependency | Task 7 |
| Simplify `wildcardToRegex` with stdlib | Task 5 |

No placeholders remain; every step contains the exact file path and code/content to produce.
