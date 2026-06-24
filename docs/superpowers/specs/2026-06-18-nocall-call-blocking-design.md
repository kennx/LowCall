# LowCall — Call Blocking App Design

<!-- Last updated: 2026-06-18 -->

## Overview

Personal-use Android call blocking app using `CallScreeningService` (API 24+). Users define blocking rules (exact number, wildcard, or regex); the system calls the service on incoming calls, matches rules, and silently rejects blocked calls with a notification. All blocked/allowed calls are logged and viewable in-app.

## Requirements

1. **Blocking Rules**: Add/edit/delete rules; enable/disable individual rules; support exact phone number, wildcard (`*`, `?`), and regex patterns.
2. **Rule Test**: Input a number to see which (if any) rule would match.
3. **Import/Export**: Export rules to JSON file; import rules from JSON file (via SAF).
4. **Call Logging**: Record every incoming call (blocked or allowed) with phone number, matched rule, action, and timestamp.
5. **Notifications**: Send a notification when a call is blocked; tapping opens the app.
6. **Material 3 UI**: All screens use `MaterialTheme` color scheme, typography, and shapes.

## Architecture

### Component Diagram

```
┌───────────────────────────────────────────────────────────┐
│                    LowCallApplication                      │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                   AppContainer                      │ │
│  │  AppDatabase (Room)                                 │ │
│  │    ├── BlockRuleDao                                 │ │
│  │    └── CallLogDao                                   │ │
│  │  RuleMatcher (pure Kotlin, testable)                │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
┌──────────────────┐    ┌──────────────────────────────┐
│   MainActivity   │    │ BlockingCallScreeningService  │
│   (NavHost)      │    │ (reads rules → matches →      │
│   5 Screens      │    │  reject + log + notify)       │
│   + ViewModels   │    │                               │
└──────────────────┘    └──────────────────────────────┘
```

### Dependency Flow (no DI framework)

- `LowCallApplication` creates `AppContainer` on `onCreate()`, holds it as a property.
- `AppContainer` initializes `AppDatabase`, exposes DAOs and `RuleMatcher`.
- ViewModels use `ViewModelProvider.Factory` that takes the `AppContainer` from Application.
- `BlockingCallScreeningService` accesses `(application as LowCallApplication).appContainer`.

### Gradle Dependencies to Add

| Dependency | Purpose |
|---|---|
| `androidx.room:room-runtime` + `room-ktx` + `room-compiler` (KSP) | Room database |
| `androidx.navigation:navigation-compose` | NavHost for screen routing |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` in Compose |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle()` |
| `com.google.code.gson:gson` | JSON serialization for import/export |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines (already transitive) |

## Data Layer

### Entity: `BlockRule`

```kotlin
@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "rule_type") val ruleType: RuleType,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

enum class RuleType { EXACT, WILDCARD, REGEX }
```

### Entity: `CallLog`

```kotlin
@Entity(tableName = "call_logs", indices = [Index(value = ["timestamp"])])
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: Long? = null,
    @ColumnInfo(name = "matched_rule_pattern") val matchedRulePattern: String? = null,
    @ColumnInfo(name = "action") val action: CallAction,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

enum class CallAction { BLOCKED, ALLOWED }
```

### DAOs

- `BlockRuleDao`: `Flow<List<BlockRule>> getAll()`, `suspend getById(id)`, `suspend getEnabled()`, `suspend insert/update/delete`, `suspend insertAll(list)`.
- `CallLogDao`: `Flow<List<CallLog>> getAllOrdered()`, `suspend insert`, `suspend deleteAll()`.

## Rule Matching Engine

```kotlin
class RuleMatcher {
    fun match(phoneNumber: String, rules: List<BlockRule>): BlockRule? {
        return rules.firstOrNull { rule ->
            rule.enabled && when (rule.ruleType) {
                RuleType.EXACT -> phoneNumber == rule.pattern
                RuleType.WILDCARD -> wildcardToRegex(rule.pattern).matches(phoneNumber)
                RuleType.REGEX -> Regex(rule.pattern).matches(phoneNumber)
            }
        }
    }

    private fun wildcardToRegex(pattern: String): Regex {
        val escaped = Regex.escape(pattern)
            .replace("\\*", ".*")
            .replace("\\?", ".")
        return Regex("^$escaped$")
    }
}
```

## Service: `BlockingCallScreeningService`

```kotlin
@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart ?: return
        val container = (application as LowCallApplication).appContainer
        val rules = runBlocking { container.blockRuleDao.getEnabled() }
        val matched = container.ruleMatcher.match(phoneNumber, rules)

        if (matched != null) {
            // Log the blocked call
            runBlocking {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        matchedRuleId = matched.id,
                        matchedRulePattern = matched.pattern,
                        action = CallAction.BLOCKED,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            // Show notification
            showBlockedCallNotification(phoneNumber, matched.description)
            // Reject the call
            respondToCall(details, CallResponse.reject(false, null))
        } else {
            // Log the allowed call
            runBlocking {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            // Allow the call (no explicit response needed — default is allow)
        }
    }
}
```

## UI Screens

### Navigation Routes

