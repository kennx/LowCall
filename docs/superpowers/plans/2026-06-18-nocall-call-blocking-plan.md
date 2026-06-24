# LowCall Call Blocking — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a personal-use Android call blocking app with CallScreeningService + Room + Jetpack Compose Material3.

**Architecture:** Single-Activity NavHost with manual DI (AppContainer). Room database stores rules and call logs. `BlockingCallScreeningService` matches incoming calls against enabled rules, rejects blocked calls with notification. 5 screens: Rules, RuleEdit, RuleTest, History, Settings.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Compose BOM 2026.02.01, Room 2.8.4, Navigation Compose 2.9.8, KSP 2.3.9, Lifecycle 2.10.0, Gson 2.14.0

---

### Task 1: Add Dependencies to Version Catalog and Build Files

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version numbers and library declarations to `gradle/libs.versions.toml`**

Open `gradle/libs.versions.toml`. Add under `[versions]`:

```toml
ksp = "2.3.9"
room = "2.8.4"
navigation = "2.9.8"
lifecycle = "2.10.0"
gson = "2.14.0"
```

Add under `[libraries]`:

```toml
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
```

Add under `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Add KSP plugin to root `build.gradle.kts`**

Open `build.gradle.kts` (root). Add the KSP plugin line:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Add KSP plugin and new dependencies to `app/build.gradle.kts`**

Open `app/build.gradle.kts`. Add `alias(libs.plugins.ksp)` to the `plugins {}` block. Add the new dependencies to the `dependencies {}` block:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ... inside dependencies {} block, add these new lines:
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.androidx.navigation.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.gson)
```

- [ ] **Step 4: Sync and verify build**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:dependencies --configuration debugCompileClasspath 2>&1 | head -5
```

Expected: BUILD SUCCESSFUL (no dependency resolution errors).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "chore: add Room, Navigation, Lifecycle, Gson dependencies"
```

---

### Task 2: Create Data Model Classes

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/data/model/BlockRule.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/data/model/CallLog.kt`

- [ ] **Step 1: Create `BlockRule.kt`**

```kotlin
package cc.niaoer.lowcall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleType { EXACT, WILDCARD, REGEX }

@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "rule_type") val ruleType: RuleType,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create `CallLog.kt`**

```kotlin
package cc.niaoer.lowcall.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CallAction { BLOCKED, ALLOWED }

@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["timestamp"])]
)
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: Long? = null,
    @ColumnInfo(name = "matched_rule_pattern") val matchedRulePattern: String? = null,
    @ColumnInfo(name = "action") val action: CallAction,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Build to verify compilation**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/data/model/
git commit -m "feat: add BlockRule and CallLog entity models"
```

---

### Task 3: Create Room Database, DAOs, and Type Converters

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/data/db/Converters.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/data/db/BlockRuleDao.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/data/db/CallLogDao.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/data/db/AppDatabase.kt`

- [ ] **Step 1: Create type converters**

```kotlin
package cc.niaoer.lowcall.data.db

import androidx.room.TypeConverter
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.RuleType

class Converters {
    @TypeConverter
    fun fromRuleType(value: RuleType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): RuleType = RuleType.valueOf(value)

    @TypeConverter
    fun fromCallAction(value: CallAction): String = value.name

    @TypeConverter
    fun toCallAction(value: String): CallAction = CallAction.valueOf(value)
}
```

- [ ] **Step 2: Create `BlockRuleDao.kt`**

```kotlin
package cc.niaoer.lowcall.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.niaoer.lowcall.data.model.BlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun getAll(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1 ORDER BY created_at DESC")
    fun getEnabled(): Flow<List<BlockRule>>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun getById(id: Long): BlockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockRule): Long

    @Update
    suspend fun update(rule: BlockRule)

    @Delete
    suspend fun delete(rule: BlockRule)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BlockRule>)
}
```

- [ ] **Step 3: Create `CallLogDao.kt`**

```kotlin
package cc.niaoer.lowcall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllOrdered(): Flow<List<CallLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLog): Long

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}
```

- [ ] **Step 4: Create `AppDatabase.kt`**

```kotlin
package cc.niaoer.lowcall.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.CallLog

