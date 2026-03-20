# Banner Logs

A professional root logcat reader for Android. Real-time log streaming with color-coded log levels, package filtering, text search, and export to Downloads.

## Requirements

- Android 8.0+ (API 26)
- Root access (su must be available)

## Features

- **Real-time streaming** via `su -c logcat` — starts automatically on launch
- **Color-coded log levels**: V=gray, D=blue, I=green, W=orange, E=red, F/A=purple
- **Level filter chips** — toggle any log level on/off
- **Text search** — filter by tag or message content
- **Package name filter** — resolves PIDs to package names via `ps -A` (refreshed every 5s)
- **Stack trace support** — continuation lines (exceptions, stack frames) are preserved
- **Export** — saves all buffered logs to `Downloads/banner-logs-<timestamp>.txt`
- **Settings**: font size, max buffer lines (500–5000), auto-scroll, timestamp/PID display, line wrap

## Installation

Download the latest APK from [Releases](https://github.com/The412Banner/Banner-Logs/releases) and install it. Grant root access when prompted on first launch.

## CI

GitHub Actions builds a debug APK on every `v*` tag push. Pre-release for tags containing `-pre` or `-beta`.
