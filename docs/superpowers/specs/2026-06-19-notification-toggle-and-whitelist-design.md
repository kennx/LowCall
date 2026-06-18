# Notification Toggle and Whitelist Design

<!-- Last updated: 2026-06-19 -->

## Goal

Add two capabilities to NoCall and verify them on a connected physical device:

1. A Settings toggle that controls whether the system notification is shown when a call is blocked. Blocking itself (reject + log) continues regardless of the toggle.
2. A whitelist that always allows a call through, evaluated before block rules. The whitelist is a dedicated Room table of manually added numbers, layered with a live contacts lookup: any number present in the device contacts is also treated as whitelisted. The blocked-call history exposes a one-tap "add to whitelist" action per blocked entry.

## Scope

- New persistent preference: notification enabled (boolean, default true).
- New Room entity `WhitelistEntry` and migration from DB v1 to v2.
- Live contacts lookup via `ContactsContract` with phone-number normalization (digits only).
- `BlockingCallScreeningService` rewrite to check whitelist (table + contacts) before rules and to gate the notification on the preference.
- Settings UI: notification `Switch`.
- Call history UI: "add to whitelist" affordance on blocked cards, disabled once added.
- New Whitelist screen + ViewModel for list/add/delete management.
- READ_CONTACTS runtime permission requested on first entry to the Whitelist screen.
- Physical-device verification of both features with real blocked and whitelisted numbers.

Out of scope: importing contacts into the whitelist table (contacts are queried live), distinguishing allow sources in the call log (both whitelist-allow and no-rule-allow stay `CallAction.ALLOWED`), libphonenumber-based E.164 normalization, and a whitelist detail/edit page.

## Design

### Data layer and preference storage

New dependency in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

- `datastorePreferences = "1.1.7"` (latest stable, Kotlin 2.2 / Compose BOM 2026.02 compatible).
- `androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastorePreferences" }`.

New Room entity in `data/model/WhitelistEntry.kt`:

```kotlin
@Entity(tableName = "whitelist", indices = [Index(value = ["normalized_number"], unique = true)])
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

The unique index on `normalized_number` makes `OnConflictStrategy.IGNORE` dedupe by digit-only form, so `+86 138-0013-8000` and `13800138000` cannot be added twice.

`AppDatabase` bumps to `version = 2`, adds `WhitelistDao` accessor, and registers `MIGRATION_1_2` (`CREATE TABLE whitelist (...)` + unique index). `fallbackToDestructiveMigration()` remains the safety net so a failed migration does not brick the app. `Converters` is unchanged (no enum on WhitelistEntry).

New `data/db/WhitelistDao.kt`:

```kotlin
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

New `data/prefs/SettingsRepository.kt` wrapping a `DataStore<Preferences>`. The `preferencesDataStore` delegate must be declared at file top level (Kotlin delegate syntax requires top-level `Context` extension), not inside the class:

```kotlin
private val Context.dataStore by preferencesDataStore("nocall_settings")

class SettingsRepository(private val context: Context) {
    private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")

    val notificationEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[NOTIFICATION_ENABLED] ?: true }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_ENABLED] = enabled }
    }
}
```

New `data/PhoneNormalizer.kt` (pure, unit-testable):

```kotlin
fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
```

Digits-only normalization strips `+`, spaces, hyphens, parentheses. It covers domestic and most international cases without libphonenumber, and it has no effect on existing rule matching (rules operate on the raw `schemeSpecificPart`; normalization is applied only to whitelist/contacts comparison).

New `data/ContactLookup.kt`:

