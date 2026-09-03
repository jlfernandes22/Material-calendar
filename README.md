# Local Calendar

A privacy-focused, offline-first calendar for Android built with Jetpack Compose and Material 3.

## Features

- **Material 3 (Material You)** design with dynamic color on Android 12+
- **Local Room database** — your events and calendars stay on your device
- **On-device Google Calendar import** via the Android CalendarProvider (read-only, one-way)
- **JSON + iCal backup/restore** using the system file picker
- **Five home-screen widgets**, all rendered with Glance and themed with Material You colors:
  - **Upcoming** — your next events with Today/Tomorrow labels, tappable rows that open the event
  - **Month** — compact month grid; tap any day to jump to its schedule, tap Today to dive in
  - **7-Day Horizon** — a strip of the next seven days with per-day event dots
  - **Quick Actions** — one-tap event templates (meeting, focus, workout, study) plus New/Today/Agenda shortcuts
  - **Time Allocation** — how your scheduled time splits across categories this week
- **Reminder notifications** for upcoming events

No accounts, no cloud sync, no data leaves your phone.

## Privacy by design

- **Least privilege**: only the `READ_CALENDAR` permission is requested — the app never writes
  to the system calendar provider.
- **No silent cloud copies**: Android's automatic backup and device-transfer are disabled
  (`allowBackup=false` plus explicit database exclusions), so the only way calendar data leaves
  the device is when *you* export a JSON or iCal file.
- **Local everything**: events, calendars, categories and settings live in an on-device Room
  database. No analytics, no tracking, no network calls.

## Build

**Prerequisites:** JDK 17+, Android SDK.

```shell
./gradlew assembleDebug
```

or build via Android Studio.

Run the app on a connected device or emulator:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test on your phone with GitHub Actions

Every push to `master`/`main` (and every manual run from the **Actions** tab →
**Build Android APK** → **Run workflow**) builds an installable debug APK:

1. Open the latest run at `https://github.com/jlfernandes22/Material-calendar/actions`
2. Download the **LocalCalendar-debug-apk** artifact
3. Unzip and open `app-debug.apk` on your phone, allow installing from that source
4. Done — the app is self-signed, no signing setup needed

Unit tests (`RRuleTest`) run automatically as part of the same workflow.

See [CODE_REVIEW.md](CODE_REVIEW.md) for the full architecture and code review.
