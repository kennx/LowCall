# Caller Attribution (来电归属地) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline phone number location (province+city) and carrier lookup, displayed on system incoming call panel (allowed calls), block notifications, home screen recent-blocked cards, and call history list.

**Architecture:** Bundle `phone.dat` (~4.7 MB) in assets. A single `PhoneAttribution` class (ByteBuffer + binary search, zero dependencies) loads once at `AppContainer` creation. `BlockingCallScreeningService` queries attribution for every screened call. For ALLOWED calls, sets `CallResponse.setCallerId()` (API 29+). Attribution data (`location`, `carrier`) is persisted in `CallLog` and flows to UI through existing `Flow` DAO queries.

**Tech Stack:** Kotlin, `java.nio.ByteBuffer`, Room (v2→v3 migration), Jetpack Compose, `CallScreeningService`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| CREATE | `data/PhoneAttribution.kt` | Binary search parser for `phone.dat`; returns `AttributionResult?` |
| CREATE | `app/src/main/assets/phone.dat` | Offline phone number attribution database |
| MODIFY | `data/model/CallLog.kt` | Add `location: String?` and `carrier: String?` fields |
| MODIFY | `data/db/AppDatabase.kt` | v2→v3 migration, version bump to 3 |
| MODIFY | `AppContainer.kt` | Load `PhoneAttribution` from assets, expose as property |
| MODIFY | `service/BlockingCallScreeningService.kt` | Lookup attribution, persist to CallLog, setCallerId for ALLOWED |
| MODIFY | `service/NotificationHelper.kt` | Accept and display location/carrier in notification |
| MODIFY | `ui/home/HomeScreen.kt` | Show attribution line in `RecentBlockedItem` |
| MODIFY | `ui/history/CallHistoryScreen.kt` | Show attribution line in `CallLogItem` |
| MODIFY | `res/values/strings.xml` | Add `attribution_format` string |

---

### Task 1: Download phone.dat and place in assets

**Files:**
- Create: `app/src/main/assets/phone.dat`

- [ ] **Step 1: Download phone.dat**

```bash
mkdir -p app/src/main/assets
curl -L -o app/src/main/assets/phone.dat \
  https://raw.githubusercontent.com/EeeMt/phone-number-geo/master/src/main/resources/phone.dat
```

- [ ] **Step 2: Verify file size**

```bash
ls -lh app/src/main/assets/phone.dat
# Expected: ~4.7 MB
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/phone.dat
git commit -m "chore(data): add phone.dat attribution database"
```

---

### Task 2: Add attribution format string resource

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add string resource**

Append before `</resources>`:

```xml
<string name="attribution_format">%1$s · %2$s</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(ui): add attribution_format string resource"
```

---

### Task 3: Create PhoneAttribution lookup engine

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/data/PhoneAttribution.kt`

- [ ] **Step 1: Create PhoneAttribution.kt**

```kotlin
package cc.niaoer.nocall.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AttributionResult(
    val province: String,
    val city: String,
    val carrier: String
)

class PhoneAttribution(data: ByteArray) {

    private val buffer = ByteBuffer.wrap(data)
        .asReadOnlyBuffer()
        .order(ByteOrder.LITTLE_ENDIAN)

    private val indexOffset: Int

    init {
        // skip version (4 bytes), read first index offset (4 bytes)
        indexOffset = buffer.getInt(4)
    }

    fun lookup(phoneNumber: String): AttributionResult? {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.length < 7) return null
        val prefix = normalized.substring(0, 7).toIntOrNull() ?: return null

        val indexCount = (buffer.capacity() - indexOffset) / INDEX_ENTRY_SIZE
        var left = 0
        var right = indexCount - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val pos = indexOffset + mid * INDEX_ENTRY_SIZE

            buffer.position(pos)
            val midPrefix = buffer.int