```kotlin
class ContactLookup(private val context: Context) {
    fun isInContacts(phoneNumber: String): Boolean {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return false
        val tail = normalized.takeLast(minOf(normalized.length, 11))
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE '%$tail'",
            null, null
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

Query on `NUMBER` (`data1`, always populated) rather than `NORMALIZED_NUMBER` (`data4`, which the provider may leave null when it cannot infer E.164). The SQL `LIKE '%tail'` is a coarse pre-filter; the in-cursor `normalizePhone(stored).endsWith(tail)` check removes false positives from non-digit suffix overlap. The tail-suffix form tolerates `+8613800138000` vs `13800138000` storage differences without libphonenumber. `ContactLookup` requires a real `ContentResolver`, so it is covered by instrumented tests on the physical device, not local unit tests; `normalizePhone` itself is a pure unit-tested function.

`AppContainer` gains `whitelistDao`, `settingsRepository`, `contactLookup`.

### Blocking flow rewrite

`BlockingCallScreeningService.onScreenCall` becomes, in order:

1. Read `phoneNumber = details.handle?.schemeSpecificPart`; blank → return.
2. Whitelist check (new, absolute priority):
   - `normalized = normalizePhone(phoneNumber)`.
   - `inWhitelist = whitelistDao.exists(normalized) || contactLookup.isInContacts(phoneNumber)`.
   - If `inWhitelist`: insert `CallLog(action = ALLOWED, phoneNumber = original, matchedRuleId = null, matchedRulePattern = null)`, `respondToCall` with a neutral `CallResponse`, return. Rules are not consulted.
3. Rule match (unchanged): `ruleMatcher.match(phoneNumber, enabledRules)`.
   - Matched → insert BLOCKED log; read `notificationEnabled` via `runBlocking { settingsRepository.notificationEnabled.first() }`; if true, call `NotificationHelper.showBlockedCallNotification`; `respondToCall` with `setDisallowCall`/`setRejectCall`.
   - Not matched → insert ALLOWED log, neutral `respondToCall` (existing else branch).

`runBlocking` for the single boolean read matches the existing `runBlocking` DAO pattern in this binder-thread callback. The notification toggle never affects blocking — only the notification.

### UI

`SettingsScreen`: above the existing export/import/clear buttons, add a row with `Text(stringResource(R.string.notification_setting))` + `Switch(checked = state.notificationEnabled, onCheckedChange = viewModel::setNotificationEnabled)`, plus a one-line `Text` helper explaining "关闭后仍会拦截，但不再显示系统通知". `SettingsViewModel` exposes `notificationEnabled: StateFlow<Boolean>` (collected from `settingsRepository`) and `setNotificationEnabled(Boolean)`.

`CallHistoryScreen` / `CallLogCard`: when `log.action == BLOCKED`, render a row containing a `TextButton`/`AssistChip` "加入白名单". `CallHistoryViewModel` exposes `whitelistedNormalized: StateFlow<Set<String>>` from `whitelistDao.getAll().map { it.map(WhitelistEntry::normalizedNumber).toSet() }`. The card computes `added = normalizePhone(log.phoneNumber) in whitelistedNormalized`; if added, the button is disabled and reads "已加白名单", otherwise it calls `viewModel.addToWhitelist(log.phoneNumber)`. `addToWhitelist` inserts `WhitelistEntry(phoneNumber = original, normalizedNumber = normalizePhone(original), note = "")`.

New `WhitelistScreen` + `WhitelistViewModel`:
- `WhitelistViewModel.entries: StateFlow<List<WhitelistEntry>>` from `whitelistDao.getAll()`; `add(phone, note)` inserts with normalized number; `delete(id)`.
- `WhitelistScreen`: `TopAppBar` "白名单" + back; empty state "无白名单号码"; `LazyColumn` rows showing number + note + time with a delete `IconButton`; a `FloatingActionButton` opens an `AlertDialog` with two `OutlinedTextField`s (number, note) and a save `TextButton`.
- On first entry, if `READ_CONTACTS` is not granted, show a card "启用通讯录白名单" with a button that launches `ActivityResultContracts.RequestPermission()`. Once granted, the list shows. The permission is requested here only, not at app launch, to avoid stacking permission prompts.

Navigation: `NavRoutes.WHITELIST = "whitelist"` and `fun whitelist() = "whitelist"`. Entry point is a new `IconButton` in `RulesScreen` top bar (icons for History, Test, Whitelist, Settings). `MainActivity.NoCallNavHost` adds `composable(NavRoutes.WHITELIST) { WhitelistScreen(onNavigateBack = ...) }`.

`AndroidManifest.xml` adds `<uses-permission android:name="android.permission.READ_CONTACTS" />`.

### Strings

New `res/values/strings.xml` entries: `notification_setting`, `notification_setting_hint`, `add_to_whitelist`, `added_to_whitelist`, `whitelist_title`, `no_whitelist`, `whitelist_number`, `whitelist_note`, `enable_contacts_whitelist`, `enable_contacts_whitelist_message`, `whitelist_added_toast` (and any delete confirmation if used).

## Verification

### Automated

- `./gradlew :app:test` for pure logic: `normalizePhone` (digits-only normalization), whitelist DAO query construction, and whitelist dedupe-by-normalized-number semantics via existing Room test patterns. `SettingsRepository` and `ContactLookup` need Android runtime (`DataStore`/`ContentResolver`) and are covered by `connectedCheck` on the device rather than local unit tests.
- `./gradlew :app:lintDebug` and `git diff --check`.
- `./gradlew :app:connectedCheck` on the physical device when online.

### Physical device

Use connected device `00285361G001888` (A069, Android 16/API 36), which holds `android.app.role.CALL_SCREENING` for `cc.niaoer.nocall`.

- Install the debug APK without clearing app data (preserve existing rules and the v1 DB; the v1→v2 migration must run cleanly).
- Notification toggle: turn off in Settings, trigger a blocked call (or simulate), confirm no system notification appears but the call is still rejected and logged BLOCKED. Turn on, confirm the notification returns.
- Whitelist: from a blocked history card, tap "加入白名单"; confirm the button flips to "已加白名单" and a `whitelist` row exists in the DB. Trigger the same number again; confirm it is logged ALLOWED and not blocked, even though a rule still matches it.
- Contacts: grant READ_CONTACTS from the Whitelist screen, confirm a number saved as a contact is allowed even without a whitelist-table entry.
- Confirm NoCall remains the call-screening role holder; inspect logcat for crashes, migration errors, or ContactLookup failures.

A real carrier call is outside scope; deterministic numbers can be exercised via the emulator Telecom flow if needed, but the physical device is the primary verification target per the user's request.

## Success Criteria

- Settings notification toggle persists across restarts and gates only the system notification; blocking always occurs.
- DB migrates v1→v2 without data loss; existing rules and history survive.
- Whitelist table + live contacts both bypass blocking; whitelist is checked before rules.
- Blocked history "加入白名单" adds the number and disables itself once added.
- Whitelist screen lists, adds, and deletes entries; READ_CONTACTS is requested only there.
- All local unit tests pass; lint and `git diff --check` are clean.
- Physical-device checks above pass with logcat free of crashes.
