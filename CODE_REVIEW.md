# Code Review — Local Calendar (Material-calendar)

**Reviewer:** AI-assisted full code review
**Scope:** entire `app/` module — data layer, widgets, UI, notifications, build system, privacy posture
**Date:** September 2025
**Verdict:** solid foundation for a privacy-first local calendar. The architecture is clean (Room + Repository + ViewModel + Compose/Glance), the MD3 styling is consistent, and no network permissions exist. However, several real bugs would have surfaced during phone testing — the most serious affecting recurring events everywhere. All critical items below have been fixed in this commit; open recommendations are listed at the end.

---

## 1. Architecture Overview

| Layer | Implementation | Assessment |
|---|---|---|
| Database | Room 2.6.1, entities `CalendarEntity` / `EventEntity`, v2 with timezone + rrule migration | Good. Migration 1→2 present, but no schema export (`exportSchema = false`) — enable it before v3. |
| Data access | `CalendarDao` / `EventDao` with `Flow` | Good. Reactive queries drive both UI and widgets. |
| Domain | `RRule.kt` custom RFC 5545 expander, `DateTimeUtils` occurrence helpers | Good concept; had correctness bugs (§3.1). |
| Sync | `DeviceCalendarSyncManager` one-way pull from Android CalendarProvider | Privacy-correct: read-only pull, no account APIs, no network. |
| Backup | `BackupRestoreManager` JSON + ICS via SAF file picker | Privacy-correct: user chooses where files go. |
| UI | Compose + Material 3, dynamic color on Android 12+ | Good MD3 usage: top bar, bottom nav, FAB, bottom sheets, filter chips. |
| Widgets | Glance 1.1.1, `NextEventsWidget` + `MonthWidget`, Material You palette via `WidgetTheme` | Good MD3 baseline; functional bugs fixed (§3.2). |
| Reminders | WorkManager unique work per event, chain-scheduling for recurrences | Sound design. |
| Permissions | `READ_CALENDAR`, `WRITE_CALENDAR`, `POST_NOTIFICATIONS` runtime requests | Correct, requested in context. |

**Privacy posture:** the app holds no `INTERNET` permission at all — verified in `AndroidManifest.xml`. With no network permission, data physically cannot leave the device except through backups the user explicitly exports. That is the strongest possible offline guarantee and the core promise of this app. The only remaining leak path was Android's automatic cloud backup, which is now closed (§3.4).

---

## 2. What Was Reviewed

