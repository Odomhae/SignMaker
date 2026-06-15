# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```powershell
.\gradlew assembleDebug        # Build debug APK
.\gradlew assembleRelease      # Build release APK (requires signing config)
.\gradlew bundle               # Build AAB for Play Store upload
.\gradlew lint                 # Run lint checks
.\gradlew test                 # Run unit tests
.\gradlew connectedAndroidTest # Run instrumented tests (requires device/emulator)
.\gradlew clean                # Clean build artifacts
```

Build config: compileSdk 35, minSdk 24, targetSdk 35, Java 17, Kotlin 1.8.20, AGP 8.3.2.

## App Overview

SignMaker is a digital signature capture app. Users draw a signature on a full-screen pad, optionally change pen color via RGB picker, then save the result as a PNG to `Pictures/SignMaker/{timestamp}.png`.

## Architecture

Single-activity app (`MainActivity.kt`) — no fragments, no ViewModel, no repository layer.

**State lives in:**
- `SharedPreferences` — `redraw_count` and `signature_count` drive the interstitial ad cadence (every 3rd clear or save)
- Activity-level fields — current bitmap, ad objects, back-press timestamp

**Key libraries:**
- `com.github.gcacace:signature-pad:1.3.1` — the drawing canvas (`SignaturePad`)
- `me.jfenn.ColorPickerDialog:base:0.2.2` — RGB color picker dialog
- `com.google.android.gms:play-services-ads:22.2.0` — AdMob banner + interstitial
- `com.google.android.play:review:2.0.1` — in-app review prompt

Both libraries come from JitPack (configured in `settings.gradle`).

## Ad Integration

There are two sets of ad unit IDs in `MainActivity.kt` — test IDs (prefixed `TEST_`) and real IDs (prefixed `REAL_`). The active IDs in use must match before a Play Store release.

- Banner ad: loaded in `onStart()`, displayed at the bottom of `activity_main.xml`
- Interstitial ad: shown every 3rd clear (`redraw_count`) and every 3rd save (`signature_count`); reloaded automatically after dismissal via `onAdDismissedFullScreenContent`

AdMob App ID: `ca-app-pub-6729344454320392~8704690482` (set in `AndroidManifest.xml`).

## Save Flow

1. `signaturePad.transparentSignatureBitmap` → Bitmap
2. Written to `Pictures/SignMaker/{timestamp}.png` via `FileOutputStream`
3. `MediaScannerConnection.scanFile` notifies the system gallery
4. Post-save `AlertDialog` offers "Open" (via `FileProvider` URI + Intent) or "Cancel"
5. In-app review flow triggered; `signature_count` incremented

`FileProvider` authority: `com.odom.signmaker.fileprovider` — paths defined in `res/xml/file_paths.xml`.

## Permissions

- Storage (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`) requested at runtime only on API < 33
- App finishes if the user denies storage permission
- `AD_ID` permission declared in manifest for AdMob

## Signing

Release keystore is `SignMaker.jks` at the project root. `private_key.pepk` is the Play App Signing export. Do not commit passwords or modify the keystore.
