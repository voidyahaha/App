# JEE Command Center

Native Android application source for managing a JEE study routine. It uses Kotlin, Jetpack Compose, Room/SQLite, and local JSON/CSV file export. There is no sign-in, cloud service, API key, or internet permission.

## Open and build

1. Install Android Studio and its bundled JDK 17.
2. In Android Studio, choose **Open** and select this project directory (the one containing `settings.gradle.kts`).
3. In **Tools → SDK Manager**, install Android SDK Platform 35 and the Android SDK Build-Tools offered for it. Accept the SDK licenses.
4. Allow Gradle to sync. The first sync downloads Gradle and the pinned Maven dependencies, so it needs an internet connection.
5. Run the app on an Android 8.0+ device (API 26+) or emulator.

From a terminal at this directory:

```bash
# Run local unit tests
./gradlew testDebugUnitTest

# Build a debug APK
./gradlew assembleDebug

# Install to a connected device with USB debugging enabled
./gradlew installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Alternatively:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`. If Android Studio reports a missing SDK, install Platform 35 in SDK Manager or set the local `sdk.dir` in an untracked `local.properties` file. If Gradle reports an incompatible Java version, set the Gradle JDK to 17 under **Settings → Build, Execution, Deployment → Build Tools → Gradle**.

## Implemented modules

- **Dashboard:** date and preparation day, daily and seven-day study totals, target progress, subject totals, recorded-day streak, current syllabus/backlog counts, and recent saved activities. Statistics start empty and are calculated from the local database.
- **Activity log and timer:** manual entries with second-level local timestamps, subject/chapter/type/notes; day navigation; visible unrecorded gaps; delete; and a single persistent timer with start, pause, resume, and stop/save controls. Timer elapsed time is recalculated from saved timestamps after the app is reopened. Paused intervals are separate entries and are not counted as study time.
- **Backlogs:** create, edit, search, filter, duplicate, delete, and cycle status; priority and deadline; estimated/actual minutes; workload progress; overdue indication; and starting a timer linked to an item. A timer does not mark an item complete.
- **Syllabus:** editable starter lists for Physics, Chemistry, and Mathematics in Classes 11 and 12; add/remove a chapter; cycle its status; and view completion by subject and class.
- **Custom tests:** create timed tests; author single-answer, multiple-answer, numerical, and true/false questions; set options, correct answers, tolerance, explanations, and per-question positive, negative, and partial marks; take tests with answer autosave, palette navigation, marking for review, countdown, submit confirmation, auto-submit at expiry, retakes, and result review.
- **Analytics:** actual recorded study time by day, month, and subject; current syllabus/backlog counts; empty states when no study data exists.
- **Settings and data ownership:** nickname, daily target, preparation and target exam dates, dark/light theme, versioned full JSON export/restore with validation and confirmation, activity CSV export, and confirmed local-data deletion.
- **Critical calculation tests:** timer elapsed time, backlog progress, numerical tolerance, partial multi-select marking, and configured test scoring.

## Known scope limits

This is a functional first release, not the entire requested academic suite. It does not yet include a foreground-service notification for a running timer, Android date/time picker controls, overlap warnings or activity edit/merge, week/month timeline browsing, automatic idle detection, chapter topics and checklist-level progress, task planner or reminders, revision scheduling, a standalone question bank or mistake notebook, image/diagram question authoring, PDF/CSV question import, attachments or file-resource library, detailed chapter/time-per-question reports, PDF reports, or cloud sync. The default syllabus is a starter list, not a guarantee of the current official JEE syllabus; verify and edit it in the app. JSON backup does not contain file attachments (this release does not store attachments).

Timer intervals are persisted in the Room database and use device wall-clock timestamps, so their elapsed duration can be reconstructed after process death or device restart. The app does not run a foreground service or show an ongoing notification. Manually logged activities accept `YYYY-MM-DD HH:mm:ss` values in the device's local timezone.

## Technical details

- Android Gradle Plugin 8.7.3; Gradle 8.9 wrapper; Kotlin 2.0.21; JDK 17.
- Compose BOM 2024.12.01; Material 3; Navigation Compose 2.8.5.
- Room 2.6.1 with KSP; kotlinx.serialization JSON 1.7.3.
- Database file: `jee-command-center.db` in app-private storage. Clearing app storage or uninstalling removes it; export a backup first.
- Package/application ID: `com.jeecommandcenter`.
- Minimum SDK: 26; compile/target SDK: 35.
