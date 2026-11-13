# LowCall Material 3 Expressive UI Redesign

Date: 2026-11-13
Branch: `feat/ui-expressive`
Base: `dev` at `60f7876`

## Outcome

Redesign the complete LowCall UI around Material 3 Expressive while preserving the existing call-screening behavior, routes, rules, logs, whitelist, and settings. Replace visually inconsistent icon and list usage, make phone numbers readable, provide offline landline attribution, and let users create an exact blocking rule from an allowed call.

Success means all existing user data survives unchanged, every screen follows `DESIGN.md`, the new interactions work on the connected physical device, and before/after screenshots cover every route.

## Safety And Existing State

- The pre-change device backup is stored in the original checkout at `backups/lowcall-20261113-103009/`.
- The backup contains the Room database, WAL, SHM, and available app files. SQLite `PRAGMA integrity_check` returned `ok`.
- Verified backup counts: 9 block rules, 19 call logs, 0 whitelist entries.
- Work occurs only in `.worktrees/ui-expressive`; the `dev` checkout remains the functional reference.
- Debug installation must preserve application data. No clear-data, uninstall, or destructive database operation is part of verification.
- Existing screenshots are in the original checkout at `build/ui-audit-before/` and cover Home, Rules, History, Whitelist, Settings, Add Rule, Edit Rule, and Rule Test.

## Design Direction

Use a full visual redesign rather than incremental card styling. Material 3 Expressive is applied through purposeful color, shape, size, motion, and containment. Dynamic color remains enabled on Android 12+, with an accessible static fallback on older versions.

References:

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://design.google/library/expressive-material-design-google-research
- https://iconmcp.com/

`DESIGN.md` is the binding design-system contract. All visual values resolve through MaterialTheme or the shared spacing contract; raw component colors and hardcoded user-visible strings are prohibited.

## Navigation And App Structure

Preserve the eight existing routes and the intentional omission of `restoreState` in bottom navigation. The five destinations remain Home, Rules, History, Whitelist, and Settings, with shorter navigation-only labels where needed.

Use IconMCP solid SVG icons imported as local Android vector drawables. Keep icon semantics consistent: filled icons represent navigation and actions, decorative icons have no content description, and icon-only controls have localized accessible names.

The root edge-to-edge Scaffold continues to own the bottom bar. Each screen consumes its own Scaffold padding. No navigation or call-screening architecture is replaced.

## Screen Designs

### Home

Rebuild Home as a protection dashboard:

1. Protection status and total blocked count form the primary visual region.
2. Today and this week are secondary statistics in a contained group.
3. Test and Add Rule remain the primary quick actions.
4. Recent blocked calls use the same phone-number information hierarchy as History.
5. Existing reactive statistics and midnight refresh behavior remain unchanged.

### Rules

Replace separate floating rule cards with a continuous grouped list. A rule with a description uses the description as its headline and the pattern as supporting text; otherwise the pattern is the headline. Rule type is supporting metadata, not a clickable chip. The trailing Switch remains the enable/disable control and row click opens Edit.

Keep search, type filtering, and the Add Rule FAB. Use localized search UI and remove static clickable-looking chips.

### History

Group logs by date within continuous tonal surfaces. Each row contains:

- A solid status icon.
- Formatted phone number as the headline.
- Location, carrier when available, and either the matched rule or allow reason as supporting text.
- Time as trailing metadata.
- A contextual action: blocked logs retain Add to Whitelist; allowed logs gain Add to Blacklist.

Remove redundant AssistChip status labels and the unsupported fixed “ring 0 seconds” copy. Preserve all existing allow reasons and legacy fallback.

### Whitelist

Use the same grouped list vocabulary. Show formatted numbers, optional notes, and delete controls with accessible names. Retain search, contacts permission explanation, empty state, and Add dialog. Empty state includes a clear icon and a direct add action.

### Settings

Replace independent cards with labeled setting groups and standard list rows. Preserve notification preference, rule import/export, clear-history confirmation, and app identity. Destructive actions use error semantics without making the whole screen alarmist.

### Rule Add/Edit And Rule Test

Use Material 3 segmented controls for rule type, standard text fields, and one high-emphasis Save/Test action. Preserve regex validation and wildcard suggestion behavior. Delete remains available only while editing an existing rule. Test results retain clear blocked/allowed semantics.