            when {
                midPrefix == prefix -> {
                    val recordOffset = buffer.int
                    val ispCode = buffer.get().toInt() and 0xFF
                    val record = readRecord(recordOffset) ?: return null
                    val parts = record.split("|")
                    if (parts.size < 2) return null
                    return AttributionResult(
                        province = parts[0],
                        city = parts[1],
                        carrier = ispToCarrier(ispCode)
                    )
                }
                midPrefix > prefix -> right = mid - 1
                else -> left = mid + 1
            }
        }
        return null
    }

    private fun readRecord(offset: Int): String? {
        buffer.position(offset)
        val sb = StringBuilder()
        while (true) {
            val b = buffer.get()
            if (b == 0.toByte()) break
            sb.append(b.toInt().toChar())
        }
        val result = sb.toString()
        return result.ifEmpty { null }
    }

    private fun ispToCarrier(code: Int): String = when (code) {
        1 -> "中国移动"
        2 -> "中国联通"
        3 -> "中国电信"
        4 -> "中国电信虚拟运营商"
        5 -> "中国联通虚拟运营商"
        6 -> "中国移动虚拟运营商"
        7 -> "中国广电"
        8 -> "中国广电虚拟运营商"
        else -> "未知运营商"
    }

    companion object {
        private const val INDEX_ENTRY_SIZE = 9
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/PhoneAttribution.kt
git commit -m "feat(data): add PhoneAttribution offline phone.dat lookup engine"
```

---

### Task 4: Write unit tests for PhoneAttribution

**Files:**
- Create: `app/src/test/java/cc/niaoer/nocall/data/PhoneAttributionTest.kt`

- [ ] **Step 1: Create test class**

```kotlin
package cc.niaoer.nocall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhoneAttributionTest {

    private lateinit var attribution: PhoneAttribution

    @Before
    fun setUp() {
        // Build a minimal valid phone.dat in memory
        // Records: "广东|深圳|518000|0755\0"
        //           "北京|北京|100000|010\0"
        val record1 = "广东|深圳|518000|0755".toByteArray() + byteArrayOf(0)
        val record2 = "北京|北京|100000|010".toByteArray() + byteArrayOf(0)
        val recordArea = record1 + record2

        val headerSize = 8
        val recordAreaStart = headerSize
        val indexOffset = recordAreaStart + recordArea.size

        // Index entries (9 bytes each): [prefix:4 LE][record_offset:4 LE][isp_code:1]
        val entry1 = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1380013)          // prefix 1380013
            putInt(recordAreaStart)  // offset to "广东|深圳..."
            put(1)                   // 中国移动
        }.array()

        val entry2 = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1390000)          // prefix 1390000
            putInt(recordAreaStart + record1.size)  // offset to "北京|北京..."
            put(2)                   // 中国联通
        }.array()

        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1701)
            putInt(indexOffset)
        }.array()

        val fullData = header + recordArea + entry1 + entry2
        attribution = PhoneAttribution(fullData)
    }

    @Test
    fun `lookup known prefix returns attribution`() {
        val result = attribution.lookup("13800138000")
        assertNotNull(result)
        assertEquals("广东", result!!.province)
        assertEquals("深圳", result.city)
        assertEquals("中国移动", result.carrier)
    }

    @Test
    fun `lookup second known prefix`() {
        val result = attribution.lookup("13900001111")
        assertNotNull(result)
        assertEquals("北京", result!!.province)
        assertEquals("北京", result.city)
        assertEquals("中国联通", result.carrier)
    }

    @Test
    fun `lookup unknown prefix returns null`() {
        val result = attribution.lookup("13099998888")
        assertNull(result)
    }

    @Test
    fun `lookup short number returns null`() {
        val result = attribution.lookup("123")
        assertNull(result)
    }

    @Test
    fun `lookup non-numeric string returns null`() {
        val result = attribution.lookup("abcdefg")
        assertNull(result)
    }

    @Test
    fun `lookup with extra characters in number`() {
        // normalizePhone strips non-digits, so "+86 138-0013-8000" → "8613800138000"
        // The first 7 digits would be "8613800" which won't match our test data
        val result = attribution.lookup("+8613800138000")
        assertNotNull(result)
        assertEquals("广东", result!!.province)
    }

    @Test
    fun `isp code 3 maps to China Telecom`() {
        // Build a minimal phone.dat with isp code 3
        val record = "上海|上海|200000|021".toByteArray() + byteArrayOf(0)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1701)
            putInt(8 + record.size) // indexOffset right after record area
        }.array()
        val index = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1330000)
            putInt(8) // record at byte 8
            put(3)    // 中国电信
        }.array()
        val data = header + record + index
        val attr = PhoneAttribution(data)

        val result = attr.lookup("13300001111")
        assertNotNull(result)
        assertEquals("中国电信", result!!.carrier)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "cc.niaoer.nocall.data.PhoneAttributionTest"
```
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/cc/niaoer/nocall/data/PhoneAttributionTest.kt
git commit -m "test(data): add PhoneAttribution unit tests"
```

---

### Task 5: Add location/carrier fields to CallLog entity

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/data/model/CallLog.kt`

- [ ] **Step 1: Add new fields**

Edit `CallLog.kt` — add two nullable fields **after** `timestamp`:

```kotlin
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
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "carrier") val carrier: String? = null
)
```

(Changes: add `location` and `carrier` fields with `String? = null` defaults)

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/model/CallLog.kt
git commit -m "feat(data): add location and carrier fields to CallLog entity"
```