```
NavHost(startDestination = "rules")
  ├── "rules"           → RulesScreen
  ├── "rules/add"       → RuleEditScreen(ruleId = null)
  ├── "rules/{ruleId}"  → RuleEditScreen(ruleId = Long)
  ├── "rules/test"      → RuleTestScreen
  ├── "history"         → CallHistoryScreen
  └── "settings"        → SettingsScreen
```

### Screen: RulesScreen

- TopAppBar: "拦截规则" + test button + settings button
- LazyColumn of rule cards with toggle switch (enabled/disabled)
- Each card shows: pattern, type chip (精确/通配/正则), description
- Swipe-to-delete or long-press menu for edit/delete
- FAB → navigate to "rules/add"

### Screen: RuleEditScreen

- TextField for pattern
- SegmentedButton for type (精确 / 通配符 / 正则)
- TextField for description
- Switch for enabled
- Save button → insert/update DB → navigate back

### Screen: RuleTestScreen

- TextField for test phone number
- "测试" button → runs RuleMatcher against all enabled rules
- Result display: matched rule name or "不会被拦截"

### Screen: CallHistoryScreen

- TopAppBar: "通话记录"
- LazyColumn of call log entries
- Each entry: phone number, action chip (已拦截/已放行), timestamp, matched rule
- Sorted newest first

### Screen: SettingsScreen

- "导出规则" button → SAF CreateDocument → JSON
- "导入规则" button → SAF OpenDocument → JSON → insertAll
- "清空记录" button → confirmation dialog → deleteAll

### Navigation UI

- TopAppBar with back navigation (auto from NavHost)
- Bottom navigation bar OR drawer — decided: **no bottom bar**, use TopAppBar actions for cross-navigation. The rules screen is primary; history and settings accessed via icon buttons in TopAppBar.

## Permissions & Manifest

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application>
    <service
        android:name=".service.BlockingCallScreeningService"
        android:permission="android.permission.BIND_SCREENING_SERVICE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.telecom.CallScreeningService" />
        </intent-filter>
    </service>
</application>
```

- `POST_NOTIFICATIONS`: runtime permission request on API 33+ (first launch).
- `READ_PHONE_STATE`: required for CallScreeningService to receive caller info.
- User must manually enable the app as "来电筛选" / "Caller ID & spam" app in system settings. On first launch, direct user to settings via intent.

## Notification Channel

```kotlin
const val CHANNEL_ID = "call_blocking"
const val CHANNEL_NAME = "来电拦截"
// Importance: IMPORTANCE_HIGH (pops up as heads-up notification)
```

Notification content:
- Title: "拦截来电"
- Body: "号码: {phoneNumber} — 规则: {ruleDescription}"
- Tap action: PendingIntent to MainActivity
- Small icon: `ic_launcher` (or custom shield icon)

## File Structure

```
app/src/main/java/cc/niaoer/lowcall/
  MainActivity.kt                    — NavHost, permission request, first-launch guide
  LowCallApplication.kt               — Application subclass, AppContainer holder
  AppContainer.kt                    — Manual DI container
  data/
    db/
      AppDatabase.kt                 — Room database
      BlockRuleDao.kt                — Rule DAO
      CallLogDao.kt                  — Call log DAO
      Converters.kt                  — Type converters (enums)
    model/
      BlockRule.kt                   — Entity + RuleType enum
      CallLog.kt                     — Entity + CallAction enum
    RuleMatcher.kt                   — Pure Kotlin matching logic
  service/
    BlockingCallScreeningService.kt  — CallScreeningService impl
    NotificationHelper.kt            — Notification channel + builder
  ui/
    navigation/
      NavRoutes.kt                   — Route constants
    rules/
      RulesScreen.kt                 — Rule list with toggles
      RulesViewModel.kt
      RuleEditScreen.kt              — Add/Edit rule form
      RuleEditViewModel.kt
    test/
      RuleTestScreen.kt              — Rule test
      RuleTestViewModel.kt
    history/
      CallHistoryScreen.kt           — Call log list
      CallHistoryViewModel.kt
    settings/
      SettingsScreen.kt              — Import/Export/Clear
      SettingsViewModel.kt
    theme/
      Color.kt, Theme.kt, Type.kt    — (existing, may add minor tweaks)
```

## Testing Strategy

### Unit Tests (`app/src/test/`)
- `RuleMatcherTest`: test exact, wildcard, regex matching; edge cases (empty, special chars)
- `BlockRuleDao` / `CallLogDao` instrumented: verify insert/query/update (Room in-memory)

### Instrumented Tests (`app/src/androidTest/`)
- `RulesScreenTest`: verify rule list displays, FAB opens add screen, toggle works
- `CallHistoryScreenTest`: verify log entries display
- `RuleTestScreenTest`: verify match/no-match display

## Implementation Order

1. Add Room + Gson + Navigation dependencies to version catalog and build.gradle.kts
2. Create data model classes (BlockRule, CallLog, enums)
3. Create Room database, DAOs, type converters
4. Create RuleMatcher + unit tests
5. Create AppContainer, LowCallApplication
6. Create BlockingCallScreeningService + NotificationHelper
7. Update AndroidManifest.xml with permissions + service
8. Create navigation routes
9. Create ViewModels + Screens (Rules → RuleEdit → RuleTest → History → Settings)
10. Update MainActivity with NavHost, permission requests, setup guide
11. Add instrumented tests
12. Build, deploy via adb, verify
