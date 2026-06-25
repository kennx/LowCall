---
name: LowCall Design System
colors:
  primary-ice-blue: "#38BDF8"
  primary-indigo: "#4F46E5"
  semantic-emerald: "#10B981"
  semantic-amber: "#F59E0B"
  semantic-rose: "#E11D48"
  background-light: "#F8FAFC"
  surface-light: "#F1F5F9"
  text-primary-light: "#0F172A"
  text-secondary-light: "#475569"
  background-dark: "#0F172A"
  surface-dark: "#1E293B"
  text-primary-dark: "#F8FAFC"
  text-secondary-dark: "#94A3B8"
shapes:
  extra-small: "4dp"
  small: "8dp"
  medium: "16dp"
  large: "24dp"
  extra-large: "32dp"
spacing:
  extra-small: "4dp"
  small: "8dp"
  medium: "16dp"
  large: "24dp"
  extra-large: "32dp"
  screen-margin: "16dp"
typography:
  display: "36sp - 57sp"
  headline: "24sp - 32sp"
  title: "14sp - 22sp"
  body: "12sp - 16sp"
  label: "11sp - 14sp"
---

# LowCall Design System

This document defines the design language and visual identity for **LowCall**. It serves as a single source of truth for both human developers and AI coding agents.

## Core Principles

1. **Material Design 3 (M3) Alignment**: All visual properties (colors, typography, shapes) MUST resolve via `MaterialTheme` token accessors. Hardcoding raw colors (e.g., `Color(0xFF...)`) in components is strictly banned.
2. **Dynamic Color Support**: Dynamic colors are DISABLED by default to enforce the "Tech Cool-Tone" brand identity on all devices, regardless of the OS version.
3. **Accessibility First**: Proper contrast ratios and touch target sizes must be maintained.

## Color Palette

The app utilizes a "Tech Cool-Tone" primary palette to convey trust, clarity, and modern geek-chic aesthetics, essential for a system utility.

- **Primary**: Indigo (`#4F46E5`) and Ice Blue (`#38BDF8`) used for primary actions, buttons, and active states.
- **Semantic**: Emerald for success (`#10B981`), Rose for destructive actions like blocking (`#E11D48`), Amber for warnings (`#F59E0B`).
- **Surfaces**: Clean Tonal surfaces using Cold Grays (Light) and Deep Slate Navy (Dark) to ensure strong text legibility and spatial depth without borders.

### Implementation Rule
When building Compose UI, always use `MaterialTheme.colorScheme.*`. Do NOT map directly to raw hex values in the view layer.

## Spacing & Layout

Consistent spacing is critical for a clean UI.

- **4.dp (Extra Small)**: Inter-item spacing within tight groups (e.g., icon and text).
- **8.dp (Small)**: Default padding inside small components or between closely related items.
- **16.dp (Medium / Screen Margin)**: The standard screen edge margin and padding between distinct sections.
- **24.dp (Large)**: Spacing between major independent layout blocks.

### Implementation Rule
Use defined spacing values via `Modifier.padding()`. Avoid arbitrary numbers like `10.dp` or `13.dp`.

## Typography

LowCall uses the default Android system font family. Font sizes strictly follow the Material 3 Typography scale:

- **Display/Headline**: Used for major screen titles.
- **Title**: Used for app bars and section headers.
- **Body**: Used for primary text content (e.g., rule descriptions, call logs).
- **Label**: Used for buttons, small metadata, and timestamp text.

### Implementation Rule
Always use `MaterialTheme.typography.*`. Do not hardcode font sizes using `.sp` inside `Text` composables. Refer to `app/src/main/java/cc/niaoer/lowcall/ui/theme/Type.kt` for exact weights.

## Shapes & Elevation

