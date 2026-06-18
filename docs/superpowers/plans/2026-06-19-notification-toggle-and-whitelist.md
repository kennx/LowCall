# Notification Toggle and Whitelist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Settings notification toggle (default on) that gates only the system notification while blocking always occurs, and add a whitelist (Room table + live contacts lookup) with absolute priority over block rules, plus a one-tap "add to whitelist" action on blocked history cards and a Whitelist management screen.

**Architecture:** New `WhitelistEntry` Room entity with DB v1→v2 migration; `SettingsRepository` over DataStore Preferences for the boolean toggle; `ContactLookup` over `ContactsContract.CommonDataKinds.Phone.NUMBER` with a pure `normalizePhone` helper; `BlockingCallScreeningService` checks whitelist (table + contacts) before rules and gates the notification on the preference. UI: Settings `Switch`, history card "加入白名单" button, new `WhitelistScreen` + `WhitelistViewModel`, `RulesScreen` top-bar entry, READ_CONTACTS requested on first Whitelist entry.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material3, Room 2.8.4 (migration), DataStore Preferences 1.1.7, ContactsContract, JUnit 4, Navigation Compose, Gradle 9.4.1.

**Spec:** `docs/superpowers/specs/2026-06-19-notification-toggle-and-whitelist-design.md`

**Conventions (from AGENTS.md):** versions only in `gradle/libs.versions.toml`; no `!!`; named args for ambiguous literals; `Modifier` last with `= Modifier`; strings in `res/values/strings.xml`; `collectAsStateWithLifecycle()`; LazyList `key`; minSdk 24 (version-gate newer APIs); Java 11; target <500 lines/file. Run `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>` for Gradle.

---

