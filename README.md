# NotiPrint

[Русская версия](README.RU.md)

NotiPrint is a local Android app that stores important phone events and prints them on a Bluetooth Classic SPP thermal printer using ESC/POS raster commands.

It is designed for a simple, large-text workflow: a family member does not need to open the app to receive a paper copy of an important event.

## Features

- Prints incoming SMS, missed calls, and calendar notifications.
- Saves every accepted event in a local Room queue before printing, then retries when the printer or Bluetooth connection becomes available.
- Supports a configurable night interval: events are buffered overnight and printed in the morning.
- Renders all content as a 384-dot monochrome bitmap, so Cyrillic text does not depend on the printer code page.
- Works with paired 58 mm Bluetooth SPP / ESC-POS printers, including 384-dot models such as the MUNBYN IMP006.
- Runs as a foreground service and requests a notification-listener rebind after app startup or device reboot.
- Can ignore SMS from unknown numeric senders while still allowing alphabetic Sender IDs such as `МЧС` or `MCHS`.
- Includes a local blacklist for SMS and missed calls. Add all numbers from a contact or enter a number/Sender ID manually.
- Provides a diagnostic test page for checking print quality and paper coverage.

## Requirements

- Android Studio with the Android SDK installed.
- A Gradle JDK compatible with the project, preferably JDK 17 or JDK 21.
- An Android phone running Android 8.0 (API 26) or later.
- A paired Bluetooth Classic SPP printer that supports ESC/POS raster printing (`GS v 0`).

## Build

Open the project root in Android Studio and select a compatible Gradle JDK in its Gradle settings if needed.

From the project root, build a debug APK with one of these commands:

```powershell
.\gradlew.bat assembleDebug
```

```bash
./gradlew assembleDebug
```

After a successful build, the APK is available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build the optimized, non-debuggable APK with R8 and resource shrinking enabled:

```powershell
.\gradlew.bat assembleRelease
```

```bash
./gradlew assembleRelease
```

The resulting APK is available at:

```text
app/build/outputs/apk/release/app-release.apk
```

The local release APK uses the standard debug certificate so it can be installed
over previous debug builds. Configure a separate private release signing key
before distributing the app through a store.

Use `--offline` only when the required Gradle dependencies have already been cached locally.

## Install and first run

For development, connect a phone with USB debugging enabled, select it in Android Studio, and press **Run**.

For a regular installation, copy `app-release.apk` to the phone, open it with the file manager, and allow installation from that source when Android asks. Use the debug APK for development and diagnostics. USB debugging is not required for manual installation.

On first launch:

1. Grant Bluetooth/Nearby devices access and choose an already paired printer.
2. Run **Test print** to confirm the connection and paper quality.
3. Grant access to notifications, SMS, contacts, and call history as requested.
4. In the phone settings, allow NotiPrint to start automatically and run in the background. The names of these settings differ between manufacturers.

## Printer notes

The app sends complete raster jobs over one SPP connection and flushes only after each receipt. This avoids the visible pauses often caused by line-by-line Bluetooth printing.

The MUNBYN IMP006 specification recommends 58 mm thermal paper with a thickness of 0.06–0.08 mm. Paper thermal sensitivity and the printer battery level can noticeably affect coverage, especially in dense black areas.