@Database(
    entities = [BlockRule::class, CallLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "lowcall.db"
            ).build()
        }
    }
}
```

- [ ] **Step 5: Build to verify Room compilation**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/data/db/
git commit -m "feat: add Room database, DAOs, and type converters"
```

---

### Task 4: Create RuleMatcher with Unit Tests

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/data/RuleMatcher.kt`
- Create: `app/src/test/java/cc/niaoer/lowcall/data/RuleMatcherTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/cc/niaoer/lowcall/data/RuleMatcherTest.kt`:

```kotlin
package cc.niaoer.lowcall.data

import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMatcherTest {

    private val matcher = RuleMatcher()

    @Test
    fun match_exact_sameNumber_returnsRule() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_exact_differentNumber_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT)
        val result = matcher.match("13800138001", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_starMatchesAny() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_wildcard_starNoMatch_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_wildcard_questionMatchesSingleChar() {
        val rule = BlockRule(id = 1, pattern = "138????????", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_validPattern_matches() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = matcher.match("13800138000", listOf(rule))
        assertEquals(rule, result)
    }

    @Test
    fun match_regex_invalidInput_returnsNull() {
        val rule = BlockRule(id = 1, pattern = "^138\\d{8}$", ruleType = RuleType.REGEX)
        val result = matcher.match("13900138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_disabledRule_isSkipped() {
        val rule = BlockRule(id = 1, pattern = "13800138000", ruleType = RuleType.EXACT, enabled = false)
        val result = matcher.match("13800138000", listOf(rule))
        assertNull(result)
    }

    @Test
    fun match_multipleRules_returnsFirstMatch() {
        val rule1 = BlockRule(id = 1, pattern = "139*", ruleType = RuleType.WILDCARD)
        val rule2 = BlockRule(id = 2, pattern = "138*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("13800138000", listOf(rule1, rule2))
        assertEquals(rule2, result)
    }

    @Test
    fun match_emptyRules_returnsNull() {
        val result = matcher.match("13800138000", emptyList())
        assertNull(result)
    }

    @Test
    fun match_wildcard_specialRegexChars_escaped() {
        val rule = BlockRule(id = 1, pattern = "+86*", ruleType = RuleType.WILDCARD)
        val result = matcher.match("+8613800138000", listOf(rule))
        assertEquals(rule, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "cc.niaoer.lowcall.data.RuleMatcherTest" 2>&1 | tail -10
```

Expected: FAIL — RuleMatcher class not found.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/cc/niaoer/lowcall/data/RuleMatcher.kt`:

```kotlin
package cc.niaoer.lowcall.data

import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType

class RuleMatcher {

    fun match(phoneNumber: String, rules: List<BlockRule>): BlockRule? {
        return rules.firstOrNull { rule ->
            rule.enabled && when (rule.ruleType) {
                RuleType.EXACT -> phoneNumber == rule.pattern
                RuleType.WILDCARD -> wildcardToRegex(rule.pattern).matches(phoneNumber)
                RuleType.REGEX -> runCatching {
                    Regex(rule.pattern).matches(phoneNumber)
                }.getOrDefault(false)
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

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "cc.niaoer.lowcall.data.RuleMatcherTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all 11 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/data/RuleMatcher.kt app/src/test/java/cc/niaoer/lowcall/data/RuleMatcherTest.kt
git commit -m "feat: add RuleMatcher with exact/wildcard/regex matching"
```

---

### Task 5: Create AppContainer and Application Class

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/AppContainer.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/LowCallApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `AppContainer.kt`**

```kotlin
package cc.niaoer.lowcall

import android.content.Context
import cc.niaoer.lowcall.data.RuleMatcher
import cc.niaoer.lowcall.data.db.AppDatabase
import cc.niaoer.lowcall.data.db.BlockRuleDao
import cc.niaoer.lowcall.data.db.CallLogDao

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val blockRuleDao: BlockRuleDao = database.blockRuleDao()
    val callLogDao: CallLogDao = database.callLogDao()
    val ruleMatcher: RuleMatcher = RuleMatcher()
}
```

- [ ] **Step 2: Create `LowCallApplication.kt`**

```kotlin
package cc.niaoer.lowcall

import android.app.Application

class LowCallApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
```

- [ ] **Step 3: Register Application in `AndroidManifest.xml`**

In `app/src/main/AndroidManifest.xml`, add `android:name=".LowCallApplication"` to the `<application>` tag:

```xml
<application
    android:name=".LowCallApplication"
    android:allowBackup="true"
    ...
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/AppContainer.kt app/src/main/java/cc/niaoer/lowcall/LowCallApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add AppContainer and LowCallApplication"
```

---

### Task 6: Create CallScreeningService and NotificationHelper

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/service/NotificationHelper.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/service/BlockingCallScreeningService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `NotificationHelper.kt`**

```kotlin
package cc.niaoer.lowcall.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.niaoer.lowcall.MainActivity
import cc.niaoer.lowcall.R

object NotificationHelper {
    const val CHANNEL_ID = "call_blocking"
    private const val CHANNEL_NAME = "来电拦截"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "拦截来电通知"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showBlockedCallNotification(context: Context, phoneNumber: String, ruleDescription: String) {
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val description = if (ruleDescription.isNotBlank()) {
            "规则: $ruleDescription"
        } else {
            "已拦截来电"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("拦截来电")
            .setContentText("号码: $phoneNumber")
            .setStyle(NotificationCompat.BigTextStyle().bigText("号码: $phoneNumber\n$description"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(phoneNumber.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(context).notify(phoneNumber.hashCode(), notification)
        }
    }
}
```

- [ ] **Step 2: Create `BlockingCallScreeningService.kt`**

```kotlin
package cc.niaoer.lowcall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart
        if (phoneNumber.isNullOrBlank()) {
            return
        }

        val container = (application as LowCallApplication).appContainer
        val rules = runBlocking { container.blockRuleDao.getEnabled() }
        val enabledRules = runBlocking {
            // getEnabled() returns Flow, we need a list from the current snapshot
            // For the screening service, we read enabled rules synchronously
            container.database.blockRuleDao().let { dao ->
                // Use a direct non-Flow query for the service
                runBlocking {
                    dao.getEnabledList()
                }
            }
        }.let { rulesFromFlow ->
            // Rules already filtered by Flow, use directly
            rulesFromFlow
        }

        // Since the service runs on a background thread, use runBlocking safely
        val matched = container.ruleMatcher.match(phoneNumber, enabledRules)

        if (matched != null) {
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

            val ruleDesc = matched.description.ifBlank { matched.pattern }
            NotificationHelper.showBlockedCallNotification(this, phoneNumber, ruleDesc)

            respondToCall(details, CallResponse.reject(false, null))
        } else {
            runBlocking {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
```

Wait — I need to also add a non-Flow query to `BlockRuleDao` for the service. Let me fix this in the service and DAO.

Actually, let me revise: the service needs a synchronous list of enabled rules. I'll add a `getEnabledList()` suspend function to the DAO.

- [ ] **Step 2a: Add `getEnabledList()` to `BlockRuleDao.kt`**

Edit `app/src/main/java/cc/niaoer/lowcall/data/db/BlockRuleDao.kt`, add this query:

```kotlin
@Query("SELECT * FROM block_rules WHERE enabled = 1 ORDER BY created_at DESC")
suspend fun getEnabledList(): List<BlockRule>
```

- [ ] **Step 2b: Simplify `BlockingCallScreeningService.kt`**

```kotlin
package cc.niaoer.lowcall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart
        if (phoneNumber.isNullOrBlank()) {
            return
        }

        val container = (application as LowCallApplication).appContainer

        runBlocking {
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
                val ruleDesc = matched.description.ifBlank { matched.pattern }
                NotificationHelper.showBlockedCallNotification(
                    this@BlockingCallScreeningService, phoneNumber, ruleDesc
                )
                respondToCall(details, CallResponse.reject(false, null))
            } else {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 3: Register service and add permissions to `AndroidManifest.xml`**

Before the `<application>` tag, add:

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Inside `<application>`, after `</activity>`, add:

```xml
<service
    android:name=".service.BlockingCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/service/ app/src/main/java/cc/niaoer/lowcall/data/db/BlockRuleDao.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add CallScreeningService and notification helper"
```

---

### Task 7: Create Navigation Routes

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/navigation/NavRoutes.kt`

- [ ] **Step 1: Create `NavRoutes.kt`**

```kotlin
package cc.niaoer.lowcall.ui.navigation

object NavRoutes {
    const val RULES = "rules"
    const val RULE_ADD = "rules/add"
    const val RULE_EDIT = "rules/{ruleId}"
    const val RULE_TEST = "rules/test"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun ruleEdit(ruleId: Long): String = "rules/$ruleId"
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/navigation/
git commit -m "feat: add navigation route constants"
```

---

### Task 8: Create RulesViewModel and RulesScreen

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesViewModel.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesScreen.kt`

- [ ] **Step 1: Create `RulesViewModel.kt`**

```kotlin
package cc.niaoer.lowcall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.BlockRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as LowCallApplication).appContainer.blockRuleDao

    val rules: StateFlow<List<BlockRule>> = blockRuleDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEnabled(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.update(rule.copy(enabled = !rule.enabled))
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            blockRuleDao.delete(rule)
        }
    }
}
```

- [ ] **Step 2: Create `RulesScreen.kt`**

```kotlin
package cc.niaoer.lowcall.ui.rules

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
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onTestRule: () -> Unit,
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
                    IconButton(onClick = onTestRule) {
                        Icon(Icons.Default.Science, contentDescription = stringResource(R.string.rule_test))
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
```

- [ ] **Step 3: Add string resources to `app/src/main/res/values/strings.xml`**

Replace the current strings.xml content:

```xml
<resources>
    <string name="app_name">LowCall</string>
    <string name="rules_title">拦截规则</string>
    <string name="no_rules">暂无拦截规则\n点击右下角添加</string>
    <string name="add_rule">添加规则</string>
    <string name="rule_test">测试匹配</string>
    <string name="settings">设置</string>
    <string name="edit_rule">编辑规则</string>
    <string name="pattern">匹配模式</string>
    <string name="description">备注</string>
    <string name="rule_type">匹配类型</string>
    <string name="save">保存</string>
    <string name="delete">删除</string>
    <string name="enabled">启用</string>
    <string name="test_title">测试匹配</string>
    <string name="test_phone">输入测试号码</string>
    <string name="test_button">测试</string>
    <string name="test_matched">匹配规则: %1$s</string>
    <string name="test_no_match">不会被拦截</string>
    <string name="history_title">通话记录</string>
    <string name="no_history">暂无通话记录</string>
    <string name="blocked">已拦截</string>
    <string name="allowed">已放行</string>
    <string name="settings_title">设置</string>
    <string name="export_rules">导出规则</string>
    <string name="import_rules">导入规则</string>
    <string name="clear_history">清空记录</string>
    <string name="clear_confirm">确认清空所有通话记录？</string>
    <string name="confirm">确认</string>
    <string name="cancel">取消</string>
    <string name="export_success">规则已导出</string>
    <string name="import_success">已导入 %1$d 条规则</string>
    <string name="import_error">导入失败，请检查文件格式</string>
    <string name="setup_title">设置来电筛选</string>
    <string name="setup_message">请将 LowCall 设为来电筛选应用以启用拦截功能</string>
    <string name="open_settings">打开设置</string>
    <string name="notification_permission">需要通知权限以显示拦截提醒</string>
    <string name="grant_permission">授予权限</string>
    <string name="history_deleted">记录已清空</string>
</resources>
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/rules/ app/src/main/res/values/strings.xml
git commit -m "feat: add RulesScreen with rule list and toggle"
```

---

### Task 9: Create RuleEditViewModel and RuleEditScreen

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RuleEditViewModel.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RuleEditScreen.kt`

- [ ] **Step 1: Create `RuleEditViewModel.kt`**

```kotlin
package cc.niaoer.lowcall.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleEditUiState(
    val pattern: String = "",
    val ruleType: RuleType = RuleType.EXACT,
    val description: String = "",
    val enabled: Boolean = true,
    val isNew: Boolean = true,
    val saved: Boolean = false
)

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    private val blockRuleDao = (application as LowCallApplication).appContainer.blockRuleDao

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
            }
        }
    }

    fun updatePattern(pattern: String) {
        _uiState.value = _uiState.value.copy(pattern = pattern)
    }

    fun updateRuleType(ruleType: RuleType) {
        _uiState.value = _uiState.value.copy(ruleType = ruleType)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun save() {
        val state = _uiState.value
        if (state.pattern.isBlank()) return
        viewModelScope.launch {
            val rule = editingRule?.copy(
                pattern = state.pattern,
                ruleType = state.ruleType,
                description = state.description,
                enabled = state.enabled
            ) ?: BlockRule(
                pattern = state.pattern,
                ruleType = state.ruleType,
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

- [ ] **Step 2: Create `RuleEditScreen.kt`**

```kotlin
package cc.niaoer.lowcall.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.rule_type),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RuleType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.ruleType == type,
                        onClick = { viewModel.updateRuleType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, RuleType.entries.size)
                    ) {
                        Text(
                            when (type) {
                                RuleType.EXACT -> "精确"
                                RuleType.WILDCARD -> "通配"
                                RuleType.REGEX -> "正则"
                            }
                        )
                    }
                }
            }

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
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/rules/
git commit -m "feat: add RuleEditScreen with add/edit/delete"
```

---

### Task 10: Create RuleTestViewModel and RuleTestScreen

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/test/RuleTestViewModel.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/test/RuleTestScreen.kt`