### Task 1: Phone normalizer (pure, TDD)

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/data/PhoneNormalizer.kt`
- Create: `app/src/test/java/cc/niaoer/nocall/data/PhoneNormalizerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/cc/niaoer/nocall/data/PhoneNormalizerTest.kt`:

```kotlin
package cc.niaoer.nocall.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizerTest {
    @Test
    fun normalize_stripsPlusSpacesHyphensParens() {
        assertEquals("13800138000", normalizePhone("+86 138-0013-8000"))
    }

    @Test
    fun normalize_keepsOnlyDigits() {
        assertEquals("0085245995523", normalizePhone("00852-4599 5523"))
    }

    @Test
    fun normalize_emptyForNoDigits() {
        assertEquals("", normalizePhone("  -  "))
    }

    @Test
    fun normalize_pureDigitsUnchanged() {
        assertEquals("13800138000", normalizePhone("13800138000"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'cc.niaoer.nocall.data.PhoneNormalizerTest'`
Expected: FAIL — `Unresolved reference: normalizePhone`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/cc/niaoer/nocall/data/PhoneNormalizer.kt`:

```kotlin
package cc.niaoer.nocall.data

fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'cc.niaoer.nocall.data.PhoneNormalizerTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/PhoneNormalizer.kt app/src/test/java/cc/niaoer/nocall/data/PhoneNormalizerTest.kt
git commit -m "feat: add pure normalizePhone helper"
```

---

### Task 2: DataStore Preferences dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library to the catalog**

Edit `gradle/libs.versions.toml`. In `[versions]` add after `gson = "2.14.0"`:

```toml
datastorePreferences = "1.1.7"
```

In `[libraries]` add after the `gson = ...` line:

```toml
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastorePreferences" }
```

- [ ] **Step 2: Add the dependency to the app module**

Edit `app/build.gradle.kts`. After `implementation(libs.gson)` add:

```kotlin
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Verify Gradle sync compiles**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep datastore`
Expected: a line containing `androidx.datastore:datastore-preferences:1.1.7` (resolved). No build failure.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add datastore-preferences 1.1.7"
```

---

### Task 3: SettingsRepository (DataStore, no local unit test — Android runtime)

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/data/prefs/SettingsRepository.kt`

- [ ] **Step 1: Implement the repository**

Create `app/src/main/java/cc/niaoer/nocall/data/prefs/SettingsRepository.kt`:

```kotlin
package cc.niaoer.nocall.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nocallDataStore by preferencesDataStore("nocall_settings")

class SettingsRepository(private val context: Context) {
    private val notificationEnabledKey = booleanPreferencesKey("notification_enabled")

    val notificationEnabled: Flow<Boolean> = context.nocallDataStore.data
        .map { prefs -> prefs[notificationEnabledKey] ?: true }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.nocallDataStore.edit { it[notificationEnabledKey] = enabled }
    }
}
```

Note: `preferencesOf` import is unused here — remove it if the IDE flags it. The delegate is declared at file top level (Kotlin requires this for `preferencesDataStore`). Default is `true` to preserve existing "always notify" behavior.

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Remove the unused `preferencesOf` import if the compiler warns.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/prefs/SettingsRepository.kt
git commit -m "feat: add SettingsRepository for notification toggle"
```

---

### Task 4: WhitelistEntry entity + DAO + DB migration

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/data/model/WhitelistEntry.kt`
- Create: `app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/data/db/AppDatabase.kt`

- [ ] **Step 1: Create the entity**

Create `app/src/main/java/cc/niaoer/nocall/data/model/WhitelistEntry.kt`:

```kotlin
package cc.niaoer.nocall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist",
    indices = [Index(value = ["normalized_number"], unique = true)]
)
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create the DAO**

Create `app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt`:

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

- [ ] **Step 3: Bump DB to v2 with migration**

Replace `app/src/main/java/cc/niaoer/nocall/data/db/AppDatabase.kt` with:

```kotlin
package cc.niaoer.nocall.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cc.niaoer.nocall.data.model.BlockRule
import cc.niaoer.nocall.data.model.CallLog
import cc.niaoer.nocall.data.model.WhitelistEntry

@Database(
    entities = [BlockRule::class, CallLog::class, WhitelistEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters.class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun callLogDao(): CallLogDao
    abstract fun whitelistDao(): WhitelistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `whitelist` (
                        `id` INTEGER NOT NULL,
                        `phone_number` TEXT NOT NULL,
                        `normalized_number` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_whitelist_normalized_number` ON `whitelist` (`normalized_number`)"
                )
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nocall.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
```

- [ ] **Step 4: Verify KSP generates the new DAO**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL (KSP generates `WhitelistDao_Impl`). If it fails on the `fallbackToDestructiveMigration()` deprecation, keep it — it is the documented safety net per spec and still compiles on Room 2.8.4.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/model/WhitelistEntry.kt \
        app/src/main/java/cc/niaoer/nocall/data/db/WhitelistDao.kt \
        app/src/main/java/cc/niaoer/nocall/data/db/AppDatabase.kt
git commit -m "feat: add whitelist entity, DAO, and DB v1->v2 migration"
```

---

### Task 5: ContactLookup

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Implement the lookup**

Create `app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt`:

```kotlin
package cc.niaoer.nocall.data

import android.content.Context
import android.provider.ContactsContract

class ContactLookup(private val context: Context) {
    fun isInContacts(phoneNumber: String): Boolean {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return false
        val tail = normalized.takeLast(minOf(normalized.length, 11))
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$tail"),
            null
        )
        return cursor?.use { c ->
            while (c.moveToNext()) {
                val stored = c.getString(0) ?: continue
                if (normalizePhone(stored).endsWith(tail)) return@use true
            }
            false
        } ?: false
    }
}
```

Rationale (verified against Android reference): `NUMBER` (`data1`) is always populated; `NORMALIZED_NUMBER` (`data4`) can be null when the provider cannot infer E.164. The SQL `LIKE` is a coarse pre-filter; the in-cursor `normalizePhone(stored).endsWith(tail)` removes false positives. Uses `?` parameter binding instead of string interpolation to avoid SQL injection and quoting issues.

- [ ] **Step 2: Add READ_CONTACTS permission**

Edit `app/src/main/AndroidManifest.xml`. Add after the `POST_NOTIFICATIONS` line:

```xml
    <uses-permission android:name="android.permission.READ_CONTACTS" />
```

- [ ] **Step 3: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add ContactLookup with READ_CONTACTS permission"
```

---

### Task 6: Wire AppContainer

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/AppContainer.kt`

- [ ] **Step 1: Add the three dependencies**

Replace `app/src/main/java/cc/niaoer/nocall/AppContainer.kt` with:

```kotlin
package cc.niaoer.nocall

import android.content.Context
import cc.niaoer.nocall.data.ContactLookup
import cc.niaoer.nocall.data.RuleMatcher
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
    val ruleMatcher: RuleMatcher = RuleMatcher()
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)
    val contactLookup: ContactLookup = ContactLookup(context.applicationContext)
}
```

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/AppContainer.kt
git commit -m "feat: wire whitelist, settings, contactLookup into AppContainer"
```

---

### Task 7: BlockingCallScreeningService rewrite

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt`

- [ ] **Step 1: Rewrite onScreenCall with whitelist-first + notification gate**

Replace the body of `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt` with:

```kotlin
package cc.niaoer.nocall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.model.CallAction
import cc.niaoer.nocall.data.model.CallLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart
        if (phoneNumber.isNullOrBlank()) {
            return
        }

        val container = (application as NoCallApplication).appContainer

        runBlocking {
            // Whitelist has absolute priority: table entry or live contact match.
            val normalized = cc.niaoer.nocall.data.normalizePhone(phoneNumber)
            val inWhitelist = container.whitelistDao.exists(normalized) ||
                container.contactLookup.isInContacts(phoneNumber)

            if (inWhitelist) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
                return@runBlocking
            }

            val enabledRules = container.blockRuleDao.getEnabledList()
            val matched = container.ruleMatcher.match(phoneNumber, enabledRules)

            if (matched != null) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        matchedRuleId = matched.id,
                        matchedRulePattern = matched.pattern,
                        action = CallAction.BLOCKED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val notificationEnabled = container.settingsRepository
                    .notificationEnabled.first()
                if (notificationEnabled) {
                    val ruleDesc = matched.description.ifBlank { matched.pattern }
                    NotificationHelper.showBlockedCallNotification(
                        this@BlockingCallScreeningService,
                        phoneNumber,
                        ruleDesc
                    )
                }
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                respondToCall(details, response)
            } else {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run existing unit tests (no regression)**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:test`
Expected: BUILD SUCCESSFUL, all prior tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt
git commit -m "feat: check whitelist before rules and gate notification on preference"
```

---

### Task 8: Strings for notification + whitelist

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new strings**

Edit `app/src/main/res/values/strings.xml`. Before the closing `</resources>` add:

```xml
    <string name="notification_setting">拦截通知</string>
    <string name="notification_setting_hint">关闭后仍会拦截，但不再显示系统通知</string>
    <string name="add_to_whitelist">加入白名单</string>
    <string name="added_to_whitelist">已加白名单</string>
    <string name="whitelist_title">白名单</string>
    <string name="no_whitelist">无白名单号码</string>
    <string name="whitelist_number">号码</string>
    <string name="whitelist_note">备注</string>
    <string name="enable_contacts_whitelist">启用通讯录白名单</string>
    <string name="enable_contacts_whitelist_message">允许读取通讯录，使通讯录中的号码自动放行</string>
    <string name="whitelist_add">添加</string>
    <string name="whitelist_cancel">取消</string>
    <string name="whitelist_added_toast">已加入白名单</string>
```

- [ ] **Step 2: Verify resource compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add notification and whitelist strings"
```

---

### Task 9: SettingsViewModel notification toggle

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Expose notificationEnabled StateFlow + setter**

Edit `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt`. Add imports:

```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
```

Inside the class, after the `gson` val, add:

```kotlin
    private val settingsRepository = container.settingsRepository

    val notificationEnabled: StateFlow<Boolean> = settingsRepository.notificationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationEnabled(enabled)
        }
    }
```

`container` is already declared at the top of the class (`private val container = (application as NoCallApplication).appContainer`). `viewModelScope` and `launch` are already imported.

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsViewModel.kt
git commit -m "feat: expose notification toggle on SettingsViewModel"
```

---

### Task 10: SettingsScreen notification Switch UI

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add the Switch row above the existing buttons**

Edit `app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsScreen.kt`. Add imports:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
```

(`Text` is already imported; `Switch` and `collectAsStateWithLifecycle` are not.)

Inside `SettingsScreen`, after `val context = LocalContext.current` and `var showClearDialog by remember { mutableStateOf(false) }`, add:

```kotlin
    val notificationEnabled by viewModel.notificationEnabled.collectAsStateWithLifecycle()
```

Then inside the `Column` (the one with `padding(16.dp)`), before the export `Button`, insert:

```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_setting),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.notification_setting_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = viewModel::setNotificationEnabled
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
```

Confirm `Row`, `Column`, `Alignment`, `Modifier.fillMaxWidth`, `Spacer`, `height` are already imported (they are, from the existing file head). Add `import androidx.compose.foundation.layout.Row` and `import androidx.compose.foundation.layout.Column` only if not present.

- [ ] **Step 2: Verify compile + lint**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/settings/SettingsScreen.kt
git commit -m "feat: add notification toggle Switch in Settings"
```

---

### Task 11: CallHistoryViewModel add-to-whitelist

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryViewModel.kt`

- [ ] **Step 1: Expose whitelisted set + addToWhitelist**

Replace `app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryViewModel.kt` with:

```kotlin
package cc.niaoer.nocall.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.normalizePhone
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NoCallApplication).appContainer
    private val callLogDao = container.callLogDao
    private val whitelistDao = container.whitelistDao

    val logs: StateFlow<List<cc.niaoer.nocall.data.model.CallLog>> = callLogDao.getAllOrdered()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelistedNumbers: StateFlow<Set<String>> = whitelistDao.getAll()
        .map { entries -> entries.map { it.normalizedNumber }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun isWhitelisted(phoneNumber: String): Boolean =
        normalizePhone(phoneNumber) in whitelistedNumbers.value

    fun addToWhitelist(phoneNumber: String) {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            whitelistDao.insert(
                WhitelistEntry(
                    phoneNumber = phoneNumber,
                    normalizedNumber = normalized,
                    note = ""
                )
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            callLogDao.deleteAll()
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryViewModel.kt
git commit -m "feat: expose whitelisted set and addToWhitelist on history VM"
```

---

### Task 12: CallHistoryScreen add-to-whitelist button on blocked cards

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryScreen.kt`

- [ ] **Step 1: Pass whitelist state into the card and render the button**

Edit `app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryScreen.kt`. Add imports:

```kotlin
import androidx.compose.material3.TextButton
import cc.niaoer.nocall.data.normalizePhone
```

In `CallHistoryScreen`, after `val logs by viewModel.logs.collectAsStateWithLifecycle()` add:

```kotlin
    val whitelistedNumbers by viewModel.whitelistedNumbers.collectAsStateWithLifecycle()
```

Change the `items(logs, key = { it.id }) { log -> CallLogCard(log) }` call to:

```kotlin
                items(logs, key = { it.id }) { log ->
                    CallLogCard(
                        log = log,
                        isWhitelisted = normalizePhone(log.phoneNumber) in whitelistedNumbers,
                        onAddToWhitelist = { viewModel.addToWhitelist(log.phoneNumber) }
                    )
                }
```

Change the `CallLogCard` signature and body. Replace:

```kotlin
@Composable
private fun CallLogCard(log: CallLog) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val isBlocked = log.action == CallAction.BLOCKED
```

with:

```kotlin
@Composable
private fun CallLogCard(
    log: CallLog,
    isWhitelisted: Boolean,
    onAddToWhitelist: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val isBlocked = log.action == CallAction.BLOCKED
```

Then, inside the `Column` of the card, after the existing `if (isBlocked && log.matchedRulePattern != null) { ... }` block, append:

```kotlin
            if (isBlocked) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onAddToWhitelist,
                    enabled = !isWhitelisted
                ) {
                    Text(
                        text = stringResource(
                            if (isWhitelisted) R.string.added_to_whitelist
                            else R.string.add_to_whitelist
                        )
                    )
                }
            }
```

- [ ] **Step 2: Verify compile + lint**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryScreen.kt
git commit -m "feat: add-to-whitelist button on blocked history cards"
```

---

### Task 13: WhitelistViewModel

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistViewModel.kt`

- [ ] **Step 1: Implement the ViewModel**

Create `app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistViewModel.kt`:

```kotlin
package cc.niaoer.nocall.ui.whitelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.normalizePhone
import cc.niaoer.nocall.data.model.WhitelistEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(application: Application) : AndroidViewModel(application) {
    private val whitelistDao = (application as NoCallApplication).appContainer.whitelistDao

    val entries: StateFlow<List<WhitelistEntry>> = whitelistDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(phoneNumber: String, note: String) {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            whitelistDao.insert(
                WhitelistEntry(
                    phoneNumber = phoneNumber.trim(),
                    normalizedNumber = normalized,
                    note = note.trim()
                )
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            whitelistDao.deleteById(id)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistViewModel.kt
git commit -m "feat: add WhitelistViewModel"
```

---

### Task 14: WhitelistScreen with READ_CONTACTS request

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistScreen.kt`

- [ ] **Step 1: Implement the screen**

Create `app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistScreen.kt`:

```kotlin
package cc.niaoer.nocall.ui.whitelist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.nocall.R
import cc.niaoer.nocall.data.model.WhitelistEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    onNavigateBack: () -> Unit,
    viewModel: WhitelistViewModel = viewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var contactsPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsPermissionGranted = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whitelist_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.whitelist_add))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!contactsPermissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.enable_contacts_whitelist),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.enable_contacts_whitelist_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.enable_contacts_whitelist))
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_whitelist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        WhitelistRow(
                            entry = entry,
                            onDelete = { viewModel.delete(entry.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        var phone by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.whitelist_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.whitelist_number)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.whitelist_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.add(phone, note)
                    showAddDialog = false
                }) { Text(stringResource(R.string.whitelist_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.whitelist_cancel))
                }
            }
        )
    }
}

