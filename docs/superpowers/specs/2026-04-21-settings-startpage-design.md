# Settings Fragment, Font Scaling, Session Caching & Start Page Simplification

**Date:** 2026-04-21
**Status:** Draft

## Overview

Add a Settings fragment with a font size slider (live preview, global scaling) and a logout button. Cache the user's session so they skip the Start page on relaunch. Simplify the Start page for senior users.

## 1. Settings Fragment

### New Files
- `app/src/main/java/com/example/myapplication/ui/settings/SettingsFragment.java`
- `app/src/main/res/layout/fragment_settings.xml`

### Layout (top to bottom)
1. **"Font Size" label** with current percentage display (e.g., "120%")
2. **Material Slider** — range 80% to 150%, default 120%, step size 5%, continuous
3. **Live preview card** — sample heading, body text, and button that update in real-time as the slider is dragged
4. Spacer
5. **"Log Out" button** — full-width, outlined style, warning/red color. Clears saved session and navigates to `nav_start`

### Navigation
- Add `nav_settings` destination to `mobile_navigation.xml`
- Wire the existing "Settings" overflow menu item in `main.xml` to navigate to this fragment via `onOptionsItemSelected` in `MainActivity`

### Storage
- SharedPreferences file: `"circle_events_prefs"`
- Key: `"font_scale"` (float)
- Default: `1.2f` (120% — bigger-side default for seniors)

## 2. Font Scale Application

### New File
- `app/src/main/java/com/example/myapplication/FontScaleHelper.java`

### Implementation
- Static method: `applyFontScale(View rootView, Context context)`
- Reads `font_scale` from SharedPreferences
- Recursively walks the view tree; for each `TextView`:
  - On first visit, stores the original text size (px) as a tag on the view
  - Multiplies the original size by the scale factor and applies it
- This avoids compounding when re-applied

### Integration Points
Fragments call `FontScaleHelper.applyFontScale(getView(), requireContext())` in `onViewCreated`:
- `HomeFragment`
- `SlideshowFragment`
- `StartFragment`
- `GalleryFragment`
- `SettingsFragment` — preview card only (slider and logout stay at fixed size)
- Nav drawer header

### Live Preview Behavior
- As the slider moves (`addOnChangeListener`), update the preview card's TextViews in real-time (without persisting)
- Persist the value to SharedPreferences only on slider release (`addOnSliderTouchListener.onStopTrackingTouch`)

## 3. Session Caching & Auto-Login

### Storage
Same SharedPreferences file (`circle_events_prefs`), additional keys:
- `"saved_circle_code"` (String) — the circle code
- `"saved_mode"` (String) — mode name: "CREATE", "JOIN", or "ADMIN"

### Save Point
In `StartFragment.confirmAction()`, after validation passes and before navigating to Home:
- Write `saved_circle_code` and `saved_mode` to SharedPreferences

### Auto-Login Flow
In `StartFragment.onCreateView()`, before UI setup:
1. Check if `saved_circle_code` exists in SharedPreferences
2. If present, read code and mode
3. Set on `EventViewModel`: `setCurrentCircleCode(code)`, `setSelectedMode(mode)`
4. Navigate to `nav_home` immediately, skipping the Start page

### Logout
Settings fragment logout button:
1. Clears `saved_circle_code` and `saved_mode` from SharedPreferences
2. Navigates to `nav_start` with `popUpTo(nav_start, inclusive = true)` to clear the entire back stack

## 4. Start Page Simplification

### Changes to `start_page.xml`
- **Logo:** 120dp → 80dp
- **Subtitle:** Remove entirely (delete the subtitle TextView)
- **Padding:** `paddingTop` 40dp → 24dp
- **Button height:** 56dp → 64dp (update `@dimen/button_height`)
- **Button text size:** 18sp → 20sp (update `@dimen/text_button`)
- **Button spacing:** 16dp → 20dp between buttons

### Changes to `strings.xml`
- Remove `welcome_subtitle` string

### Changes to `dimens.xml`
- `button_height`: 56dp → 64dp
- `text_button`: 18sp → 20sp

**Note:** `button_height` and `text_button` are used globally in button styles (`Widget.CircleEvents.Button` and `Widget.CircleEvents.Button.Outlined`), so all buttons across the app will get the larger size. This is intentional for the senior audience.

## Files Modified

| File | Change |
|------|--------|
| `mobile_navigation.xml` | Add `nav_settings` fragment destination |
| `MainActivity.java` | Add `onOptionsItemSelected` to handle Settings menu tap → navigate to `nav_settings` |
| `res/menu/main.xml` | No change (existing `action_settings` item is reused) |
| `StartFragment.java` | Add session save in `confirmAction()`, add auto-login check in `onCreateView()` |
| `HomeFragment.java` | Add `FontScaleHelper.applyFontScale()` call in `onViewCreated` |
| `SlideshowFragment.java` | Add `FontScaleHelper.applyFontScale()` call in `onViewCreated` |
| `start_page.xml` | Shrink logo, remove subtitle, tighten spacing, increase button sizes |
| `dimens.xml` | Update `button_height` to 64dp, `text_button` to 20sp |
| `strings.xml` | Remove `welcome_subtitle` |

## New Files

| File | Purpose |
|------|---------|
| `ui/settings/SettingsFragment.java` | Settings screen with font slider and logout |
| `res/layout/fragment_settings.xml` | Settings layout |
| `FontScaleHelper.java` | Static utility for recursive font scaling |
