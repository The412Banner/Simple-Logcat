# Simple Logcat

A professional root logcat reader for Android. Real-time log streaming with color-coded log levels, package filtering with saved presets, live file output for terminal tools, and export to any folder.

## Requirements

- Android 8.0+ (API 26)
- Root access (su must be available)

## Features

- **Real-time streaming** via `su -c logcat` — starts automatically on launch
- **Color-coded log levels**: V=gray, D=blue, I=green, W=orange, E=red, F/A=purple
- **Level filter chips** — toggle any log level on/off
- **Text search** — filter by tag or message content
- **Package name filter** — resolves PIDs to package names via `/proc/<pid>/cmdline` (no extra root popups)
- **Saved package filter list** — bookmark any package name filter; saved entries appear as chips below the field for one-tap apply or removal; persists across restarts
- **Live file mode** — tap the record button (●) to stream filtered logs to a file in real time; a foreground service keeps capture running while the app is minimised. Read from Termux or any terminal:
  ```
  tail -f /sdcard/Android/data/com.banner.logs/files/simple-logcat-live.log
  ```
- **Export** — saves all buffered logs to `logcat-<timestamp>.txt`; destination set via Settings → Export Location (default: `/sdcard/`)
- **Custom export location** — Settings → Export Location → Choose opens the system folder picker; selection persists across restarts; tap × to reset
- **Settings**: font size, max buffer lines (500 / 1k / 2k / 5k / 10k / 20k / ∞, default ∞), auto-scroll, timestamp/PID display, line wrap

## Signing

All releases are signed with a committed testkey (v1 + v2 + v3 signatures), so you can update over any previous release without uninstalling.

## Installation

Download the latest APK from [Releases](https://github.com/The412Banner/Simple-Logcat/releases) and install it. Grant root access when prompted on first launch.

## CI

GitHub Actions builds and signs an APK on every `v*` tag push. Pre-release for tags containing `-pre` or `-beta`.

---
<sub>☕ [Support on Ko-fi](https://ko-fi.com/the412banner)</sub>