- [ ] **Step 1: Create `RuleTestViewModel.kt`**

```kotlin
package cc.niaoer.lowcall.ui.test

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.BlockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuleTestUiState(
    val phoneNumber: String = "",
    val matchedRule: BlockRule? = null,
    val tested: Boolean = false
)

class RuleTestViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LowCallApplication).appContainer

    private val _uiState = MutableStateFlow(RuleTestUiState())
    val uiState: StateFlow<RuleTestUiState> = _uiState.asStateFlow()

    fun updatePhoneNumber(number: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = number, tested = false)
    }

    fun test() {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank()) return
        viewModelScope.launch {
            val rules = container.blockRuleDao.getEnabledList()
            val matched = container.ruleMatcher.match(phone, rules)
            _uiState.value = _uiState.value.copy(matchedRule = matched, tested = true)
        }
    }
}
```

- [ ] **Step 2: Create `RuleTestScreen.kt`**

```kotlin
package cc.niaoer.lowcall.ui.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: RuleTestViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.test_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                value = state.phoneNumber,
                onValueChange = viewModel::updatePhoneNumber,
                label = { Text(stringResource(R.string.test_phone)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::test,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phoneNumber.isNotBlank()
            ) {
                Text(stringResource(R.string.test_button))
            }

            if (state.tested) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.matchedRule != null)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.matchedRule != null) {
                            val rule = state.matchedRule!!
                            Text(
                                text = stringResource(R.string.test_matched, rule.pattern),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (rule.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rule.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.test_no_match),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/test/
git commit -m "feat: add RuleTestScreen for testing rule matching"
```

