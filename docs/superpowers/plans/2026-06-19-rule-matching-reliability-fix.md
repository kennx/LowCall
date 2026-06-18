# Rule Matching Reliability Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent regex-like wildcard misclassification, reject invalid regex before persistence, and ensure dialog cancellation never saves, with physical-device and emulator Telecom verification.

**Architecture:** Keep matching and editor-decision rules in pure Kotlin functions so local JUnit tests cover behavior without Android/Room mocks. The ViewModel consumes those decisions, exposes resource-backed validation state, and maps distinct dialog events to cancel, keep, or switch actions. Device verification exercises UI/persistence on A069 and the Telecom incoming-call pipeline on Pixel_7.

**Tech Stack:** Kotlin 2.2, JUnit 4, Jetpack Compose Material3, Android Room, Android Telecom/CallScreeningService, Gradle 9.4.1.

---

### Task 1: Regex classification and validation

**Files:**
- Modify: `app/src/test/java/cc/niaoer/nocall/data/RuleMatcherTest.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/data/RuleMatcher.kt`

- [ ] Add failing tests proving `138.*` and `(138).*` are regex-shaped, `+86*` remains a wildcard, valid regex compiles, and `[0-9` is rejected.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'cc.niaoer.nocall.data.RuleMatcherTest'`; expect the new assertions to fail.
- [ ] Extend `looksLikeRegex()` with high-signal `.*`, `.+`, and grouping detection; add a pure `isValidRegex(pattern)` compiler check.
- [ ] Re-run the focused test; expect all matcher tests to pass.

### Task 2: Editor save decisions

**Files:**
- Create: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditDecision.kt`
- Create: `app/src/test/java/cc/niaoer/nocall/ui/rules/RuleEditDecisionTest.kt`
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditViewModel.kt`

- [ ] Add failing pure JUnit tests for direct invalid REGEX rejection, invalid wildcard-to-regex confirmation, cancellation without persistence, explicit wildcard persistence, and valid regex persistence.
- [ ] Run the focused decision test; expect failures because the decision model does not exist.
- [ ] Implement a small sealed decision result and pure functions that normalize input, request suggestion, validate REGEX, cancel without save, or return a persistable pattern/type.
- [ ] Update `RuleEditUiState` with a nullable pattern error and route `save`, switch, keep, and cancel events through the pure decisions.
- [ ] Re-run focused matcher/editor tests; expect all to pass.

### Task 3: Compose dialog and validation UI

**Files:**
- Modify: `app/src/main/java/cc/niaoer/nocall/ui/rules/RuleEditScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Add the invalid-regex resource string and display the state error through `OutlinedTextField.isError` plus supporting text.
- [ ] Bind `AlertDialog.onDismissRequest` to cancel-only, “仍用通配” to explicit keep-and-save, and “切换为正则” to validated switch-and-save.
- [ ] Compile and run all local tests plus lint.

### Task 4: Automated and physical-device verification

**Files:** No production file changes expected.

- [ ] Run `git diff --check`.
- [ ] Run `./gradlew :app:test :app:lintDebug` with Android Studio JBR.
- [ ] Confirm `00285361G001888` is online and still holds `android.app.role.CALL_SCREENING`.
- [ ] Run `ANDROID_SERIAL=00285361G001888 ./gradlew :app:connectedCheck` and install the debug APK without clearing data.
- [ ] Exercise exact, wildcard, valid regex, invalid regex, suggestion cancel, keep, and switch flows through the device UI; inspect app state and logcat.

### Task 5: Emulator Telecom incoming-call verification

**Files:** No production file changes expected.

- [ ] Start the `Pixel_7` AVD and wait for boot completion.
- [ ] Install the same debug APK, set NoCall as the test/default call-screening app, and create deterministic EXACT, WILDCARD, and REGEX rules.
- [ ] Trigger matching and non-matching incoming calls with the emulator console GSM command.
- [ ] Use Telecom dumpsys, logcat, and NoCall history/Room state to verify blocked versus allowed outcomes and absence of process crashes.
- [ ] Stop test calls and restore temporary Telecom overrides.

### Task 6: Final verification

**Files:** No additional changes expected.

- [ ] Re-run `git diff --check`, `:app:test`, `:app:lintDebug`, and physical-device `:app:connectedCheck` after all edits.
- [ ] Inspect `git diff` for surgical scope and confirm no generated/device artifacts are tracked.
- [ ] Report exact automated, physical-device, and emulator evidence plus any platform limitations.
