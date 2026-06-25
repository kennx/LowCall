---
name: LowCall Design System
colors:
  primary-google-blue: "#0B57CF"
  semantic-emerald: "#10B981"
  semantic-amber: "#F59E0B"
  semantic-rose: "#E11D48"
  background-light: "#FFFFFF"
  surface-light: "#F8F9FA"
  surface-light-high: "#F1F3F4"
  text-primary-light: "#1F1F1F"
  text-secondary-light: "#5E6368"
  background-dark: "#131314"
  surface-dark: "#1E1F22"
  surface-dark-high: "#282A2D"
  text-primary-dark: "#E3E3E3"
  text-secondary-dark: "#C4C7C5"
shapes:
  extra-small: "8dp"
  small: "12dp"
  medium: "16dp"
  large: "24dp"
  extra-large: "32dp"
  pill: "50%"
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

1. **Android 16 Material You Alignment**: All visual properties (colors, typography, shapes) MUST resolve via `MaterialTheme` token accessors. Hardcoding raw colors (e.g., `Color(0xFF...)`) in components is strictly banned.
2. **Dynamic Color First**: The app fully embraces Material You dynamic colors, adapting to the user's wallpaper and system preferences for a native Android 16 experience.
3. **High-Contrast Fallback**: When dynamic color is unavailable, the app relies on a crisp "Google Blue" palette paired with pure white and true black backgrounds.
4. **Zero-Shadow Flat Depth**: UI hierarchy is defined exclusively by tonal differences (`surfaceContainer` levels) rather than shadow elevations.

## Color Palette

The app utilizes a modern Android 16 Google Play aesthetic.

- **Dynamic Colors**: Used by default via `dynamicLightColorScheme` and `dynamicDarkColorScheme`.
- **Primary Fallback**: Google Blue (`#0B57CF`) used for primary actions, buttons, and active states.
- **Semantic Fallback**: Emerald for success (`#10B981`), Rose for destructive actions like blocking (`#E11D48`), Amber for warnings (`#F59E0B`).
- **Surfaces Fallback**: Clean Tonal surfaces using Pure White/Grays (Light) and True Black/Dark Grays (Dark).

### Implementation Rule
When building Compose UI, always use `MaterialTheme.colorScheme.*`. Do NOT map directly to raw hex values in the view layer.
**M3 Baseline Override**: The application theme MUST explicitly define all container colors (e.g., `surfaceContainer`, `secondaryContainer`) and set `surfaceTint = Color.Transparent`. This prevents legacy Material 3 default baselines (purple/pink) from bleeding into the UI.

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

- **Display/Headline**: Used for major screen titles. Thick, bold presence.
- **Title**: Used for app bars and section headers.
- **Body**: Used for primary text content (e.g., rule descriptions, call logs).
- **Label**: Used for buttons, small metadata, and timestamp text.

### Implementation Rule
Always use `MaterialTheme.typography.*`. Do not hardcode font sizes using `.sp` inside `Text` composables. Refer to `app/src/main/java/cc/niaoer/lowcall/ui/theme/Type.kt` for exact weights.

## Shapes & Elevation

### Shapes
Android 16 style emphasizes heavily rounded corners and fully pill-shaped actionable components.
- **Pill Shape (Circle)**: All primary buttons, FABs, and bottom navigation indicators.
- **Large (24.dp)** / **Extra Large (32.dp)**: Cards, Dialogs, Bottom Sheets. Avoid sharp corners.

### Zero-Shadow Elevation
Modern Material You relies entirely on **Tonal Elevation**.
- **Cards & Surfaces**: Use default `CardDefaults.cardColors()` and rely on `surfaceContainer` colors (like `surfaceContainerLowest` to `surfaceContainerHighest`) to pull depth from contrast.
- **Borders/Shadows**: Explicit `BorderStroke` and `elevation = CardDefaults.elevatedCardElevation()` MUST BE AVOIDED. Shadows are deprecated for layout hierarchy.

### Implementation Rule
Always use `MaterialTheme.shapes.*` for rounding. Do not use `CardDefaults.elevatedCardElevation()`.

## Motion & Animation

Modern Compose UI must feel alive and responsive.
- **Visibility Changes**: Use `AnimatedVisibility` when adding, deleting, or revealing items (like adding a new block rule).
- **State Transitions**: Use `animateColorAsState` or `animateFloatAsState` for active/inactive icon states or selections.
- **Transitions**: Do not use abrupt snaps for UI states if a simple crossfade or size animation can be applied.

## Accessibility (a11y) & Touch Targets

- **Touch Targets**: ALL clickable elements (Icons, Buttons) MUST have a minimum touch target size of **48.dp**.
- **Content Descriptions**: All decorative icons should set `contentDescription = null`. Any functional icon MUST have a meaningful string resource.

## Edge-to-Edge & Insets

The application runs in edge-to-edge mode. 
- When using `Scaffold`, you **MUST** consume its `PaddingValues` via `Modifier.padding(innerPadding)`.
- Use `WindowInsets` or `safeDrawingPadding()` for non-Scaffold screens to avoid drawing behind the system navigation and status bars.

## Component Guidelines

### Card
- **Padding**: Internal content should universally use `16.dp` padding.
- **Elevation**: Shadow elevation is BANNED. Use default `Card` to leverage tonal `surfaceContainerHighest` or `surfaceContainerLow` colors.
- **Spacing**: Vertical spacing between stacked cards should be `8.dp`.

### List / LazyColumn (ListItem)
- **Edge Spacing**: Lists should generally have a horizontal content padding of `16.dp` or stretch edge-to-edge with internal item padding of `16.dp`.
- **Item Typography**: Primary text uses `titleMedium`, secondary/subtext uses `bodyMedium` or `bodySmall` with `color = MaterialTheme.colorScheme.onSurfaceVariant`.

### Switch
- **Colors**: Rely on default Material 3 Switch colors (which map active state to `primary`).

### TopAppBar
- **Colors**: Always explicitly set `colors = TopAppBarDefaults.topAppBarColors(...)`. By default, the background should map to `surface` or `surfaceContainer` for a seamless edge-to-edge transition.

### FloatingActionButton (FAB)
- **Placement**: Align to bottom-end using `Scaffold`'s `floatingActionButton` slot.
- **Color**: Mapped to `primaryContainer` and `onPrimaryContainer` by default. Should be pill-shaped.

### TextField / OutlinedTextField
- **Width**: Typically use `Modifier.fillMaxWidth()` for form inputs.
- **Rounding**: Rely on the default M3 OutlinedTextField rounding.
