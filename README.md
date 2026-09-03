# Local Calendar

A privacy-focused, offline-first calendar for Android built with Jetpack Compose and Material 3.

## Features

- **Material 3 (Material You)** design with dynamic color on Android 12+
- **Local Room database** — your events and calendars stay on your device
- **On-device Google Calendar import** via the Android CalendarProvider
- **JSON + iCal backup/restore** using the system file picker
- **Home-screen widgets** for glanceable schedule at-a-glance, 7-day horizon, quick actions, and time-allocation breakdown
- **Reminder notifications** for upcoming events

No accounts, no cloud sync, no data leaves your phone.

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