---

### Task 6: Database migration v2 → v3

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/data/db/AppDatabase.kt`

- [ ] **Step 1: Add migration and bump version**

In `AppDatabase.kt`:
1. Change `version = 2` to `version = 3` (line 17)
2. Add `MIGRATION_2_3` object in `companion object` after `MIGRATION_1_2`:

```kotlin
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `call_logs` ADD COLUMN `location` TEXT")
        db.execSQL("ALTER TABLE `call_logs` ADD COLUMN `carrier` TEXT")
    }
}
```

3. Update the migrations list in `create()`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/data/db/AppDatabase.kt
git commit -m "feat(db): add v2 to v3 migration for location and carrier columns"
```

---

### Task 7: Register PhoneAttribution in AppContainer

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/AppContainer.kt`

- [ ] **Step 1: Add phoneAttribution property**

In `AppContainer`, add after `settingsRepository` line:

```kotlin
val phoneAttribution: PhoneAttribution by lazy {
    val data = context.assets.open("phone.dat").use { it.readBytes() }
    PhoneAttribution(data)
}
```

Full file after edit:

```kotlin
package cc.niaoer.nocall

import android.content.Context
import cc.niaoer.nocall.data.PhoneAttribution
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
    val phoneAttribution: PhoneAttribution by lazy {
        val data = context.assets.open("phone.dat").use { it.readBytes() }
        PhoneAttribution(data)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/AppContainer.kt
git commit -m "feat(di): register PhoneAttribution in AppContainer"
```

---

### Task 8: Update BlockingCallScreeningService

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt`

- [ ] **Step 1: Add attribution lookup and setCallerId**

The key changes:
1. Look up attribution once at the top of `runBlocking` (before whitelist check)
2. Pass `location` and `carrier` to every `CallLog` insertion
3. For ALLOWED calls, call `setCallerId()` on the response (API 29+)
4. Pass `location` and `carrier` to `NotificationHelper.showBlockedCallNotification()`

Replace `BlockingCallScreeningService.kt` with:

```kotlin
package cc.niaoer.nocall.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import cc.niaoer.nocall.NoCallApplication
import cc.niaoer.nocall.data.isInContacts
import cc.niaoer.nocall.data.match
import cc.niaoer.nocall.data.model.CallAction
import cc.niaoer.nocall.data.model.CallLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

@RequiresApi(Build.VERSION_CODES.N)
class BlockingCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "BlockingCallScreening"
        private const val CONTACT_LOOKUP_TIMEOUT_MS = 500L
    }

    override fun onScreenCall(details: Call.Details) {
        val phoneNumber = details.handle?.schemeSpecificPart
        if (phoneNumber.isNullOrBlank()) {
            return
        }

        val container = (application as NoCallApplication).appContainer

        runBlocking {
            val attribution = container.phoneAttribution.lookup(phoneNumber)
            val location = attribution?.let { "${it.province}${it.city}" }
            val carrier = attribution?.carrier

            val normalized = cc.niaoer.nocall.data.normalizePhone(phoneNumber)

            val inWhitelist = container.whitelistDao.exists(normalized) ||
                withTimeoutOrNull(CONTACT_LOOKUP_TIMEOUT_MS) {
                    isInContacts(this@BlockingCallScreeningService, phoneNumber)
                } ?: false

            if (inWhitelist) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        location = location,
                        carrier = carrier,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val responseBuilder = CallResponse.Builder()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val callerId = buildCallerId(location, carrier)
                    if (callerId != null) {
                        responseBuilder.setCallerId(callerId)
                    }
                }
                respondToCall(details, responseBuilder.build())
                return@runBlocking
            }

            val enabledRules = container.blockRuleDao.getEnabledList()
            val matched = match(phoneNumber, enabledRules)

            if (matched != null) {
                container.callLogDao.insert(
                    CallLog(
                        phoneNumber = phoneNumber,
                        matchedRuleId = matched.id,
                        matchedRulePattern = matched.pattern,
                        location = location,
                        carrier = carrier,
                        action = CallAction.BLOCKED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val notificationEnabled = container.settingsRepository
                    .notificationEnabled.first()
                if (notificationEnabled) {
                    val ruleDesc = matched.description.ifBlank { matched.pattern }
                    NotificationHelper.showBlockedCallNotification(
                        context = this@BlockingCallScreeningService,
                        phoneNumber = phoneNumber,
                        location = location,
                        carrier = carrier,
                        ruleDescription = ruleDesc
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
                        location = location,
                        carrier = carrier,
                        action = CallAction.ALLOWED,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val responseBuilder = CallResponse.Builder()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val callerId = buildCallerId(location, carrier)
                    if (callerId != null) {
                        responseBuilder.setCallerId(callerId)
                    }
                }
                respondToCall(details, responseBuilder.build())
            }
        }
    }

    private fun buildCallerId(location: String?, carrier: String?): String? {
        return when {
            location != null && carrier != null -> "$location · $carrier"
            location != null -> location
            carrier != null -> carrier
            else -> null
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt
git commit -m "feat(service): add caller attribution to screening and setCallerId"
```

---

### Task 9: Update NotificationHelper

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/service/NotificationHelper.kt`

- [ ] **Step 1: Add location/carrier parameters**

Change the `showBlockedCallNotification` function signature and body:

```kotlin
fun showBlockedCallNotification(
    context: Context,
    phoneNumber: String,
    location: String?,
    carrier: String?,
    ruleDescription: String
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createChannel(context)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val attributionPart = buildString {
        if (location != null) append(location)
        if (location != null && carrier != null) append(" · ")
        if (carrier != null) append(carrier)
    }

    val contentText = if (attributionPart.isNotEmpty()) {
        "号码: $phoneNumber · $attributionPart"
    } else {
        "号码: $phoneNumber"
    }

    val description = if (ruleDescription.isNotBlank()) {
        "规则: $ruleDescription"
    } else {
        "已拦截来电"
    }

    val bigText = "$contentText\n$description"

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("拦截来电")
        .setContentText(contentText)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(bigText)
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(phoneNumber.hashCode(), notification)
        }
    } else {
        NotificationManagerCompat.from(context)
            .notify(phoneNumber.hashCode(), notification)
    }
}
```

The full file after edit keeps all imports unchanged, only the function body changes.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/service/NotificationHelper.kt
git commit -m "feat(notification): display caller attribution in blocked call notification"
```

