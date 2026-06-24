# NoCall Agent Protocol (AI-Native)

<!-- Last updated: 2026-06-25 -->

[Constraint]: This is a strict state-machine execution protocol, NOT a human guide. Any deviation from these rules constitutes a critical task failure.

## 1. System Architecture & Topological Map
[Execution_Context]: Before modifying ANY file, you MUST cross-reference its path with this routing matrix to determine your exact permission boundaries.

- `[Path: app/src/main/java/cc/niaoer/nocall/NoCallApplication.kt]`
  - STATUS: Application entry point. Initializes `AppContainer` in `onCreate()`. Exposes `appContainer` as `lateinit`.
- `[Path: app/src/main/java/cc/niaoer/nocall/MainActivity.kt]`
  - STATUS: Sole Activity. Hosts `NoCallNavHost` (8 routes), 5-tab bottom nav, call screening role management, notification permission request. Applies `enableEdgeToEdge()`. Contains `NoCallApp`, `SetupBanner`, `NoCallNavHost` composables.
  - CONSTRAINT: `restoreState` is intentionally OMITTED from bottom-nav `navigate()` calls to prevent Navigation Compose from restoring wrong destination on tab switches.
- `[Path: app/src/main/java/cc/niaoer/nocall/AppContainer.kt]`
  - STATUS: Manual DI container. Holds `AppDatabase`, `BlockRuleDao`, `CallLogDao`, `WhitelistDao`, `SettingsRepository`. Created by `NoCallApplication`, accessed via `(application as NoCallApplication).appContainer`.
  - CONSTRAINT: This is the sole source of singleton dependencies. DO NOT introduce new dependency instantiation points elsewhere.
- `[Path: app/src/main/java/cc/niaoer/nocall/data/RuleMatcher.kt]`
  - STATUS: Core matching engine. Top-level functions: `looksLikeRegex(pattern)`, `isValidRegex(pattern)`, `match(phoneNumber, rules)`. Iterates enabled rules (EXACT → WILDCARD → REGEX), returns first match or null.
  - CONSTRAINT: WILDCARD conversion (`*` → `.*`, `?` → `.`) happens inline in `match()`. REGEX patterns MUST be validated via `isValidRegex()` before insertion. DO NOT change match priority order without updating `RuleMatcherTest`.
- `[Path: app/src/main/java/cc/niaoer/nocall/data/ContactLookup.kt]`
  - STATUS: Top-level function `isInContacts(context, phoneNumber)` queries `ContactsContract` via LIKE matching on last 11 digits. Called from `BlockingCallScreeningService` with a 500ms timeout.
- `[Path: app/src/main/java/cc/niaoer/nocall/data/PhoneNormalizer.kt]`
  - STATUS: Single top-level function `normalizePhone(raw)` strips all non-digit characters.
- `[Path: app/src/main/java/cc/niaoer/nocall/data/db/.*]`
  - STATUS: Room persistence layer. `AppDatabase` (v2, 3 entities: `BlockRule`, `CallLog`, `WhitelistEntry`). Three DAOs expose `Flow<List<T>>` for reactive reads and suspend functions for writes. `Converters` handles `RuleType`/`CallAction` enum serialization.
  - CONSTRAINT: Schema changes to entities MUST be backward-tolerant and MUST include a `Migration` object. `fallbackToDestructiveMigration()` is a last-resort safety net, NOT an excuse to skip writing migrations. New DAO methods returning `Flow` MUST use the `Flow` return type (not `LiveData`).
- `[Path: app/src/main/java/cc/niaoer/nocall/data/model/.*]`
  - STATUS: Room entity definitions. `BlockRule` (pattern, ruleType, enabled, description, createdAt), `CallLog` (phoneNumber, matchedRuleId, matchedRulePattern, action, timestamp), `WhitelistEntry` (phoneNumber, normalizedNumber, note, createdAt).
  - CONSTRAINT: Entity fields added after the initial schema MUST have default values to maintain backward compatibility with existing rows.
- `[Path: app/src/main/java/cc/niaoer/nocall/data/prefs/SettingsRepository.kt]`
  - STATUS: DataStore Preferences wrapper. Exposes `notificationEnabled: Flow<Boolean>` (default true) and `setNotificationEnabled()`.
  - CONSTRAINT: DO NOT add new DataStore keys without default values. All preferences MUST use `Flow<T>` for reads.
- `[Path: app/src/main/java/cc/niaoer/nocall/service/BlockingCallScreeningService.kt]`
  - STATUS: Call screening entry point (API 24+). `onScreenCall()` executes on binder thread: whitelist check (DB + contacts with 500ms timeout) → rule matching → call blocking/rejection via `CallResponse.Builder()`. Logs all calls to `CallLog` table.
  - CONSTRAINT: `runBlocking` is used here because `CallScreeningService` requires a synchronous response before the system rings the call. The work inside the coroutine is bounded by the contacts lookup timeout. DO NOT add unbounded blocking operations inside this coroutine.