### Shapes
Shapes define the corner rounding for components like cards, buttons, and dialogs.
- **Extra Small (4.dp)** / **Small (8.dp)**: Chips, text fields, small buttons.
- **Medium (16.dp)**: Standard cards (e.g., a rule item or call log card).
- **Large (24.dp)** / **Extra Large (32.dp)**: Dialogs, Bottom Sheets.

### Elevation
Material 3 relies on **Tonal Elevation** rather than heavy drop shadows or explicit borders.
- **Cards & Surfaces**: Use default `CardDefaults.cardColors()` to let the surface/surfaceVariant container color differentiate itself from the background.
- **Borders/Shadows**: Explicit `BorderStroke` and `elevation = CardDefaults.elevatedCardElevation()` MUST BE AVOIDED. Use Clean Tonal design (pure flat colors pulling depth from contrast).

### Implementation Rule
Always use `MaterialTheme.shapes.*` for rounding. Do not hardcode `RoundedCornerShape(X.dp)` in individual screens.

## Motion & Animation

Modern Compose UI must feel alive and responsive.
- **Visibility Changes**: Use `AnimatedVisibility` when adding, deleting, or revealing items (like adding a new block rule).
- **State Transitions**: Use `animateColorAsState` or `animateFloatAsState` for active/inactive icon states or selections.
- **Transitions**: Do not use abrupt snaps for UI states if a simple crossfade or size animation can be applied.

## Accessibility (a11y) & Touch Targets

Since LowCall is a utility app, accessibility is not optional.
- **Touch Targets**: ALL clickable elements (Icons, Buttons) MUST have a minimum touch target size of **48.dp**. Compose's `IconButton` handles this automatically, but custom clickable rows must use `Modifier.defaultMinSize(minHeight = 48.dp)` or `Modifier.padding` to expand the click area.
- **Content Descriptions**: All decorative icons should set `contentDescription = null`. Any functional icon (e.g., "Delete Rule", "Add Rule", "Back") MUST have a meaningful string resource for screen readers.

## Edge-to-Edge & Insets

The application runs in edge-to-edge mode. 
- When using `Scaffold`, you **MUST** consume its `PaddingValues` via `Modifier.padding(innerPadding)`.
- Use `WindowInsets` or `safeDrawingPadding()` for non-Scaffold screens to avoid drawing behind the system navigation and status bars.

## Component Guidelines

To maintain pixel-perfect consistency across LowCall screens, adhere to these specific component rules:

### Card
- **Padding**: Internal content should universally use `16.dp` padding.
- **Elevation**: Use `CardDefaults.elevatedCardElevation()` for clickable or primary cards. Use default `Card` for secondary, flatter lists.
- **Spacing**: Vertical spacing between stacked cards should be `8.dp`.

### List / LazyColumn (ListItem)
- **Edge Spacing**: Lists should generally have a horizontal content padding of `16.dp` or stretch edge-to-edge with internal item padding of `16.dp`.
- **Item Typography**: Primary text uses `titleMedium`, secondary/subtext uses `bodyMedium` or `bodySmall` with `color = MaterialTheme.colorScheme.onSurfaceVariant`.

### Switch
- **Colors**: Rely on default Material 3 Switch colors (which map active state to `primary`). Do not manually override to `SuccessGreen` unless explicitly required by a specific destructive/warning context.

### TopAppBar
- **Colors**: Always explicitly set `colors = TopAppBarDefaults.topAppBarColors(...)`. By default, the background should map to the screen's `surface` or `background` color to ensure a seamless edge-to-edge transition. Avoid hardcoding primary color backgrounds for modern M3 apps.

### FloatingActionButton (FAB)
- **Placement**: Align to bottom-end using `Scaffold`'s `floatingActionButton` slot.
- **Color**: Use `primaryContainer` for the background and `onPrimaryContainer` for the icon by default.

### TextField / OutlinedTextField
- **Width**: Typically use `Modifier.fillMaxWidth()` for form inputs.
- **Rounding**: Rely on the default M3 OutlinedTextField rounding. Do not override shape manually.
