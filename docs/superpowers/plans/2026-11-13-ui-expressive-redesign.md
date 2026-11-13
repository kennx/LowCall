# LowCall Material 3 Expressive Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every LowCall screen with Material 3 Expressive and IconMCP solid icons while adding display-only phone formatting, offline landline attribution, and safe allowed-call-to-blacklist actions.

**Architecture:** Preserve the existing Activity, eight routes, AppContainer, Room schema, and screening service. Add small pure data utilities, enrich logs in ViewModels without rewriting rows, and share only the repeated Compose list/number primitives. Implement behavior before visual tasks, then verify the assembled app on the physical device.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Room, StateFlow, JUnit4, Android vector drawables.

**Spec:** `docs/superpowers/specs/2026-11-13-ui-expressive-redesign-design.md`

## Global Constraints

- Work only in `.worktrees/ui-expressive` on `feat/ui-expressive`; use `dev` as read-only behavioral reference.
- Preserve the backup at the original checkout's `backups/lowcall-20261113-103009/`; never uninstall or clear app data.
- minSdk 24, Java 11 bytecode, dynamic color gated to API 31+, no new dependency or Room schema change.
- Use `collectAsStateWithLifecycle`, StateFlow-based ViewModels, deterministic lazy keys, and at least 48dp touch targets.
- All visual tokens resolve through MaterialTheme; all user-visible strings and accessible labels use resources.
- Do not change rule priority, matching semantics, call response behavior, or the 500ms contacts timeout.
- Run `git diff --check` and the task's focused tests after each task; do not run ktlint or detekt.

## Review Focus

- Malformed international numbers, mixed extension separators, and service numbers must remain readable and must not receive fabricated attribution; pinned by Task 1 tests.
- `phone.dat` records with missing/blank area-code fields and unknown landline prefixes must return null without breaking mobile lookup; pinned by Task 2 tests.
- Existing enabled/disabled exact rules and differently formatted incoming numbers must not produce duplicates or alter unrelated rules; pinned by Task 3 tests.
- Long regex patterns, large font scale, and narrow screens must leave Save, Switch, and blacklist actions reachable; pinned by Tasks 5–7 Compose tests and Task 8 device screenshots.
- Installation and verification must preserve existing rules/logs/whitelist; pinned by Task 8 before/after count and integrity checks.

---

### Task 1: Display-Only Phone Formatter

**Files:**
- Create: `app/src/main/java/cc/niaoer/lowcall/data/PhoneDisplayFormatter.kt`
- Create: `app/src/test/java/cc/niaoer/lowcall/data/PhoneDisplayFormatterTest.kt`

**Interfaces:**
- Produces: `data class PhoneDisplay(val number: String, val extension: String? = null)`.
- Produces: `fun formatPhoneForDisplay(raw: String): PhoneDisplay`; pure Kotlin, no Android resources, no mutation of input or persisted values.

- [ ] **Write failing formatter tests** covering `13800138000 -> PhoneDisplay("138 0013 8000")`, `+8613800138000 -> PhoneDisplay("+86 138 0013 8000")`, `02169619151 -> PhoneDisplay("021 6961 9151")`, `073158329153 -> PhoneDisplay("0731 5832 9153")`, and `+12025550123 -> PhoneDisplay("+1 202 555 0123")`.
- [ ] **Add Review Focus tests** for `x`, `;`, `,`, and `转` extensions; blank input; letters; `12345`; unknown international prefixes; and repeated `+` characters. Assert extension digits are retained and malformed/service input is not assigned a country.
- [ ] **Run `./gradlew :app:testDebugUnitTest --tests '*PhoneDisplayFormatterTest'`** and confirm failure because the formatter does not exist.
- [ ] **Implement the formatter** with extension separation, Chinese mobile/landline rules, the two-digit Chinese area-code set (`10`, `20`–`29`), and conservative E.164 country-code grouping for other international input. Leave nonmatching service/malformed values readable.
- [ ] **Re-run the focused test** and confirm PASS.

### Task 2: Offline Landline Attribution

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/data/PhoneAttribution.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/service/BlockingCallScreeningService.kt`
- Modify: `app/src/test/java/cc/niaoer/lowcall/data/PhoneAttributionTest.kt`

**Interfaces:**
- Consumes: existing `AttributionResult(province, city, carrier)` and `PhoneAttribution.lookup(String)`.
- Produces: `fun lookupLandline(phoneNumber: String): AttributionResult?`; landline results use `carrier = ""` when no carrier exists.
- Produces: `fun lookupAny(phoneNumber: String): AttributionResult?` that selects mobile or landline lookup for callers.

- [ ] **Write failing landline tests** using the existing in-memory `phone.dat`: `02169619151` resolves to the record associated with area code `021`; `075512345678` resolves to Shenzhen; unknown `099912345678` returns null; existing mobile assertions remain unchanged.
- [ ] **Add Review Focus tests** for blank/missing fourth record fields and records without area codes; they must be skipped without index corruption.
- [ ] **Run `./gradlew :app:testDebugUnitTest --tests '*PhoneAttributionTest'`** and confirm the new tests fail.
- [ ] **Implement a lazily built area-code map** by scanning records only inside the existing record area `(8 until indexOffset)`, splitting fields defensively, and indexing nonblank area codes once. `lookupAny` preserves the current seven-digit mobile binary search and uses the map for Chinese landlines.
- [ ] **Use `lookupAny` in `BlockingCallScreeningService`** so new landline logs and notifications receive location without changing the call-response flow.
- [ ] **Run the focused test and then `./gradlew :app:test`**, confirming PASS.
- [ ] **Measure real-asset first lookup and cached lookup on the connected device** during Task 8; do not add a new I/O operation to `onScreenCall`.

### Task 3: Allowed-Call Blacklist State And Persistence

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/data/db/BlockRuleDao.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryViewModel.kt`
- Modify: `app/src/androidTest/java/cc/niaoer/lowcall/ui/history/CallHistoryScreenTest.kt`
- Create: `app/src/androidTest/java/cc/niaoer/lowcall/ui/history/CallHistoryViewModelTest.kt`

