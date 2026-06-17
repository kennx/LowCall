# NoCall Agent Guide

<!-- Last updated: 2026-06-18 -->

Android App (single module `:app`), minSdk 24, Jetpack Compose + Material3. Currently no DI framework, Navigation Compose, Room, DataStore, or network stack is used. This file covers project-specific constraints only; standard Kotlin/Compose conventions are enforced by the compiler and IDE.

## Project Map

- `app/src/main/java/cc/niaoer/nocall/` — Main code. `MainActivity` is the sole Activity.
- `app/src/main/java/cc/niaoer/nocall/ui/theme/` — Theme system with dynamic color support (Android 12+), Material3 `ColorScheme` and `Typography` definitions.
- `app/src/main/res/` — Android resources (strings, themes, drawables, mipmaps, XML configs).
- `app/src/test/` — Local unit tests (JUnit 4).
- `app/src/androidTest/` — Instrumented tests (AndroidJUnit4 + Compose UI Test).
- `app/build.gradle.kts` — App-level build config.
- `gradle/libs.versions.toml` — Version Catalog (sole source of truth for versions).
- `build.gradle.kts` (root) — Global plugins only.
- `gradle.properties` — Gradle JVM args, configuration cache, Kotlin code style settings.

## Environment

- **JDK**: Java 17+ to run AGP 9.2 / Gradle 9.4.1 (current workspace uses Android Studio bundled JBR, JDK 21). `compileOptions` target remains `JavaVersion.VERSION_11` for runtime bytecode compatibility. On macOS without a local JDK:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <tasks>`
- **Android SDK**: compileSdk 36 (minor API level 1), targetSdk 36, minSdk 24.
- **AGP**: 9.2.1. **Kotlin**: 2.2.10. **Gradle**: Wrapper-managed (9.4.1).
- **Kotlin Compose Plugin**: `org.jetbrains.kotlin.plugin.compose` (declared as `kotlin-compose` in the version catalog). With Kotlin 2.x, the Compose compiler is managed by this plugin. `buildFeatures { compose = true }` is still present in `app/build.gradle.kts` for IDE/tooling compatibility but is not the compiler source of truth.

## Hard Constraints

- **minSdk = 24**: Do not use APIs unsupported below API 24; version-gate newer APIs with `Build.VERSION.SDK_INT >= ...`.
- **Java 11**: Do not introduce syntax or dependencies requiring a higher Java version.
- **Single Module**: Only `:app` exists today. If a new submodule is ever added, update `settings.gradle.kts` and this guide.
- **Version Catalog**: All dependency versions must live in `gradle/libs.versions.toml`. Hardcoding versions in `build.gradle.kts` is prohibited. After dependency changes, sync Gradle and verify compilation.
- **Dynamic Color Fallback**: In `Theme.kt`, dynamic colors are only enabled when `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`. Preserve this fallback.
- **i18n**: All user-visible strings must live in `res/values/strings.xml`. When adding locales, keep translations consistent across all supported languages.
- **Language**: Respond in the same language the user uses (git commits excepted, which use Conventional Commits in English).

## Tool Use Guidelines

### MCP Verification

Before writing code that depends on any external source, you MUST query the relevant MCP or connector tool. Skipping an available MCP/connector tool is a violation. Exact callable names can differ by environment; if the relevant tool is not already exposed, use tool discovery first, then query the closest matching GitHub, documentation, search/research, or design tool.

| Domain | MCP Tool | When to Use |
|---|---|---|
| GitHub Issues/PRs | `mcp__github-mcp-server__issue_read` / `pull_request_read` | Fetch actual issue/PR content before claiming what it says. DO NOT infer from memory. |
| GitHub Code Search | `mcp__github-mcp-server__search_code` | Look up real-world usage examples, API patterns, or reference implementations. |
| GitHub Commits | `mcp__github-mcp-server__list_commits` / `get_commit` | Verify commit history, authorship, or change timelines. |
| GitHub Releases | `mcp__github-mcp-server__get_latest_release` / `list_releases` | Check version numbers, changelogs, or release dates. |
| Library/API Docs | `mcp__context7__resolve-library-id` → `mcp__context7__query-docs` | Verify API signatures, parameters, deprecation status, and usage examples. Always resolve the library ID first, then query. |
| Web Facts | `mcp__searchxng__searxng_web_search` → `mcp__searchxng__web_url_read` | Verify version numbers, compatibility, release dates, or any claim about the external world. Read result pages when the snippet is insufficient. |
| Web Research & Content | `mcp__tavily__tavily_search` / `tavily_research` → `mcp__tavily__tavily_extract` | Search for current information, news, or facts. `tavily_research` for deep multi-source research. Use `tavily_extract` to read full page content from result URLs. |

DO NOT guess API signatures, version numbers, issue contents, or external facts. If the domain maps to an MCP tool above, use it.

### Tool-First Rule

Before relying on training data or memory for any factual, external, or project-specific claim, verify with the appropriate tool.

| Scenario | Use |
|---|---|
| External versions, compatibility, or current facts | Search / research tools |
| Framework, library, or API documentation | Documentation query tools |
| Project files, structure, or state | File read, code search, or shell tools |
| Build/test failures or runtime errors | Reproduce with shell, then inspect files |
| Unclear which skill or tool fits | Check available skills/tools first; do not guess |

Do not claim version numbers, API signatures, error meanings, or environment facts without verifying them first.

### Parameter Format

- **Omit optional fields** instead of passing `null`.
  - ✅ `{"required_field": "value"}`
  - ❌ `{"required_field": "value", "optional_field": null}`

- **Arrays must be real JSON arrays**, not stringified JSON.
  - ✅ `{"tags": ["a", "b"]}`
  - ❌ `{"tags": "[\"a\",\"b\"]"}`

- **Single-element values still need arrays** when the schema expects an array.
  - ✅ `{"files": ["notes.md"]}`
  - ❌ `{"files": "notes.md"}`

- **Do not confuse objects with arrays**.
  - ✅ `{"items": ["a"]}`
  - ❌ `{"items": {"a"}}` or `{"items": "a"}`

### Paths and Links

- **File paths are plain strings**. Do not wrap them in Markdown links.
  - ✅ `{"path": "/Users/x/notes.md"}`
  - ❌ `{"path": "/Users/x/[notes.md](http://notes.md)"}`

### Validation and Repair

- **Send parameters as-is**; do not pre-process or guess repairs. Let the tool's validation layer report specific errors.
- **Read error messages carefully** and fix the exact field/path reported.
- **Prefer explicit paired parameters** (e.g. `offset` + `limit`). When only one is provided, the harness may apply defaults (`offset = 0`, `limit = 2000`) and report that transparently.

## Compose & UI Rules

- **Modifier placement**: Put `Modifier` at the end of optional params, defaulting to `Modifier`.
- **Stateless first**: Child Composables must not read ViewModels directly. Receive data via params and emit events via lambdas. Top-level screen Composables may use `by viewModel()`.
- **State hoisting**: Stateful wrapper + stateless presentation component. Expose immutable UI state (`StateFlow<UiState>`) from ViewModels; UI sends Events/Actions back.
- **Flow collection**: Inside Compose, collect ViewModel flows with `collectAsStateWithLifecycle()`.
- **Edge-to-edge**: `enableEdgeToEdge()` is active globally. Use `WindowInsets`, `safeDrawingPadding()`, or consume `Scaffold` `PaddingValues` to avoid system bars.
- **Theme tokens**: Do not hardcode raw colors in Composables. Use `MaterialTheme.colorScheme/typography/shapes`.
- **Strings**: UI strings go in `res/values/strings.xml`. Do not hardcode user-visible text in Composables.
- **Icons**: Use `painterResource()` or Material `Icons`; prefer Material vector icons over custom drawables for standard actions.
- **Lazy list keys**: Always declare a unique `key` for `items` in LazyLayouts.

## Kotlin & Coroutine Rules

- **No `!!`**: Use `?.`, `?:`, or `requireNotNull(value) { "msg" }`.
- **No `GlobalScope`**: Use `ViewModelScope` or lifecycle-bound scopes.
- **Main-safe suspend**: Offload blocking work with `withContext(Dispatchers.IO)` or `Dispatchers.Default`.
- **Coroutine errors**: Wrap long-lived, persistence, or business-state `launch` blocks in `try-catch` or use a `CoroutineExceptionHandler`. Short UI animation/gesture launches may rely on structured cancellation and `finally` cleanup when they do not cross data boundaries.
- **Named arguments**: Use named arguments for ambiguous literals (`foo(isEnabled = false, data = null)`).
- **Exhaustive `when`**: Avoid `else` branches on `sealed class`/`enum` so the compiler catches missing cases.
- **Expression bodies**: Use `=` for single-expression functions.

## Module & File Size

- Target < 500 lines per Kotlin file (excluding tests). If a file is already over the target but below 800 lines, avoid making it substantially larger. Extract when a file exceeds 800 lines.
- When extracting, migrate related tests and docs together.

## Tests

- `test/` — Pure logic, no Android runtime. `androidTest/` — Android/Compose UI tests.
- Prefer adding instrumented tests to existing files unless logic is complex enough to warrant isolation.
- Compose UI tests: use `createComposeRule()`; locate nodes by text, content description, or `testTag`; assert with `assertIsDisplayed()`, `assertExists()`, `assertTextEquals()`.
- Prefer whole-object equality over field-by-field assertions.
- Do not mutate process environment variables in tests.

## Verification Checklist

After changes, run without waiting for user permission:
- General: `git diff --check` + `./gradlew :app:test`
- Android/Compose/UI changes: also `./gradlew :app:lintDebug`
- With device/emulator online: also `./gradlew :app:connectedCheck` (check with `adb devices` first)

Do not run tasks like `ktlint` or `detekt` unless you have verified their existence via `./gradlew tasks --all`.

## Workflow

- **Git commits**: Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`). Group body by file/feature; for bugs, explain root cause.
- **Search**: Prefer `rg` / `rg --files` when available.
- **Ignored files**: `.DS_Store` and `.worktrees/` must remain ignored by `.gitignore`. Do not commit them.
- **Updates**: Keep global rules in root `AGENTS.md`; directory-specific rules in nearest subdirectory `AGENTS.md`.
