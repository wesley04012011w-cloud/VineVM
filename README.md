# VineVM 🌿

VineVM is an Android VM frontend and runtime abstraction for running Android guest ROMs from `.vrom` packages.

## Architecture

VineVM is intentionally backend-driven:

- **VineOS backend** — first MVP path, based on the VineOS container/runtime architecture.
- **QEMU backend** — reserved for full-system and compatibility emulation.
- **AVF backend** — reserved for Android Virtualization Framework on supported Android hosts.
- **AUTO** — chooses the best available backend.

The original VineOS project documents its runtime, ROM format, QEMU support and planned AVF backend here: https://github.com/Hexadecinull/VineOS

## Current MVP

- Kotlin + Jetpack Compose Android app
- Android 8.0+ host target
- `.vrom` file import through the Android document picker
- Local ROM storage
- SHA-256 utility for ROM verification
- VM configuration model
- Pluggable VineOS/QEMU/AVF backend interface
- GitHub Actions debug APK build

## Roadmap

1. Integrate the real VineOS runtime into `VineOsBackend`.
2. Parse and validate `manifest.json` inside `.vrom`.
3. Extract/mount system, vendor, ramdisk and kernel images safely.
4. Add persistent per-instance `/data`.
5. Connect framebuffer and input bridges.
6. Add the QEMU full-system backend.
7. Add AVF backend where the host exposes the required virtualization APIs.
8. Add VM console, logs, snapshots and multi-instance management.

## Build locally

Install Android SDK + JDK 17 and run:

```bash
gradle assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```
