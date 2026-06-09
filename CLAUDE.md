# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Architecture Overview

## Product Context
A senior-friendly shared calendar app intended for deployment in senior living / care centers. Residents (and the staff/admins who manage their circle) view and receive reminders for upcoming events. This purpose drives several design choices — large default font scaling, a minimal "circle code" join flow instead of accounts, and the locked-down JOIN mode for residents. Favor accessibility (large tap targets, high contrast, simple flows, generous text sizing) over density when making UI decisions.

## Project Type
Android application built with Java using Android Gradle Plugin (AGP) 8.13.2, single-module (`app/`). `minSdk` 24, `targetSdk`/`compileSdk` 36, Java 11. View Binding is enabled; the codebase uses generated `*Binding` classes rather than `findViewById`.

## Key Components

### Main Activity Flow
- `MainActivity.java` — Single activity hosting all fragments via Navigation Component + a `DrawerLayout`. Responsibilities: requests permissions (POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM), creates the `event_reminders` notification channel, schedules alarms via `scheduleEventNotification()`, and locks/hides the drawer in JOIN mode (see Mode System). The toolbar overflow menu (`R.menu.main`, `action_settings`) navigates to the Settings screen.
- Navigation uses Android Navigation Component (graph: `res/navigation/mobile_navigation.xml`). Destinations:
  - `StartFragment` (`nav_start`) — Default destination; CREATE/JOIN/ADMIN mode selection, circle code validation, and auto-login (see Session Persistence).
  - `HomeFragment` (`nav_home`) — Displays merged Firestore + Google Calendar events as dynamic cards; pull-to-refresh + refresh-on-resume for Google Calendar (see Live Calendar Sync).
  - `SlideshowFragment` (`nav_slideshow`) — Event creation form and Google Calendar ID linking.
  - `SettingsFragment` (`nav_settings`) — Font-size slider and logout.
  - `GalleryFragment` (`nav_gallery`) — Placeholder (unused).

### Data Layer
- **Firebase Firestore**: Primary backend
  - `default` collection: Maps circleCode → `{adminCode}` (used for circle creation/validation and admin auth)
  - `events` collection: Event documents filtered by `circleCode`
  - `circles` collection: Maps circleCode → `{calendarId}` for Google Calendar integration
- **Google Calendar API**: Fetched via Retrofit (`CalendarService`), merged with Firestore events in `EventViewModel`. API key is hardcoded in `EventViewModel.java` (`fetchGoogleCalendarEvents`, ~line 95).

### Event System
- `Event.java` — Data model with UUID auto-generation; Firestore-serializable. **Note:** there is no separate `source` field — Google Calendar events reuse the `circleCode` field set to the literal `"google_calendar"` to tag their origin (used for alarm-skipping and de-duplication on refresh).
- `EventViewModel.java` — Activity-scoped shared ViewModel (obtained via `requireActivity()` in every fragment); core business logic for events, mode state, the Firestore real-time listener, and Google Calendar fetching. Observed by all fragments.
- `AlarmReceiver.java` — BroadcastReceiver triggered by AlarmManager; shows the notification and deletes the event from Firestore.