@Composable
private fun WhitelistRow(entry: WhitelistEntry, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    text = entry.phoneNumber,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile + lint**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/whitelist/WhitelistScreen.kt
git commit -m "feat: add WhitelistScreen with READ_CONTACTS request"
```

---

### Task 15: Navigation route + RulesScreen entry + NavHost wiring

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/rules/RulesScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/MainActivity.kt`

- [ ] **Step 1: Add the route**

Edit `app/src/main/java/cc/niaoer/nocall/ui/navigation/NavRoutes.kt`. Add after `const val SETTINGS = "settings"`:

```kotlin
    const val WHITELIST = "whitelist"
```

- [ ] **Step 2: Add the top-bar entry in RulesScreen**

Edit `app/src/main/java/cc/niaoer/nocall/ui/rules/RulesScreen.kt`. Add imports:

```kotlin
import androidx.compose.material.icons.filled.List
```

Add an `onWhitelist: () -> Unit` parameter to `RulesScreen` (after `onTestRule: () -> Unit`):

```kotlin
    onWhitelist: () -> Unit,
```

In the `actions` block of the `TopAppBar`, after the Test `IconButton` and before the Settings `IconButton`, add:

```kotlin
                    IconButton(onClick = onWhitelist) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = stringResource(R.string.whitelist_title)
                        )
                    }
```

- [ ] **Step 3: Wire the NavHost and pass the callback**

Edit `app/src/main/java/cc/niaoer/nocall/MainActivity.kt`. Add import:

```kotlin
import cc.niaoer.nocall.ui.whitelist.WhitelistScreen
```

In `NoCallNavHost`, in the `composable(NavRoutes.RULES) { RulesScreen(...) }` call, add `onWhitelist = { navController.navigate(NavRoutes.WHITELIST) }` to the `RulesScreen(...)` arguments.

After the `composable(NavRoutes.RULE_TEST) { ... }` block, add:

```kotlin
        composable(NavRoutes.WHITELIST) {
            WhitelistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
```

- [ ] **Step 4: Verify compile + lint**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/navigation/NavRoutes.kt \
        app/src/main/java/cc/niaoer/nocall/ui/rules/RulesScreen.kt \
        app/src/main/java/cc/niaoer/nocall/MainActivity.kt
git commit -m "feat: add whitelist route and RulesScreen top-bar entry"
```

---

### Task 16: Full automated verification

**Files:** No production changes.

- [ ] **Step 1: Run all unit tests**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:test`
Expected: BUILD SUCCESSFUL. Confirm `PhoneNormalizerTest` (4) + existing `RuleMatcherTest` (29) + `RuleEditDecisionTest` (7) + `RuleImportTest` (7) all pass.

- [ ] **Step 2: Run lint + whitespace check**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug && git diff --check`
Expected: BUILD SUCCESSFUL and no whitespace errors.

- [ ] **Step 3: Inspect the full diff scope**

Run: `git diff main --stat` (or `git log --oneline main..HEAD` if on a branch)
Expected: only files named in Tasks 1–15 touched; no device artifacts or generated files tracked.

- [ ] **Step 4: Commit verification note (optional)**

Only if the working tree has any leftover changes from fixing lint findings:

```bash
git add -A
git commit -m "chore: address lint findings from new screens"
```

---

### Task 17: Physical-device verification

**Target device:** `00285361G001888` (A069, Android 16/API 36), already holding `android.app.role.CALL_SCREENING` for `cc.niaoer.nocall`. Verify with `adb -s 00285361G001888 shell dumpsys role | grep -A2 CALL_SCREENING` before starting.

**Files:** No production changes.

- [ ] **Step 1: Confirm device online and role held**

Run: `adb devices -l` (expect `00285361G001888 device`) and `adb -s 00285361G001888 shell dumpsys role | grep -A3 CALL_SCREENING` (expect `holders=cc.niaoer.nocall`).

- [ ] **Step 2: Install debug APK preserving data**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_SERIAL=00285361G001888 ./gradlew :app:installDebug`
Expected: `Installed on 1 device.` This MUST preserve the existing v1 DB so the v1→v2 migration runs on real data.

- [ ] **Step 3: Verify DB migration ran cleanly**

Run:
```
adb -s 00285361G001888 shell run-as cc.niaoer.nocall ls databases/
adb -s 00285361G001888 shell run-as cc.niaoer.nocall cat databases/nocall.db > /tmp/nocall_db2.db
sqlite3 /tmp/nocall_db2.db ".tables"
sqlite3 /tmp/nocall_db2.db "SELECT id, pattern, rule_type FROM block_rules ORDER BY id;"
sqlite3 /tmp/nocall_db2.db "SELECT COUNT(*) FROM call_logs;"
```
Expected: `whitelist` table present; pre-existing `block_rules` and `call_logs` rows survive (no data loss). If the device has no `sqlite3` binary, pull the db file and inspect with host `sqlite3` as shown.

- [ ] **Step 4: Notification toggle off → blocked call produces no notification**

In the app, open Settings and turn the "拦截通知" switch OFF. Trigger a blocked call (use a number that matches an existing enabled rule; on the emulator use `gsm call`, or on the device use a number known to match). Observe:
- The call is rejected (not connected).
- No system notification appears.
- `sqlite3 /tmp/nocall_db2.db "SELECT phone_number, action, datetime(timestamp/1000,'unixepoch','localtime') FROM call_logs ORDER BY timestamp DESC LIMIT 3;"` shows a new BLOCKED row.

- [ ] **Step 5: Notification toggle on → notification appears**

Turn the switch ON. Trigger another blocked call. Observe: a system notification "拦截来电 / 号码: …" appears; a new BLOCKED row is logged.

- [ ] **Step 6: Add-to-whitelist from a blocked history card**

Open History. On a BLOCKED card tap "加入白名单". Expected: the button text flips to "已加白名单" and is disabled. Verify the DB:
```
adb -s 00285361G001888 shell run-as cc.niaoer.nocall cat databases/nocall.db > /tmp/nocall_db2.db
sqlite3 /tmp/nocall_db2.db "SELECT id, phone_number, normalized_number, note FROM whitelist;"
```
Expected: one row with the added number and its normalized form.

- [ ] **Step 7: Whitelisted number is allowed even though a rule matches**

Trigger a call from the same number again. Expected: the call is allowed (rings/connects, not rejected), no notification, and a new ALLOWED row is logged:
```
sqlite3 /tmp/nocall_db2.db "SELECT phone_number, action, datetime(timestamp/1000,'unixepoch','localtime') FROM call_logs ORDER BY timestamp DESC LIMIT 3;"
```
Expected: top row is ALLOWED for that number, even though a rule in `block_rules` still matches it.

- [ ] **Step 8: Contacts-based whitelist (live lookup)**

Open the Whitelist screen (Rules top-bar list icon). If READ_CONTACTS is not granted, tap "启用通讯录白名单" and grant. Save a test contact in the device contacts app with a number that matches an existing rule. Trigger a call from that number. Expected: allowed (not blocked), ALLOWED log row, no notification — without any `whitelist` table entry for it:
```
sqlite3 /tmp/nocall_db2.db "SELECT phone_number FROM whitelist WHERE normalized_number LIKE '%<digits>';"
```
Expected: no row (contacts are live, not imported).

- [ ] **Step 9: Whitelist management — add via FAB, delete**

In the Whitelist screen, tap the FAB, enter a number + note, save. Expected: new row appears. Tap the delete icon on a row. Expected: row disappears; confirm in DB it is gone.

- [ ] **Step 10: Logcat health check**

Run: `adb -s 00285361G001888 logcat -d -t 500 | grep -iE 'nocall|CallScreening|FATAL|AndroidRuntime|SQLiteException|Migration'`
Expected: no FATAL/AndroidRuntime crashes, no SQLiteException, no migration errors. Bound CallScreeningService messages present on blocked/allowed calls.

- [ ] **Step 11: Record evidence**

Save the key command outputs (role holder, migration tables, notification on/off log rows, whitelist row, logcat crash grep) into the final report. State plainly which steps passed and, if any could not be run on the physical device, why.

---

## Self-Review Notes

- **Spec coverage:** notification toggle (Tasks 2,3,7,9,10), whitelist table+contacts (Tasks 4,5,6,7), whitelist-first priority (Task 7), history add-to-whitelist (Tasks 11,12), Whitelist screen + READ_CONTACTS (Tasks 13,14,15), DB migration preserving data (Tasks 4,17 step 3), physical-device verification (Task 17). All spec sections covered.
- **Type/name consistency:** `normalizePhone`, `WhitelistEntry`, `WhitelistDao` (getAll/exists/insert/deleteById), `SettingsRepository` (notificationEnabled/setNotificationEnabled), `ContactLookup.isInContacts`, `CallHistoryViewModel` (whitelistedNumbers/addToWhitelist), `WhitelistViewModel` (entries/add/delete), `NavRoutes.WHITELIST` — consistent across tasks.
- **No placeholders.**