**Interfaces:**
- Produces: `suspend fun BlockRuleDao.findExact(pattern: String): BlockRule?` using exact pattern and `RuleType.EXACT` equality.
- Produces: `data class CallHistoryUiState(logs: List<CallLog> = emptyList(), whitelistedNumbers: Set<String> = emptySet(), exactBlockPatterns: Set<String> = emptySet(), feedback: CallHistoryFeedback? = null)`.
- Produces: `enum class CallHistoryFeedback { BLACKLIST_ADDED, BLACKLIST_REACTIVATED, BLACKLIST_EXISTS, BLACKLIST_FAILED }`.
- Produces: `val CallHistoryViewModel.uiState: StateFlow<CallHistoryUiState>`, `fun addToBlacklist(phoneNumber: String)`, `fun consumeFeedback()`, and existing `addToWhitelist`/`clearAll`.
- Enriches null locations in the exposed in-memory log copies through `lookupAny`; never rewrites database rows.

- [ ] **Write failing ViewModel tests** for inserting an enabled exact rule with the original logged phone string, doing nothing for blank numbers, returning EXISTS for an enabled duplicate, and re-enabling a disabled exact rule without insertion.
- [ ] **Add Review Focus tests** proving a different exact rule is untouched, repeated calls do not duplicate rows, and old null-location logs receive display-only landline attribution.
- [ ] **Run the connected focused tests** and confirm compilation/behavior fails before implementation.
- [ ] **Implement the DAO query and unified UI state** by combining logs, whitelist, and block rules; wrap persistence launches in try/catch and map failures to `BLACKLIST_FAILED`.
- [ ] **Run the focused connected tests and `./gradlew :app:test`**, confirming PASS and unchanged Room schema version 4.

### Task 4: Theme, Solid Icons, And Shared Presentation Primitives

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/theme/Color.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/theme/Shape.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/theme/Theme.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/common/ListGroup.kt`
- Create: `app/src/main/java/cc/niaoer/lowcall/ui/common/PhoneNumberText.kt`
- Create: `app/src/main/res/drawable/ic_home_solid.xml`, `ic_rule_solid.xml`, `ic_history_solid.xml`, `ic_whitelist_solid.xml`, `ic_settings_solid.xml`, `ic_add_solid.xml`, `ic_search_solid.xml`, `ic_delete_solid.xml`, `ic_import_solid.xml`, `ic_export_solid.xml`, `ic_notification_solid.xml`, `ic_blocked_solid.xml`, `ic_allowed_solid.xml`, `ic_test_solid.xml`, `ic_back_solid.xml`, `ic_close_solid.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PhoneDisplay` and `formatPhoneForDisplay` from Task 1.
- Produces: `@Composable fun ListGroup(modifier: Modifier = Modifier, content: ColumnScope.() -> Unit)` using a tonal Surface and MaterialTheme shape.
- Produces: `@Composable fun PhoneNumberText(phoneNumber: String, modifier: Modifier = Modifier)` rendering formatted national number and localized extension.
- Icon mapping: `home`, `firewall-rule`, `phone-history`, `verified-profile`, `settings`, `plus`, `search`, `trash`, `file-down`, `database-export`, `bell`, `phone-blocked`, `phone-check`, `test-tube`, `arrow-left`, `close` from IconMCP `get_icon(style="solid", size=24)`.

- [ ] **Fetch and inspect each actual solid SVG** through IconMCP; convert to 24dp vector drawables with opaque black source fills and apply MaterialTheme tint through Compose `Icon`. Do not hand-draw paths.
- [ ] **Update fallback colors and shape tokens** to the accessible Material 3 roles and 4/8/12/16/24dp scale in `DESIGN.md`; preserve the API 31 dynamic-color branch.
- [ ] **Implement ListGroup and PhoneNumberText**, with `Modifier` as the last optional parameter and localized extension formatting.
- [ ] **Run `./gradlew :app:compileDebugKotlin :app:lintDebug`** and confirm PASS.

### Task 5: Home Dashboard And Navigation Shell

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/MainActivity.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/service/NotificationHelper.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/cc/niaoer/lowcall/MainActivityNavigationTest.kt`