- `[Path: app/src/main/java/cc/niaoer/nocall/service/NotificationHelper.kt]`
  - STATUS: Singleton object. Creates notification channel "call_blocking" (API 26+), shows blocked-call notification with phone number and rule description. Respects `POST_NOTIFICATIONS` permission on API 33+.
- `[Path: app/src/main/java/cc/niaoer/nocall/ui/theme/.*]`
  - STATUS: Design token layer. `Color.kt` (Purple/Pink palette constants), `Theme.kt` (dynamic/static theme switching), `Type.kt` (Typography), `Shape.kt` (rounded corners).
  - CONSTRAINT: `[Dynamic_Color]` gate in `Theme.kt` (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`) MUST be preserved. ALL new color tokens MUST be defined in `Color.kt`, NOT inline in components. When removing a feature, you MUST grep `Color.kt` for tokens it introduced and delete any that are now unreferenced.
- `[Path: app/src/main/java/cc/niaoer/nocall/ui/navigation/NavRoutes.kt]`
  - STATUS: Route constants. HOME, RULES, RULE_ADD, RULE_EDIT ("rules/{ruleId}"), RULE_TEST, HISTORY, SETTINGS, WHITELIST. Helper `ruleEdit(id)` constructs parameterized route.
- `[Path: app/src/main/java/cc/niaoer/nocall/ui/(home|rules|history|whitelist|settings|test)/.*ViewModel\.kt]`
  - STATUS: Screen state holders. All extend `AndroidViewModel(context)` to access `AppContainer`. Expose state as `StateFlow<UiState>`, receive events via function calls.
  - MANDATORY: Expose state EXCLUSIVELY as `StateFlow<UiState>`. Use `viewModelScope.launch` for async operations. Access DAOs via `(application as NoCallApplication).appContainer`.
  - BANNED: `runBlocking` in ViewModels. Direct `GlobalScope` usage.
- `[Path: app/src/main/java/cc/niaoer/nocall/ui/(home|rules|history|whitelist|settings|test)/.*Screen\.kt]`
  - STATUS: Stateless Compose UI. Receive data via params, emit events via lambdas. Top-level screen composables may call composable ViewModel accessors (no direct `viewModel()` injection in child components).
  - BANNED: Hardcoded raw colors (`Color(0xFF...)`), hardcoded user-visible strings, `collectAsState()` (use `collectAsStateWithLifecycle()`).
  - MANDATORY: `modifier: Modifier` MUST be the LAST optional parameter defaulting to `Modifier`. ALL colors MUST resolve via `MaterialTheme.colorScheme`. ALL strings MUST resolve via `stringResource()`.
- `[Path: app/src/main/java/cc/niaoer/nocall/ui/settings/RuleImport.kt]`
  - STATUS: Import validation logic. `filterValidRules(rules)` accepts JSON arrays of rule objects, rejects blank/invalid patterns, returns `ImportFilterResult`.
  - CONSTRAINT: Validation logic changes MUST be mirrored in `RuleImportTest.kt`.

## 2. Hard Environment & Compilation Directives
- `[API_Boundary]`: minSdk = 24. NEVER use APIs requiring SDK > 24 unless explicitly gated inside `Build.VERSION.SDK_INT >= ...`. Known gates: `RoleManager.ROLE_CALL_SCREENING` (API 29+), dynamic color (API 31+), `POST_NOTIFICATIONS` permission (API 33+), notification channel (API 26+).
- `[JDK_Boundary]`: Java 11 bytecode target (`JavaVersion.VERSION_11`). NEVER introduce syntax requiring higher JVM versions.
- `[Version_Truth]`: ALL dependency versions MUST be read from and written to `gradle/libs.versions.toml`. BANNED: Hardcoding versions in `app/build.gradle.kts`. Compose libraries without explicit versions in the catalog use the Compose BOM for version alignment.
- `[Dynamic_Color]`: In `Theme.kt`, dynamic colors are ONLY enabled when `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`. Preserve this fallback with custom Purple/Pink `ColorScheme`.
- `[i18n_Isolation]`: ALL user-visible strings MUST be localized in `res/values/strings.xml`. BANNED: Hardcoded UI strings in `.kt` bodies. When adding locales, keep translations consistent across all supported language directories.
- `[Language]`: Respond in the same language the user uses (git commits excepted, which MUST use Conventional Commits in English).
- `[Single_Module]`: Only `:app` exists today. If a new submodule is ever added, update `settings.gradle.kts` and this guide.
- `[KSP]`: Room annotation processing uses KSP (`com.google.devtools.ksp`). DO NOT introduce kapt. Room entities and DAOs are the only KSP-processed sources.

## 3. Tool Calling & MCP Protocol
[Tool_First_Execution]: You MUST invoke MCP tools to verify external facts before generating code. Hallucination of APIs or issues is strictly forbidden.

| Domain | MCP Tool | Execution Mandate |
|---|---|---|
| GitHub Issues/PRs | `mcp__github-mcp-server__issue_read` / `pull_request_read` | Fetch actual content before claiming what it says. DO NOT infer from memory. |
| GitHub Code Search | `mcp__github-mcp-server__search_code` | Look up real-world usage examples, API patterns, or reference implementations. |
| GitHub Commits | `mcp__github-mcp-server__list_commits` / `get_commit` | Verify commit history, authorship, or change timelines. |
| GitHub Releases | `mcp__github-mcp-server__get_latest_release` / `list_releases` | Check version numbers, changelogs, or release dates. |
| Library/API Docs | `mcp__context7__resolve-library-id` → `mcp__context7__query-docs` | Verify API signatures, parameters, deprecation status, and usage examples. Always resolve the library ID first, then query. |
| Web Facts | `mcp__searxng__searxng_web_search` → `mcp__searxng__web_url_read` | Verify version numbers, compatibility, release dates, or any claim about the external world. Read result pages when the snippet is insufficient. |
| Web Research & Content | `mcp__tavily__tavily_search` / `tavily_research` → `mcp__tavily__tavily_extract` | Search for current information, news, or facts. `tavily_research` for deep multi-source research. |

[Validation_Reaction]: Pass tool parameters EXACTLY as defined by the schema. If a tool returns an error, fix the EXACT field reported. Do NOT guess or preprocess repairs.

### Parameter Format Rules
- **Omit optional fields** instead of passing `null`.
- **Arrays must be real JSON arrays**, not stringified JSON.
- **Single-element values still need arrays** when the schema expects an array.
- **Do not confuse objects with arrays**.
- **File paths are plain strings**. Do not wrap them in Markdown links.
- **Send parameters as-is**; do not pre-process or guess repairs. Let the tool's validation layer report specific errors.

## 4. Kotlin & Compose Structural Protocols
- `[Null_Safety]`:
  - BANNED: `!!` operator.
  - UI/Compose Path: MUST use `?:` for skeleton/fallback states.
  - Domain/Data Path: MUST use `requireNotNull(value) { "Explicit reason" }` to enforce fail-fast mechanisms.
- `[Coroutine_Constraints]`:
  - BANNED: `GlobalScope`.
  - MANDATORY: Long-lived `launch` blocks that touch persistence or business state MUST be wrapped in `try-catch` or register a `CoroutineExceptionHandler`.
  - OFFLOAD: Explicitly wrap with `withContext(Dispatchers.IO)` ONLY for raw File I/O, Bitmap manipulation, or SDKs lacking internal main-safety. Do NOT wrap Room DAO calls.
  - `[CallScreening_Exception]`: `runBlocking` is permitted inside `BlockingCallScreeningService.onScreenCall()` because the screening decision MUST be synchronous; the bounded 500ms contacts timeout prevents ANR.
- `[Syntax_Enforcement]`:
  - `when` expressions on `sealed class`/`enum` MUST NOT contain `else` branches.
  - Function calls with `Boolean` or `null` literals MUST use named arguments (e.g., `isEnabled = true`, `data = null`).
  - Functions containing only a single expression MUST use the `=` assignment syntax.
- `[Compose_State]`: MUST use `collectAsStateWithLifecycle()` for ALL Flow collections within Compose UI. `collectAsState()` is BANNED.
- `[Compose_Architecture]`:
  - Stateful wrapper (Screen composable calling ViewModel) + stateless presentation components.
  - Child Composables MUST NOT access ViewModels directly. Receive data via params, emit events via lambdas.
  - `Modifier` MUST be the last optional parameter, defaulting to `Modifier`.
- `[Insets]`: `enableEdgeToEdge()` is active. When using `Scaffold`, you MUST consume its `PaddingValues` via `Modifier.padding(innerPadding)`. Use `WindowInsets` or `safeDrawingPadding()` for non-Scaffold screens.
- `[LazyLayout]`: Every `items()` invocation within `LazyColumn`/`LazyRow` MUST include a deterministic `key` parameter. BANNED: Relying on default positional index.
- `[Theme_Tokens]`: ALL visual properties (colors, typography, shapes) MUST resolve via `MaterialTheme` token accessors. BANNED: Raw `Color(0xFF...)` instantiation in components, hardcoded font sizes, or literal shape values.

## 5. Data & Service Performance Constraints
- `[Call_Screening_Latency]`: `onScreenCall()` MUST complete within 5 seconds (system-imposed limit). The contacts lookup timeout of 500ms is the safety valve. DO NOT add new I/O operations inside the `runBlocking` block without an explicit timeout bound.
- `[Room_Queries]`: DAO methods that return `Flow` MUST NOT contain heavy synchronous computation. Offload complex filtering/transformation to the ViewModel or use `flowOn`.
- `[Rule_Matching]`: `match()` returns on FIRST hit — rules are tested in definition order, not priority-sorted. DO NOT change this to a "best match" algorithm without updating the call screening contract in `BlockingCallScreeningService`.
- `[Notification_Channel]`: Channel "call_blocking" is created once in `NotificationHelper.init()`. DO NOT recreate or reconfigure it on every notification post.

## 6. Verification Triggers (Pre-Commit)
[Condition]: Before claiming completion, the corresponding verification MUST pass with exit code 0.
- General → `git diff --check` + `./gradlew :app:test`
- UI/Android/Compose Changes → also `./gradlew :app:lintDebug`
- With device/emulator online → also `./gradlew :app:connectedCheck` (verify with `adb devices` first)
- Room/DataStore/Entity Changes → ensure all unit tests pass, then run instrumented tests if a device is available.

[Banned_Actions]: Do NOT attempt to run `ktlint` or `detekt` unless explicitly verified via `./gradlew tasks --all`. Do NOT mutate process environment variables in tests.

## 7. Auto-Commit Protocol
[Execution]: You MUST strictly adhere to Conventional Commits.
- **Header**: `<type>(<scope>): <semantic_summary>`
  - `[Constraint]`: `<type>` MUST be strictly chosen from: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`. NO variations allowed.
  - `<scope>` is optional but recommended — use the primary module/path affected (e.g., `data`, `ui`, `service`, `db`, `theme`).
- **Body**: Group changes by file/feature. For `fix` commits, explain root cause and impact.
- **Search Engine**: Strictly prefer `rg` / `rg --files` for high-throughput semantic indexing.
- **Ignored Files**: NEVER stage or commit `.DS_Store` or `.worktrees/`. BANNED under any circumstances.

## 8. Agent Conduct & Verification Discipline

[Constraint]: These rules govern how you engage with user requests and code. Any deviation constitutes a critical task failure.

### 8.1 Read Before Acting

Before you answer a question, propose a change, edit an existing file, create a new file, or run a command:

1. Read the relevant code paths completely. Do not rely on memory, summaries, or partial file content.
2. Read commit messages (`git log`, `git show`) that touch the relevant files to understand intent and history.
3. Read related tests, build files, and documentation that define the contract of the code you are touching.
4. Only after you have actually read the relevant files may you form a response or make a change.

[Banned]: Guessing file contents, assuming what a function does, or editing from memory.

### 8.2 Clarify Ambiguous or Conflicting Requests

If any part of the user's request is unclear, ambiguous, contradictory, or leaves room for interpretation:

1. STOP and ask the user for clarification.
2. Do not proceed by guessing, assuming, inferring, or filling in the blanks.
3. Do not choose an interpretation and act on it without confirmation.
4. Phrase your clarification request around the specific uncertainty and the concrete options you see.

[Banned]: Proceeding with "probably," "likely," "I assume," or any other form of speculation about user intent.

### 8.3 Verify Technical Facts with Tools

If you encounter any of the following while working on code:

- Doubt about an API signature, behavior, or deprecation status
- Uncertainty about a library version, compatibility, or release date
- Suspicion that a value, state, or calculation might be wrong
- Assumption or inference about external system behavior
- Any non-trivial calculation that could be verified

You MUST use the appropriate MCP tool (GitHub, Context7, web search, etc.) or project-local command to verify the fact before acting on it.

[Banned]: Proceeding based on "I think," "it should be," "probably," or unverified memory.

### 8.4 No Hallucination

You MUST NOT invent:

- File contents or line numbers you have not read
- API methods, parameters, or behavior
- Error messages, logs, or test results
- Commit messages, issues, or documentation that do not exist
- Facts about external libraries, frameworks, or the physical world

If you do not know something, say so and use a tool to verify.

### 8.5 Simplicity First

- No features beyond the request.
- No single-use abstractions.
- No unrequested flexibility or configurability.
- No error handling for impossible scenarios.
- Prefer existing project patterns over introducing new ones.
- When editing existing code, do NOT improve adjacent code, comments, or formatting. Touch only what the change requires.
- Every changed line should trace directly to the request.

### 8.6 Module & File Size

- Target < 500 lines per Kotlin file (excluding tests). If a file is already over the target but below 800 lines, avoid making it substantially larger. Extract when a file exceeds 800 lines.
- When extracting, migrate related tests and docs together.