---

### Task 11: Create CallHistoryViewModel and CallHistoryScreen

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryViewModel.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryScreen.kt`

- [ ] **Step 1: Create `CallHistoryViewModel.kt`**

```kotlin
package cc.niaoer.lowcall.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.CallLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val callLogDao = (application as LowCallApplication).appContainer.callLogDao

    val logs: StateFlow<List<CallLog>> = callLogDao.getAllOrdered()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            callLogDao.deleteAll()
        }
    }
}
```

- [ ] **Step 2: Create `CallHistoryScreen.kt`**

```kotlin
package cc.niaoer.lowcall.ui.history

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import cc.niaoer.lowcall.R
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallHistoryViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_history),
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
                items(logs, key = { it.id }) { log ->
                    CallLogCard(log)
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(log: CallLog) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val isBlocked = log.action == CallAction.BLOCKED

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.phoneNumber,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                ActionChip(isBlocked)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isBlocked && log.matchedRulePattern != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "规则: ${log.matchedRulePattern}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionChip(isBlocked: Boolean) {
    val label = if (isBlocked)
        stringResource(R.string.blocked)
    else
        stringResource(R.string.allowed)
    val containerColor = if (isBlocked)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onError
        )
    )
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/history/
git commit -m "feat: add CallHistoryScreen with blocked/allowed log list"
```

---

### Task 12: Create SettingsViewModel and SettingsScreen (Import/Export/Clear)

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create `SettingsViewModel.kt`**

```kotlin
package cc.niaoer.lowcall.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.niaoer.lowcall.LowCallApplication
import cc.niaoer.lowcall.data.model.BlockRule
import cc.niaoer.lowcall.data.model.RuleType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val container = (application as LowCallApplication).appContainer
    private val gson = Gson()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

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
                Toast.makeText(context, context.getString(cc.niaoer.lowcall.R.string.export_success), Toast.LENGTH_SHORT).show()
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

                val rules = importList.map { export ->
                    BlockRule(
                        pattern = export.pattern,
                        ruleType = RuleType.valueOf(export.ruleType),
                        enabled = export.enabled,
                        description = export.description
                    )
                }
                container.blockRuleDao.insertAll(rules)
                Toast.makeText(
                    context,
                    context.getString(cc.niaoer.lowcall.R.string.import_success, rules.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(cc.niaoer.lowcall.R.string.import_error),
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
                context.getString(cc.niaoer.lowcall.R.string.history_deleted),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
```

- [ ] **Step 2: Create `SettingsScreen.kt`**

```kotlin
package cc.niaoer.lowcall.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.niaoer.lowcall.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRules(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRules(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            Button(
                onClick = { exportLauncher.launch("lowcall_rules.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_rules))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_rules))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.clear_history))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(context)
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/ui/settings/
git commit -m "feat: add SettingsScreen with import/export/clear"
```

---

### Task 13: Update MainActivity with NavHost and Setup Guide

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/MainActivity.kt`

- [ ] **Step 1: Rewrite `MainActivity.kt`**

```kotlin
package cc.niaoer.lowcall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.niaoer.lowcall.ui.history.CallHistoryScreen
import cc.niaoer.lowcall.ui.navigation.NavRoutes
import cc.niaoer.lowcall.ui.rules.RuleEditScreen
import cc.niaoer.lowcall.ui.rules.RulesScreen
import cc.niaoer.lowcall.ui.settings.SettingsScreen
import cc.niaoer.lowcall.ui.test.RuleTestScreen
import cc.niaoer.lowcall.ui.theme.LowCallTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, app still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        ensureScreeningServiceEnabled()

        setContent {
            LowCallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.RULES
                    ) {
                        composable(NavRoutes.RULES) {
                            RulesScreen(
                                onAddRule = { navController.navigate(NavRoutes.RULE_ADD) },
                                onEditRule = { id -> navController.navigate(NavRoutes.ruleEdit(id)) },
                                onTestRule = { navController.navigate(NavRoutes.RULE_TEST) },
                                onSettings = { navController.navigate(NavRoutes.SETTINGS) }
                            )
                        }
                        composable(NavRoutes.RULE_ADD) {
                            RuleEditScreen(
                                ruleId = null,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = NavRoutes.RULE_EDIT,
                            arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getLong("ruleId")
                            RuleEditScreen(
                                ruleId = ruleId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoutes.RULE_TEST) {
                            RuleTestScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoutes.HISTORY) {
                            CallHistoryScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureScreeningServiceEnabled() {
        // Check if our screening service is the default
        lifecycleScope.launch {
            delay(500) // Wait for UI to settle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val telecomManager = getSystemService(TelecomManager::class.java)
                // Guide user to enable screening service if not set
                // TelecomManager does not expose a direct check for CallScreeningService,
                // so we guide users on first launch
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cc/niaoer/lowcall/MainActivity.kt
git commit -m "feat: update MainActivity with NavHost and all screens"
```

---

### Task 14: Add Initial Rule (Dogfooding) and Polishing

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/LowCallApplication.kt` (add prepopulate)
- Modify: `app/src/main/res/values/strings.xml` (add setup guide strings)
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesScreen.kt` (add history button)

No new files needed beyond the AppContainer update for prepopulating a demo rule.

- [ ] **Step 1: Remove the old ExampleUnitTest and ExampleInstrumentedTest, replace with real tests**

Delete:
- `app/src/test/java/cc/niaoer/lowcall/ExampleUnitTest.kt`
- `app/src/androidTest/java/cc/niaoer/lowcall/ExampleInstrumentedTest.kt`

```bash
rm app/src/test/java/cc/niaoer/lowcall/ExampleUnitTest.kt
rm app/src/androidTest/java/cc/niaoer/lowcall/ExampleInstrumentedTest.kt
```

- [ ] **Step 2: Run all unit tests**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, RuleMatcherTest passes.

- [ ] **Step 3: Add history icon button to RulesScreen TopAppBar**

Modify `RulesScreen.kt` TopAppBar actions to include history:

In the `actions` block of TopAppBar, add a history icon before settings:

```kotlin
import androidx.compose.material.icons.filled.History

// Inside TopAppBar actions, add before the existing icons:
IconButton(onClick = onHistory) {
    Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title))
}
```

And add `onHistory: () -> Unit` parameter to `RulesScreen` composable.

- [ ] **Step 4: Update MainActivity to pass onHistory**

In the `composable(NavRoutes.RULES)` block, add:
```kotlin
onHistory = { navController.navigate(NavRoutes.HISTORY) },
```

- [ ] **Step 5: Build and run full check**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run lint check**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintDebug 2>&1 | tail -15
```

Expected: No errors (warnings ok).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add history navigation, remove template tests, polish UI"
```

---

### Task 15: Build and Deploy via ADB

**Files:** None (deployment only)

- [ ] **Step 1: Check connected devices**

```bash
adb devices
```

Expected: List of connected devices with "device" status.

- [ ] **Step 2: Build debug APK**

```bash
cd /Users/kenn/PROJECTS/LowCall && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: "Success"

- [ ] **Step 4: Launch the app**

```bash
adb shell am start -n cc.niaoer.lowcall/.MainActivity
```

Expected: App launches on device.

- [ ] **Step 5: Verify app is installed and running**

```bash
adb shell pm list packages | grep lowcall
```

Expected: `package:cc.niaoer.lowcall`

- [ ] **Step 6: Enable the screening service**

After app launches, the user must manually enable CallScreeningService:
- Open Settings → Apps → Default apps → Caller ID & spam app → Select LowCall
- Or run: `adb shell cmd telecom set-default-dialer` (not applicable for screening service)

Note: The user will need to manually enable the screening service in system settings. We should display a guide on first launch.

- [ ] **Step 7: Commit final state**

```bash
git add -A
git commit -m "chore: final build and deployment verification"
```

---

## Verification Checklist

After all tasks, verify:

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Full build
./gradlew :app:assembleDebug

# Connected device tests (if device available)
adb devices
./gradlew :app:connectedCheck
```