**Interfaces:**
- Consumes: Task 2 `lookupAny`, Task 4 icons/list/number primitives.
- Preserves: `LowCallApp(screeningEnabled: Boolean, onSetup: () -> Unit)` and all existing route signatures.

- [ ] **Update navigation tests first** for the five solid-icon destinations, shorter navigation-only labels, existing route transitions, and the no-`restoreState` regression scenario.
- [ ] **Rebuild the root shell and Home dashboard** with protection status, total/today/week hierarchy, two quick actions, and grouped recent calls. Replace `collectAsState` with `collectAsStateWithLifecycle`.
- [ ] **Enrich Home's recent null-location logs in memory** via `lookupAny` with `flowOn(Dispatchers.IO)`; do not update Room rows.
- [ ] **Apply display formatting to blocked-call notifications** while preserving channel initialization and permission behavior.
- [ ] **Run `./gradlew :app:test` and the navigation connected test**, confirming PASS.

### Task 6: Rules, Rule Editor, And Rule Test Screens

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RulesScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/rules/RuleEditScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/test/RuleTestScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/cc/niaoer/lowcall/ui/rules/RuleEditScreenTest.kt`

**Interfaces:**
- Consumes: Task 4 solid icons and ListGroup.
- Preserves: RulesViewModel filtering/toggle APIs, RuleEditViewModel validation APIs, and RuleTestViewModel behavior.

- [ ] **Write Compose tests** for description-first rule rows, raw pattern supporting text, enabled Switch, rule-type selection, Save reachability with a long regex, and blocked/allowed test results.
- [ ] **Redesign Rules as a grouped list** with localized search, type tabs, FAB clearance, no static AssistChips, and accessible solid controls.
- [ ] **Replace the custom selector with Material 3 SegmentedButton** and rebuild editor/test actions and results according to `DESIGN.md`, preserving regex suggestion and deletion behavior.
- [ ] **Run rule-focused connected tests, `:app:test`, and `:app:lintDebug`**, confirming PASS.

### Task 7: History, Whitelist, And Settings Screens

**Files:**
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/history/CallHistoryScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/whitelist/WhitelistScreen.kt`
- Modify: `app/src/main/java/cc/niaoer/lowcall/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/cc/niaoer/lowcall/ui/history/CallHistoryScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 `CallHistoryUiState`, `addToBlacklist`, `consumeFeedback`; Task 4 primitives.
- Produces: localized confirmation dialog, success/existing/failure Snackbars, formatted old/new phone rows, and allowed/blocked contextual actions.

- [ ] **Update Compose tests** for formatted numbers, all four allow-reason cases, blocked Add to Whitelist, allowed Add to Blacklist, confirmation/cancellation, duplicate state, and feedback Snackbar.
- [ ] **Add narrow/large-font coverage** showing that long metadata wraps or ellipsizes without hiding the blacklist action.
- [ ] **Rebuild History with date-grouped ListGroups**, no status AssistChips or “ring 0 seconds”, and the confirmed blacklist flow.
- [ ] **Rebuild Whitelist and Settings with grouped rows and actionable empty states**, retaining search, contacts permission, import/export, notification, and clear-history behavior.
- [ ] **Run history-focused connected tests, `:app:test`, and `:app:lintDebug`**, confirming PASS.

### Task 8: Full Physical-Device Verification And Screenshots

**Files:**
- Create: ignored `build/ui-audit-after/*.png`
- Modify only if evidence exposes a regression: files owning that regression.

**Interfaces:**
- Consumes: the complete app from Tasks 1–7 and original `dev` screenshots/behavior.

- [ ] **Run `adb devices`, `git diff --check`, `./gradlew :app:test`, and `./gradlew :app:lintDebug`**; all must exit 0.
- [ ] **Install the debug build with data preservation**, then open the copied database read-only and assert integrity plus baseline counts: 9 rules, 19 logs, 0 whitelist entries before any deliberate UI mutation.
- [ ] **Run `./gradlew :app:connectedCheck`**, confirm exit 0, and re-check baseline data before deliberate UI mutations.
- [ ] **Exercise every route on the physical device**, capturing Home, Rules, History, Whitelist, Settings, Add Rule, Edit Rule, and Rule Test in dark mode; capture Home, History, and Settings in light mode.
- [ ] **Verify real interactions** for search/filter, rule add/edit/test, whitelist add/delete, blacklist confirmation/cancellation, Snackbar feedback, duplicate prevention, and disabled-rule reactivation. Restore or remove only test-created fixtures, never baseline user data.
- [ ] **Measure first and cached real `phone.dat` lookups**, verify old landline rows gain display-only attribution, and confirm mobile/landline/international formatting on actual screens and a notification where feasible.
- [ ] **Inspect every PNG visually**, compare with `build/ui-audit-before/`, and fix only verified clipping, overlap, contrast, or interaction regressions before repeating the affected checks.
- [ ] **Run `git status` and remove only generated files not needed for delivery**; retain intentional screenshots in ignored build output and leave the original backup untouched.
