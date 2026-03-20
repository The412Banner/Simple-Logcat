# Simple Logcat

A professional root logcat reader for Android. Real-time log streaming with color-coded log levels, package filtering with optional persistence, text search, and export to Downloads.

## Requirements

- Android 8.0+ (API 26)
- Root access (su must be available)

## Features

- **Real-time streaming** via `su -c logcat` — starts automatically on launch
- **Color-coded log levels**: V=gray, D=blue, I=green, W=orange, E=red, F/A=purple
- **Level filter chips** — toggle any log level on/off
- **Text search** — filter by tag or message content
- **Package name filter** — resolves PIDs to package names via `/proc/<pid>/cmdline` (no extra root popups)
- **Save package filter** — bookmark icon next to the filter field persists your filter across launches
- **Stack trace support** — continuation lines (exceptions, stack frames) are preserved
- **Export** — saves all buffered logs to `Downloads/logcat-<timestamp>.txt`
- **Settings**: font size, max buffer lines (500 / 1000 / 2000 / 5000 / 10000 / 20000 / ∞), auto-scroll, timestamp/PID display, line wrap

## Signing

All releases are signed with a committed testkey (v1 + v2 + v3 signatures), so you can update over any previous release without uninstalling.

## Installation

Download the latest APK from [Releases](https://github.com/The412Banner/Simple-Logcat/releases) and install it. Grant root access when prompted on first launch.

## CI

GitHub Actions builds and signs an APK on every `v*` tag push. Pre-release for tags containing `-pre` or `-beta`.