### Mode System
- `Mode` enum (CREATE/JOIN/ADMIN/START) in `ui/start/Mode.java`
- JOIN mode locks the navigation drawer and hides the nav menu (enforced in `MainActivity`'s `addOnDestinationChangedListener`); other modes allow full navigation
- Switching modes calls `eventViewModel.setSelectedMode(mode)` and `setCurrentCircleCode(code)`; `setCurrentCircleCode` re-registers the Firestore listener for the new circle

### Session Persistence (auto-login)
- `StartFragment` saves `saved_circle_code` and `saved_mode` to `SharedPreferences` (`circle_events_prefs`) on successful entry, then auto-navigates to Home on next launch without re-prompting.
- `SettingsFragment`'s logout button clears those two keys and pops back to `nav_start`.

### Font Scaling
- `FontScaleHelper` (top-level class) stores a global `font_scale` float (default `1.2`) in `SharedPreferences` (`circle_events_prefs`) and recursively rescales every `TextView` in a view tree, caching each view's original size in a tag so scaling is idempotent.
- Every fragment calls `FontScaleHelper.applyFontScale(view, requireContext())` in `onViewCreated`. `SettingsFragment` drives the scale via a Material `Slider` (live preview, persists on touch release).

## Package Structure

```
app/src/main/java/com/example/myapplication/
├── MainActivity.java
├── AlarmReceiver.java
├── FontScaleHelper.java           # App-wide TextView scaling via SharedPreferences
├── CalendarService.java           # Retrofit interface for Google Calendar API v3
├── CalendarEvent.java, CalendarResponse.java, EventDateTime.java
└── ui/
    ├── start/    StartFragment, StartViewModel, Mode (enum)
    ├── home/     HomeFragment, HomeViewModel, EventViewModel, Event
    ├── slideshow/ SlideshowFragment, SlideshowViewModel
    ├── settings/ SettingsFragment
    └── gallery/  GalleryFragment, GalleryViewModel  # unused placeholder
```

## Build System

```bash
# Build debug APK
./gradlew assembleDebug

# Install debug APK to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run a single unit test
./gradlew test --tests com.example.myapplication.ExampleUnitTest

# Clean build
./gradlew clean assembleDebug
```

On Windows use `gradlew.bat` (e.g. `.\gradlew.bat assembleDebug`).

Dependencies are managed via `gradle/libs.versions.toml` (version catalog). Add new dependencies there first, then reference with `libs.<name>` in `app/build.gradle.kts`. (Retrofit and the Gson converter are the exception — they are declared as inline string coordinates in `build.gradle.kts`.)

## Important Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/example/myapplication/ui/home/EventViewModel.java` | Core business logic — events, modes, Firestore listener, Google Calendar fetch/merge |
| `app/src/main/java/com/example/myapplication/MainActivity.java` | Permissions, notification channel, alarm scheduling, drawer control, settings menu |
| `app/src/main/java/com/example/myapplication/CalendarService.java` | Retrofit interface for Google Calendar API |
| `app/src/main/java/com/example/myapplication/FontScaleHelper.java` | Global font-scale preference + recursive TextView rescaling |
| `app/src/main/res/navigation/mobile_navigation.xml` | Fragment graph and navigation actions |
| `gradle/libs.versions.toml` | Version catalog — add dependencies here |
| `app/google-services.json` | Firebase config (do not commit to public repos) |

## Key Behaviors to Know

- **Event auto-deletion**: `EventViewModel`'s Firestore listener deletes any `events` document whose date/time is in the past as it loads them.
- **Dual event source & idempotent merge**: `EventViewModel` merges Firestore events with Google Calendar events. Because `fetchGoogleCalendarEvents` is called repeatedly (on every Firestore change, on relink, on resume, and on pull-to-refresh), it first strips existing `circleCode == "google_calendar"` events from the list before re-adding fresh ones, so repeated fetches don't duplicate Google events.
- **Live Calendar Sync**: New Google Calendar events appear without relinking. `HomeFragment.onResume()` and the `SwipeRefreshLayout` pull-to-refresh both call `EventViewModel.refreshGoogleCalendarEvents()`, which looks up the circle's `calendarId` and re-fetches. The `isRefreshing` LiveData drives the spinner (cleared on success, failure, and the no-calendar-linked case). This is poll-on-view, not server push — a new event won't appear while idle on the screen until the next refresh. True push would require OAuth2 + a webhook server (Google Calendar `watch` API).
- **Notification flow**: SlideshowFragment creates Event → saves to Firestore → calls `MainActivity.scheduleEventNotification()` → AlarmManager fires AlarmReceiver at event time → AlarmReceiver shows notification and deletes the Firestore document. Google Calendar events (`circleCode == "google_calendar"`) are skipped for alarm scheduling.
- **Date format**: Events store dates as `MM/dd/yyyy HH:mm`; Google Calendar RFC3339 dates are parsed to this format in `EventViewModel`. "All Day" Google events use the time string `"All Day"`.
- **Shared preferences**: A single file, `circle_events_prefs`, holds `font_scale`, `saved_circle_code`, and `saved_mode`.
