# UI Polish Design Spec: Tech Cool-Tone (Clean Tonal)

## 1. Overview
The goal of this project is to polish the entire design of the LowCall application to achieve a "Modern practical, tech cool-tone style". The design strictly adopts a "Clean Tonal" approach (C-1), leveraging pure color blocks and tonal elevation instead of heavy borders or drop shadows.

The design will respect the system's default Light/Dark mode setting equally, without a forced dark mode bias.

## 2. Core Color Palette
The traditional "Secure Blue" is being replaced with a cooler, more high-tech palette.

### Primary Accents
- **Primary / Active:** Ice Blue (`#38BDF8`) & Indigo (`#4F46E5`)
- **Semantic Success:** Teal/Emerald (`#10B981`)
- **Semantic Warning:** Amber (`#F59E0B`)
- **Semantic Error:** Rose/Red (`#E11D48`)

### Light Mode (System Default)
- **Background:** Cold White (`#F8FAFC`)
- **Surface (Cards):** Cold Gray (`#F1F5F9`)
- **Text Primary:** Deep Slate (`#0F172A`)
- **Text Secondary:** Slate (`#475569`)

### Dark Mode (System Default)
- **Background:** Deep Slate Navy (`#0F172A`)
- **Surface (Cards):** Slate Navy (`#1E293B`)
- **Text Primary:** Cool White (`#F8FAFC`)
- **Text Secondary:** Slate Gray (`#94A3B8`)

## 3. Component Guidelines (Clean Tonal)

### Cards & Surfaces
- **No Borders / No Outlines:** Cards will rely purely on the contrast between the Background and Surface colors.
- **Elevation:** Drop shadows will be minimized or removed entirely in favor of Tonal Elevation (relying on color differentiation).
- **Corner Radii (Shapes):** Maintained at standard M3 scales (e.g., Cards 16.dp, Dialogs 24.dp).

### Typography
- Retain the M3 typography scale.
- Colors mapped tightly to the new Primary/Secondary text tokens to ensure crisp legibility on both Light and Dark backgrounds.

### Call Screening Context
- **Blocked Calls:** Represented with the Rose/Red semantic color, applied to icons and text subtly.
- **Allowed Calls:** Represented with the primary Ice Blue or Emerald color.

## 4. Implementation Strategy
1. **Update `DESIGN.md`:** The project's root `DESIGN.md` will be updated to reflect the new Tech Cool-Tone tokens and rules.
2. **Update `Color.kt` & `Theme.kt`:** Translate the new palette into standard Material 3 `lightColorScheme` and `darkColorScheme`.
3. **Refactor Components:** Sweep through `*Screen.kt` files to remove any legacy hardcoded elevation/shadows and ensure they map to the new pure Tonal design.
