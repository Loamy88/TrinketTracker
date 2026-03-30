# Trinket Tracker — VEX IQ 2026 Middle School Worlds

A purpose-built Android app for tracking trinkets at the VEX IQ 2026 Middle School World Championship.

## Features

- **Not Collected tab** — Lists all registered teams; tap any team to open the camera and capture their trinket photo
- **Collected tab** — Shows all teams whose trinkets have been captured, with thumbnails; tap to view full-screen
- **Live progress bar** — Shows `XX% Completed - XXX/XXX Teams` at the top of every screen
- **Refresh from RobotEvents** — Downloads the official team list from `robotevents.com` as an XLS file and syncs locally, preserving already-collected teams
- **Photo storage** — Images saved to `Android/data/com.vexiq.trinkettracker/files/Pictures/TrinketTracker/` on the device; accessible via the folder button in the Collected tab
- **Search** — Filter teams by number or name in either tab

## Building with Codemagic

1. Push this repo to GitHub
2. Sign up / log in at [codemagic.io](https://codemagic.io)
3. Connect your GitHub repo — Codemagic auto-detects `codemagic.yaml`
4. For a **debug APK** (no signing needed), trigger the `android-debug` workflow
5. For a **release APK**, add your keystore to Codemagic (Environment → Android code signing) and set:
   - `CM_KEYSTORE_PATH`
   - `CM_KEYSTORE_PASSWORD`
   - `CM_KEY_ALIAS`
   - `CM_KEY_PASSWORD`
   Then trigger `android-release`

## Local Development

### Requirements
- Android Studio Hedgehog (2023.1) or newer
- JDK 17
- Android SDK 34

### Steps
```bash
git clone https://github.com/YOUR_USERNAME/TrinketTracker.git
cd TrinketTracker
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## App Walkthrough

### First Launch
1. Open the app → the **Not Collected** tab is shown
2. Tap the **red refresh button** to download the team list from RobotEvents
3. All registered teams appear as cards

### Capturing a Trinket
1. Tap a team card in **Not Collected**
2. The camera opens automatically
3. Take the photo — or press Back to cancel (no change is made)
4. The team immediately moves to the **Collected** tab with its thumbnail

### Viewing Photos
- Tap any team in the **Collected** tab to see its photo full-screen
- Tap the **green folder button** to open or locate the photos directory on device

### Photo Location on Device
```
Internal Storage / Android / data / com.vexiq.trinkettracker / files / Pictures / TrinketTracker /
```
Files are named `TRINKET_<TeamNumber>_<Timestamp>.jpg`.

## Tech Stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + Kotlin Flow |
| Database | Room (SQLite) |
| HTTP | OkHttp 4 |
| XLS Parsing | Apache POI 3.17 (HSSF) |
| Image Loading | Coil 2 |
| Build | Gradle 8.4, KSP |
| CI | Codemagic |

## Permissions Used

| Permission | Reason |
|-----------|--------|
| `INTERNET` | Download team list from RobotEvents |
| `CAMERA` | Capture trinket photos |
| `READ/WRITE_EXTERNAL_STORAGE` | Save photos (Android ≤ 9) |