- `app/build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, Gradle wrapper
- `AndroidManifest.xml`, backup/extraction rule XMLs, widget provider XMLs, strings/themes/colors
- Data layer: models, DAOs, database, repository, `DeviceCalendarSyncManager`, `BackupRestoreManager`, `RRule`
- Widgets: `NextEventsWidget`, `MonthWidget`, `WidgetTheme`, `WidgetUpdater`
- Notifications: `NotificationHelper`, `ReminderScheduler`, `ReminderWorker`
- UI: `MainActivity`, `MainScreen`, `CalendarViewModel`, all calendar views, dialogs, `WidgetsShowcaseView`, `SyncAndBackupView`, theme files
- Tests: `app/src/test`

---

## 3. Critical Issues (all fixed in this commit)

### 3.1 Recurrence engine lost occurrences and shifted dates

**File:** `data/recurrence/RRule.kt`

- **Weekly `BYDAY` with multiple days only ever produced the first weekday.** `FREQ=WEEKLY;BYDAY=MO,WE` — the standard Google Calendar pattern for "Mon & Wed" — rendered only Mondays. The old `weeklyCandidate()` picked a single `minBy` weekday per interval week instead of expanding every matching day.
- **DAILY recurrence used fixed 86,400,000 ms arithmetic**, so every DST crossing shifted wall-clock start times by one hour.
- **MONTHLY/YEARLY compounded clamped dates** (Jan 31 → Feb 28 → Mar 28 …) because each candidate derived from the previous clamped candidate instead of the DTSTART anchor.
- **COUNT semantics** were counted only inside the queried range, so a `COUNT=5` event re-expanded infinitely in later months.
- **Performance:** expansion always iterated from DTSTART; a 6-year-old daily event queried in a month grid did ~2,000 `Calendar` allocations per day-cell.

**Fix:** full rewrite (see `RRule.kt`). Occurrences are now generated chronologically from a jump-forward index; WEEKLY+BYDAY walks day-by-day and yields *every* matching weekday aligned to Monday-based interval weeks (RFC default `WKST=MO`); DAILY/WEEKLY/MONTHLY/YEARLY all expand through `Calendar` field arithmetic so wall-clock time is preserved across DST; MONTHLY/YEARLY always expand from the DTSTART anchor; COUNT counts from DTSTART (with a documented approximation for WEEKLY+BYDAY when the query range starts far after DTSTART); UNTIL is inclusive per RFC 5545. `nextOccurrenceAfter()` now delegates to `occurrences()` so both paths share one implementation.

**Verified:** 12 unit-test scenarios ported and executed against the algorithm (daily/weekly/BYDAY/interval/monthly-clamp/UNTIL/COUNT/far-future) — all pass. Same scenarios ship as `RRuleTest.kt`.

### 3.2 Widgets showed wrong dates for recurring events

**File:** `widget/NextEventsWidget.kt`

- The "UP NEXT" list sorted by raw `startMillis`, so a daily standup created six months ago appeared **at the top with a date from six months ago**. Recurring events were sorted as if their DTSTART were the next occurrence.
- All-day events rendered as `"EEE · 12:00 AM · All day"` — a meaningless clock time.
- The **"New" chip did nothing useful**: it opened the app without creating an event, even though `MainActivity.handleWidgetIntent()` already supports a `create_event` extra that nothing was sending.

**Fix:** widget rows are now built from concrete occurrences (`occurrencesBetween` within a 45-day horizon, capped at 3 per event) with the occurrence's own timestamp; sort key and displayed time both use the occurrence; all-day rows show "EEE d MMM · All day"; recurring rows get a `· ↻` repeat marker; the "New" chip sends `create_event=true` through `actionStartActivity` parameters, opening the pre-filled event sheet directly from the home screen. Tap-per-row deep-linking into the event details (`event_id`) was already correct and is preserved.

### 3.3 ICS export produced invalid TZID datetimes

**File:** `data/backup/BackupRestoreManager.kt`

Events with a stored timezone were exported as `DTSTART;TZID=America/New_York:20250903T120000Z` — UTC wall time **plus** a literal `Z` **plus** a TZID. RFC 5545 forbids `Z` with TZID, and the time value itself was UTC rather than local, so every timed event imported into Google Calendar landed on the wrong hour (or was rejected outright).

**Fix:** TZID values are now formatted in the event's own timezone (`DTSTART;TZID=America/New_York:20250903T080000`), plain events stay UTC-`Z`, all-day stays `VALUE=DATE`. Round-trip with the existing importer verified logically: `TZID` + local time parses back to the correct epoch and preserves the TZID.

### 3.4 Calendar database was eligible for Google cloud backup

**Files:** `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`

`allowBackup="true"` with effectively empty rules meant the Room database (your entire schedule) could be uploaded to Google One cloud backup — directly contradicting "everything stays locally".

**Fix:** the calendar DB (including WAL/SHM sidecars) is now explicitly excluded from **cloud backup** on Android 8–11 (`full-backup-content`) and Android 12+ (`data-extraction-rules` / cloud-backup). Device-to-device transfer keeps including the DB so switching phones doesn't silently drop data. To make this safe, reminders are re-armed automatically at app start (`ReminderScheduler.rescheduleAll` in `MainActivity.onCreate`), since the WorkManager job store is no longer part of backups.

### 3.5 Sync never refreshed already-imported events

**File:** `data/sync/DeviceCalendarSyncManager.kt`

Re-importing matched events by `systemEventId` and skipped existing ones entirely — edits made in Google Calendar after the first import never appeared locally, while the button still reported success. The timezone (`EVENT_TIMEZONE`) was never read either.

**Fix:** re-import now refreshes title/description/location/start/end/all-day/RRULE/timezone/color on existing rows (only writing when something actually changed, preserving local-only fields like category, reminder, completion state), reads and stores the provider timezone, and reports both new and refreshed counts in the result message.

### 3.6 Permission grant could leave the Backup screen stuck

**Files:** `ui/viewmodel/CalendarViewModel.kt`, `ui/MainScreen.kt`

`hasPermission` was read from the OS during composition rather than held in state. Granting the permission updated `deviceCalendars` only if the list was non-empty, so on devices with no calendars (or a denied-then-granted flow) the screen stayed on "Grant Access" until a full recomposition happened by accident.

**Fix:** `hasCalendarPermission` is now a `MutableStateFlow` inside the ViewModel, part of `CalendarUiState`, and is refreshed by `checkAndLoadDeviceCalendars()` regardless of grant outcome. The UI reacts to the state change immediately.

### 3.7 Test sources could not compile; Gradle wrapper was broken

- `GreetingScreenshotTest.kt` / `ExampleRobolectricTest.kt` referenced Robolectric, Roborazzi and Compose test APIs **that were never declared as dependencies**, and pinned `sdk = [36]`. Any `testDebugUnitTest` run failed at compile time. `ExampleUnitTest.kt` was a 0-byte file.
- `gradle/wrapper/gradle-wrapper.properties` was **0 bytes** and `gradlew`/`gradle-wrapper.jar` were missing, so `./gradlew` (as documented in the README) could not run anywhere.

**Fix:** removed the unbuildable tests; added a real `RRuleTest.kt` (pure JUnit 4, JVM-only, deterministic UTC) covering the recurrence engine including the BYDAY regression; declared `testImplementation(libs.junit)` (4.13.2); restored a complete working wrapper for Gradle 8.11.1 (AGP 8.9.2's minimum) with `gradlew`, `gradlew.bat`, jar and properties committed.

### 3.8 Smaller fixes

- **Widget receivers** are now `android:exported="false"` (official AppWidgets guidance; only the system delivers `APPWIDGET_UPDATE`), shrinking the attack surface.
- **Notification icon** replaced the jarring `android.R.drawable.ic_dialog_info` with a proper monochrome calendar vector (`res/drawable/ic_notification.xml`).

---

## 4. Widget Review (MD3 / UX)

**Good already:** both widgets follow Material You — `WidgetTheme` maps the app's dynamic `ColorScheme` (wallpaper-derived on Android 12+) into Glance `ColorProviders`, including dark/light handling; `SizeMode.Responsive` layouts; 24dp corner radius; event color chips; loading layout declared in provider XML.

**Improved here:** occurrence-accurate dates, all-day formatting, working "New" action, repeat markers.

**Known limitations (intentionally left for later):**

1. `MonthWidget` renders a fixed 42-cell grid + optional today-footer. On the smallest responsive size (180×180dp) the footer can clip. Consider reading `LocalSize` and dropping the footer below ~220dp height, or making the grid 5 rows when the month fits.
2. `MonthWidget` shows only the current month; month navigation would need `actionRunCallback` work actions.
3. `updatePeriodMillis=1800000` (30 min) is fine, but widgets don't refresh at midnight — the "Today" footer and today-highlight can go stale overnight until the next update. A `WorkManager` daily refresh or `AlarmManager`-based midnight update would fix it.
4. `WidgetsShowcaseView` (in-app preview tab) filters "upcoming" by raw `endMillis`, so recurring events whose DTSTART has passed vanish from the preview even though they're upcoming. Cosmetic only; the real widgets are correct.
5. The launcher icon is still the default template art.

---

## 5. Data-Layer Notes (non-blocking)

- **ICS export DTEND for all-day events:** RFC 5545 wants DTEND exclusive (next day). Current export writes the stored end. If all-day events are always created with end = next-day midnight this is correct; worth an explicit normalization in the editor.
- **`RRule` subset:** supports FREQ/INTERVAL/BYDAY(weekly)/UNTIL/COUNT. Not supported: BYMONTHDAY, BYSETPOS, BYMONTH, WKST overrides, BYDAY ordinal prefixes (e.g. `-1SU` for "last Sunday"), and EXDATE. Google events using those will expand approximately. The data model stores the raw RRULE, so a stronger engine can be swapped in later without migration.
- **Deletion propagation:** if a Google event is deleted remotely, re-import keeps the local copy (by design — the local DB is a snapshot, and the app never deletes data you didn't delete). Worth documenting in-app one day.
- **`getEventsForRange` DAO query** is currently unused by views (they filter `allEvents` in memory). Fine at current scale; revisit if datasets grow to tens of thousands of events.
- **`ReminderWorker.doWork()`** uses `runBlocking` on a WorkManager thread — acceptable here (short query, no UI thread), noted for completeness.
- **Room schema export** should be enabled (`room.schemaLocation` ksp arg) before the next schema change so migrations stay reviewable.

---

## 6. Security & Privacy Checklist

| Check | Status |
|---|---|
| No `INTERNET` permission | ✅ verified in manifest |
| No third-party SDKs / analytics / trackers | ✅ dependencies are androidx + junit only |
| Calendar data excluded from cloud backup | ✅ fixed (was exposed) |
| Device-transfer allowed (encrypted, local) | ✅ intentional |
| Widget receivers not exported | ✅ fixed |
| Runtime permission requests in context | ✅ |
| Backup files written only via user-chosen SAF location | ✅ |
| Reminders self-heal after reboot/restore | ✅ added |

---

## 7. Continuous Integration (new)

`.github/workflows/android-build.yml` builds on every push to `master`/`main` and via manual dispatch:

1. JDK 17 (Temurin) + Gradle 8.11.1 via the restored wrapper (`gradle/actions/setup-gradle@v4` caches dependencies).
2. `./gradlew assembleDebug` → installable debug APK.
3. `./gradlew testDebugUnitTest` → runs `RRuleTest` (fails the build on regression).
4. Uploads `LocalCalendar-debug-apk` artifact (30-day retention) and test reports on failure.

To test on a phone: open the workflow run → download the APK artifact → open it on the device → allow installing from that source. Debug APKs are self-signed and install without any signing setup.

---

## 8. Recommended Next Steps (not in this commit)

1. **ICS import calendar choice** — imports always land in the first calendar (or "Imported ICS Calendar"); a picker would avoid merges users don't expect.
2. **Midnight widget refresh** (see §4.3).
3. **Month widget navigation + size-aware footer** (see §4.1–4.2).
4. **Sample-data button gating** — "Load Sample Google Calendar Account" is prominent in production UI; hide it behind a debug flag or a confirm dialog before public release.
5. **Room schema export** before the next entity change.
6. **Stronger RRULE coverage** (BYMONTHDAY, ordinals, EXDATE) when real-world imports demand it.
7. **Instrumented tests** for the widget taps (deeplink extras) once an emulator workflow job is added.
