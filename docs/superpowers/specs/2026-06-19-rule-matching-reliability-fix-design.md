# Rule Matching Reliability Fix Design

<!-- Last updated: 2026-06-19 -->

## Goal

Eliminate the three verified silent-failure and accidental-save paths introduced or left open by commit `226bc7dea593ce3ca814ea8e4fe88def5b0784be`, then validate exact, wildcard, and regex rules through unit tests, UI behavior, a connected physical device, and an Android emulator Telecom call flow.

## Scope

1. Detect common regex-shaped wildcard input such as `138.*` and grouped expressions without treating ordinary wildcard patterns such as `+86*` as regex.
2. Reject syntactically invalid regex patterns before persistence, including the wildcard-to-regex confirmation path and direct REGEX saves.
3. Make Back and outside-tap dismissal close the regex suggestion without saving. Only explicit dialog buttons may persist.
4. Add regression tests for matcher classification, save validation, and dialog event semantics.

Existing rule matching semantics remain unchanged:

- EXACT performs full-string equality.
- WILDCARD uses `*` for zero or more characters and `?` for exactly one character; all regex metacharacters are literals.
- REGEX uses Kotlin `Regex.matches`, so the expression must match the full phone number.

## Design

### Pattern validation

Keep matching logic pure. Extend the regex-shape detector only for high-signal structures missing from the current implementation: regex wildcard operators (`.*`, `.+`) and grouping parentheses. Preserve the existing exception for a leading phone-country `+` in wildcard patterns.

Add a pure regex validation function returning whether the pattern compiles. `RuleEditViewModel.save()` and `confirmSwitchToRegex()` must call it before persistence. Invalid input remains on screen and exposes a resource-backed error message; it must not set `saved` or write to Room.

### Dialog events

Split the current overloaded dismissal action into two explicit events:

- `cancelRegexSuggestion()` only clears dialog visibility.
- `keepWildcardAndSave()` clears dialog visibility and persists the wildcard rule.

`AlertDialog.onDismissRequest` uses the cancel event. The “仍用通配” button uses the save event.

### Testability

Move decision logic needed by the ViewModel into small pure functions/data results so local JUnit tests can verify save decisions without Android or Room mocks. Compose UI tests verify that Back/outside dismissal does not trigger persistence if this can be tested without adding a test-only dependency seam; otherwise the pure event transition is the regression boundary and manual device UI verification covers the dialog.

## Verification

### Automated

- Add failing tests first for `138.*`, grouped regex input, invalid regex rejection, cancel-without-save, and explicit keep-with-save.
- Run `./gradlew :app:test`.
- Run `./gradlew :app:lintDebug` and `git diff --check`.
- Run `./gradlew :app:connectedCheck` on the physical device when online.

### Physical device

Use connected device `00285361G001888` (A069, Android 16/API 36), which currently holds the `android.app.role.CALL_SCREENING` role for `cc.niaoer.nocall`.

- Install the debug APK without clearing app data.
- Verify exact, wildcard, valid regex, invalid regex, suggestion cancel, and explicit keep/switch flows.
- Confirm the package remains the call-screening role holder.
- Inspect application and Telecom logs for crashes or rejected callbacks.

### Emulator Telecom flow

Use the local `Pixel_7` AVD. Install the same debug APK, grant/set the call-screening role, create deterministic enabled rules, then use the emulator console incoming-call command for one matching and one non-matching number.

Verify the real Android Telecom pipeline rather than invoking `RuleMatcher` directly:

- `BlockingCallScreeningService` is bound.
- Matching calls are rejected and logged as blocked.
- Non-matching calls are allowed and logged as allowed.
- No regex compilation exception or process crash appears in logcat.

The emulator call validates Telecom integration; the physical device validates installation, UI, persistence, and OEM behavior. A real carrier call is outside scope.

## Success Criteria

- All three reviewed defects have regression tests and pass.
- EXACT, WILDCARD, and valid REGEX matching remain green.
- Invalid REGEX cannot be persisted through either editor path.
- Back/outside dialog dismissal never saves.
- Physical-device checks pass.
- Emulator matching and non-matching incoming calls produce the expected screening and history outcomes.
