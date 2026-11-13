---
name: LowCall Design System
version: 2
basis: Material 3 Expressive
icon_style: IconMCP solid
colors:
  source: dynamic-color-first
  fallback-primary: "#0B57CF"
  semantic-success: emerald
  semantic-warning: amber
  semantic-destructive: error
shapes:
  extra-small: "4dp"
  small: "8dp"
  medium: "12dp"
  large: "16dp"
  extra-large: "24dp"
  full: "50%"
spacing:
  extra-small: "4dp"
  small: "8dp"
  medium: "16dp"
  large: "24dp"
  extra-large: "32dp"
  screen-margin: "16dp"
  list-content-bottom: "88dp"
touch-target-minimum: "48dp"
typography:
  display: "36sp - 57sp"
  headline: "24sp - 32sp"
  title: "14sp - 22sp"
  body: "12sp - 16sp"
  label: "11sp - 14sp"
---

# LowCall Design System

This document is the binding design-system contract for LowCall. Jetpack Compose UI MUST follow this contract and the Material 3 Expressive guidance. Implementation details live in `ui/theme`; screens consume theme tokens instead of defining their own visual language.

## 1. Principles

1. **Purposeful hierarchy**: Color, shape, size, motion, and containment direct attention to protection status and primary actions.
2. **Content before decoration**: Phone numbers, rule purpose, and call outcomes are easier to scan than raw metadata or decorative containers.
3. **Dynamic color first**: Android 12+ uses Material You dynamic color. Older versions use the accessible static fallback defined in `Color.kt` and `Theme.kt`.
4. **Grouped, not card-heavy**: Related rows share a continuous tonal surface. Cards are reserved for independent summary or result regions.
5. **One primary action per region**: Filled buttons and FABs are reserved for the main forward action.
6. **Accessible by default**: Status never depends on color alone, touch targets are at least 48dp, and text scales with system preferences.

## 2. Design Tokens

### Color

- ALL colors MUST resolve through `MaterialTheme.colorScheme`.
- New palette constants belong only in `ui/theme/Color.kt`.
- Use `primary`/`onPrimary` for primary actions and active states.
- Use `surfaceContainer*` roles for grouping and hierarchy.
- Use `error`/`onError` and `errorContainer`/`onErrorContainer` for destructive or blocked states.
- Use `onSurfaceVariant` for secondary text and metadata.
- Do not use raw hex colors, arbitrary alpha overlays, gradients, or shadow elevation in screen components.
- Preserve the Android 12 dynamic-color gate in `Theme.kt`.

### Typography

- Use `MaterialTheme.typography`; do not set `.sp` values in screen components.
- Screen titles use `headlineSmall` or the appropriate app-bar title style.
- List headlines use `titleMedium` or `bodyLarge` according to emphasis.
- Supporting text uses `bodyMedium`; timestamps and compact metadata use `labelMedium`.
- Home's total blocked count may use `displayLarge`; secondary statistics must not compete with it.
- Phone numbers use a readable body/title style and MUST NOT be truncated merely to preserve decorative trailing content.

### Shape

- Use `MaterialTheme.shapes` for containers and controls.
- Small controls use small/medium shapes; grouped surfaces use large/extra-large shapes.
- FABs and navigation indicators use the component's Material 3 defaults.
- Do not create one-off rounded rectangles in screens.

### Spacing

Use the 4/8/16/24/32dp scale:

- 4dp: tightly coupled icon/text or metadata gaps.
- 8dp: related rows and compact internal spacing.
- 16dp: screen margins and standard container padding.
- 24dp: major sections.
- 32dp: exceptional separation only.

Lists that share a screen with a FAB MUST reserve at least 88dp bottom content clearance.

## 3. Icons

- Use solid icons from IconMCP (https://iconmcp.com/), imported as local vector drawables.
- Do not mix outline, filled, and solid icon families within the same control group.
- Use recognizable meanings: Home, Rules/Shield, History, Whitelist/Verified, Settings, Add, Search, Delete, Import, Export, Notification, Block, and Allow.
- Decorative icons use `contentDescription = null`.
- Functional icon-only controls require a localized content description and a minimum 48dp target.
- Do not hand-draw replacement icon paths when an appropriate IconMCP asset exists.

## 4. Navigation And App Bars

- Preserve the five bottom destinations: Home, Rules, History, Whitelist, Settings.
- Navigation labels may be shorter than screen titles; use dedicated string resources.
- Bottom navigation appears only on the five top-level routes.
- Preserve the intentional omission of `restoreState` in tab navigation.
- Child routes use a back button and a clear title.
- App bars use `surface` or `surfaceContainer` with appropriate `onSurface` content colors.
- All edge-to-edge layouts consume Scaffold padding and system insets.

## 5. Lists And Containment

### Standard list row

Use Material 3 `ListItem` or an equivalent row with the same hierarchy:

1. Optional leading solid status/identity icon.
2. Headline containing the user's primary identifier.
3. Supporting text containing location, type, rule, or reason.
4. Trailing timestamp, Switch, or contextual action.

Rows within a logical group share a tonal Surface. Use dividers or spacing sparingly. Do not wrap every row in an independent elevated card.

### Rules

- Description is the headline when present; otherwise use the pattern.
- Raw pattern and rule type are supporting metadata.
- Enable/disable uses a trailing Switch.
- Static rule type MUST NOT use a clickable AssistChip.

### Call history

- Group by date.
- Headline is the formatted number.
- Supporting text shows attribution and call outcome.
- Time is trailing metadata.
- Blocked rows offer Add to Whitelist; allowed rows offer Add to Blacklist.
- Do not repeat the same outcome in an icon, chip, and supporting text.
- Do not display unsupported call-duration claims.

### Settings and whitelist

- Use section labels and grouped list rows.
- Delete and other destructive controls use error semantics and accessible labels.
- Empty states explain the absence and expose the relevant next action.

## 6. Phone Number Presentation

Formatting is display-only. Stored numbers and rule matching remain unchanged.

- Chinese mobile: `138 0013 8000`.
- Chinese mobile with country code: `+86 138 0013 8000`.
- Landline: split area code and local number, for example `021 6961 9151`.
- Extensions use localized extension copy.
- Unknown international formats preserve `+` and use conservative grouping; do not invent attribution.
- Apply the formatter consistently in Home, History, Whitelist, notifications, and other user-facing number displays.
- Rule patterns remain unformatted because they are configuration.

## 7. Actions And Feedback

- Save and Test are filled primary buttons.
- Add Rule and Add Whitelist Entry use the existing FAB pattern.
- Add to Blacklist from an allowed call requires confirmation because it changes future call handling.
- Successful blacklist creation or reactivation produces a Snackbar; duplicates are not inserted.
- Import/export and permission actions use secondary row or button emphasis.
- Clear History and Delete require existing confirmation behavior and error color semantics.
- Snackbars and dialogs use localized strings and do not expose raw internal errors.

## 8. Motion

- Use Material component defaults for presses, selection, and state transitions.
- Use AnimatedVisibility or equivalent only for meaningful result/empty/content changes.
- Do not add perpetual, decorative, or navigation-obscuring animation.

## 9. Accessibility And Verification

- Minimum interactive target: 48dp.
- Support dark/light themes, dynamic/static colors, font scaling, and screen readers.
- Every icon-only action has a localized accessible name.
- Every LazyLayout item has a deterministic key.
- UI changes require unit/instrumented tests appropriate to the behavior, `:app:test`, `:app:lintDebug`, and physical-device verification when a device is connected.
- Capture every route after a visual redesign and compare against the pre-change screenshots.
