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
gradlew assembleDebug
```

or build via Android Studio.

Run the app on a connected device or emulator:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