---

### Task 10: Show attribution in HomeScreen RecentBlockedItem

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add attribution line to RecentBlockedItem**

In `RecentBlockedItem`, add an attribution line between the phone number line and the rule+time line. Replace the `RecentBlockedItem` composable (lines 237-281):

```kotlin
@Composable
private fun RecentBlockedItem(log: CallLog) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (log.location != null && log.carrier != null) {
                    Text(
                        text = stringResource(
                            R.string.attribution_format,
                            log.location,
                            log.carrier
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${log.matchedRulePattern ?: "未知规则"} · ${dateFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/home/HomeScreen.kt
git commit -m "feat(ui): show caller attribution in home recent blocked cards"
```

---

### Task 11: Show attribution in CallHistoryScreen CallLogItem

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryScreen.kt`

- [ ] **Step 1: Add attribution line to CallLogItem**

In `CallLogItem`, after the phone number line and before the action subtitle line, add the attribution display. Replace the `CallLogItem` composable (lines 155-240) — only the text content area (lines 199-215) changes. Keep everything else identical.

Old content area (lines 199-215):
```kotlin
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isBlocked) {
                            "${log.matchedRulePattern ?: "未知规则"} · 响铃 0 秒"
                        } else {
                            "已放行 · 白名单匹配"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

Replace with:
```kotlin
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.location != null && log.carrier != null) {
                        Text(
                            text = stringResource(
                                R.string.attribution_format,
                                log.location,
                                log.carrier
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isBlocked) {
                            "${log.matchedRulePattern ?: "未知规则"} · 响铃 0 秒"
                        } else {
                            "已放行 · 白名单匹配"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/cc/niaoer/nocall/ui/history/CallHistoryScreen.kt
git commit -m "feat(ui): show caller attribution in call history list"
```

---

### Task 12: Final verification

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: All tests pass, including new `PhoneAttributionTest` and existing tests.

- [ ] **Step 2: Run lint**

```bash
./gradlew :app:lintDebug
```
Expected: No new lint errors.

- [ ] **Step 3: git diff check**

```bash
git diff --check
```
Expected: No whitespace issues.

- [ ] **Step 4: Verify all commits**

```bash
git log --oneline -12
```
Expected: 12 commits, one per task.

---

## Verification Summary

| Check | Command | Expected |
|-------|---------|----------|
| Unit tests | `./gradlew :app:testDebugUnitTest` | All pass |
| Lint | `./gradlew :app:lintDebug` | No new errors |
| Whitespace | `git diff --check` | Clean |