## Phone Display Formatting

Add a pure display formatter. It must not alter stored values, matching behavior, import/export, or test input semantics.

Required output:

- Domestic Chinese mobile: `13800138000` -> `138 0013 8000`.
- Chinese mobile with country code: `+8613800138000` -> `+86 138 0013 8000`.
- Chinese landline: `02169619151` -> `021 6961 9151`; three-digit area codes use the same area-code/local-number split.
- Extension: recognize `x`, `;`, `,`, and `转` separators; preserve the extension digits and present them with localized “extension” text.
- Other international numbers: `+86` receives Chinese-specific formatting. Other valid ITU E.164 country codes are preserved as the prefix and the remaining digits are grouped conservatively in 3–4 digit groups; no country-specific national formatting is claimed.
- Blank, malformed, service, and unknown formats remain readable without inventing country or region information.

Apply formatting consistently to Home, History, Whitelist, notifications, and other user-facing phone displays. Rule patterns remain raw because they are configuration, not call-display values.

## Offline Landline Attribution

Extend the existing `PhoneAttribution` implementation instead of adding a network service or second data source. Existing `phone.dat` records contain province, city, postcode, and area code. Build an in-memory area-code index from the already loaded data, cache it once, and use it for landline lookups.

- Mobile lookup retains the current seven-digit binary search.
- Landline lookup strips formatting, recognizes Chinese area codes, and returns province/city without inventing a carrier.
- Existing logs with null location are enriched for display through the same lookup; their database rows are not rewritten.
- New calls store landline location through the existing `location`/`carrier` fields; no Room schema change is required.
- Index construction must be measured on the physical device and must not add unbounded work to call screening.

## Add Allowed Call To Blacklist

The allowed-call action opens a confirmation dialog explaining that future matching calls will be blocked. Confirmation creates an enabled `EXACT` rule using the original logged phone string so it follows existing exact-match semantics.

The operation is idempotent:

- If the same exact rule exists and is enabled, do not insert a duplicate.
- If it exists but is disabled, re-enable it.
- Otherwise insert one rule.
- Reflect the result with a Snackbar and reactive rule state.
- Cancellation leaves all data unchanged.

No blacklist entity or new navigation destination is introduced; `BlockRule` remains the source of truth.

## State, Errors, And Accessibility

- ViewModels expose StateFlow and use viewModelScope; persistence failures are surfaced rather than silently swallowed.
- Compose uses collectAsStateWithLifecycle and stateless presentation components.
- Interactive targets are at least 48dp; status is never conveyed by color alone.
- Dynamic and fallback color roles maintain readable foreground/background combinations.
- All new user-visible strings and accessibility labels are resources.
- Lists have deterministic keys, bottom content clearance, and correct system-bar insets.
- Motion is limited to meaningful state/visibility changes and respects framework defaults.

## Testing And Verification

Automated:

- Unit tests for domestic/mobile/international/extension formatting and malformed inputs.
- Unit tests for landline attribution, missing area codes, and existing mobile lookup regressions.
- DAO/ViewModel tests for blacklist insertion, duplicate prevention, and re-enabling.
- Compose tests for allowed-call action, confirmation, cancellation, formatted numbers, and existing allow reasons.
- `git diff --check`, `./gradlew :app:test`, and `./gradlew :app:lintDebug` must pass.

Physical device:

- Preserve and re-check the existing database counts after installation.
- Exercise all eight routes, rule add/edit/test, search/filter, whitelist add/delete, allowed-call blacklist confirmation/cancellation, and import/export entry points.
- Capture after screenshots for every route in dark mode and key screens in light mode.
- Verify formatted mobile and landline numbers, old-record attribution fallback, duplicate blacklist prevention, and Snackbar feedback.
- Run `./gradlew :app:connectedCheck` with the connected device.
- Compare behavior against `dev` where a regression is suspected; do not mutate the reference checkout.

## Explicit Non-Goals

- No online phone lookup, accounts, analytics, or new permissions.
- No Room schema migration for this feature set.
- No change to rule priority, wildcard conversion, call response behavior, or contacts timeout.
- No adaptive navigation rail/tablet redesign beyond preventing broken layouts.
- No unrelated refactoring or new feature screens.
