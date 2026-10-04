# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

The system default JDK is 24, which the Gradle wrapper cannot run on ("Unsupported class file major version 68"). Set `JAVA_HOME` to a JDK 17 first:

```powershell
$env:JAVA_HOME = "C:\Users\YES\.jdks\corretto-17.0.16"
.\gradlew assembleDebug        # Build debug APK
.\gradlew assembleRelease      # Build release APK (requires signing config)
.\gradlew bundle               # Build AAB for Play Store upload
.\gradlew lint                 # Run lint checks
.\gradlew test                 # Run unit tests (none exist yet — see Testing)
.\gradlew connectedAndroidTest # Run instrumented tests (requires device/emulator)
.\gradlew clean                # Clean build artifacts
```

Build config: compileSdk 36, minSdk 24, targetSdk 36, Java 17, Kotlin 2.3.0, AGP 8.11.1, Gradle 8.13, ViewBinding enabled. Current release: versionCode 8, versionName 1.7.

## Testing

There is **no unit-test infrastructure or meaningful test suite**. The code is almost entirely AdMob SDK + Android lifecycle integration, which is verified by **building (`assembleDebug`) and manual on-device testing**, not unit tests. Do not add placeholder/mock unit tests for the ad or lifecycle code.

## App Overview

SignMaker is a digital signature capture app. The user draws a signature on a pad, picks a pen color/width, and saves the result as a PNG to `Pictures/SignMaker/{timestamp}.png`. After saving, the app offers to open the image in the gallery. There is no in-app browsing of saved signatures — the system gallery is the viewer. Revenue model is **ads only** (AdMob) — no IAP, no premium features.

## Architecture

No fragments, no ViewModel, no repository layer. Three Kotlin files, each with a clear responsibility:

- **`MainActivity.kt`** — the single activity. Signature pad setup and save flow, pen settings dialog, banner + interstitial + exit-dialog-banner ad logic, exit-confirmation dialog.
- **`SignMakerApplication.kt`** — `Application` subclass (registered in the manifest). Calls `MobileAds.initialize()` once, owns the `AppOpenAdManager`, observes `ProcessLifecycleOwner` to detect app foreground/background, and tracks the current foreground `Activity` via `ActivityLifecycleCallbacks` (so the app-open ad knows which activity to show on).
- **`AppOpenAdManager.kt`** — loads and shows the App Open ad with all its gating (see Ad Integration).

The filesystem is the only "database" — saves are just PNG files in `Pictures/SignMaker/`; there is no record of them beyond the files themselves.

**Persisted state** — `SharedPreferences` (`"SignMakerPrefs"`):
- `redraw_count`, `signature_count` — drive the interstitial ad cadence (every 3rd clear / 3rd save)
- `pen_color` (Int), `pen_width` (Float, default 7f) — restored on launch via `applyPenSettings()`; max width = value, min width = value × 0.4
- `last_app_open_ad_time` (Long, epoch millis), `app_open_first_launch_done` (Boolean) — drive the App Open ad frequency cap and first-launch exclusion

**Key libraries:**
- `com.github.gcacace:signature-pad:1.3.1` — the drawing canvas (`SignaturePad`), from JitPack (configured in `settings.gradle`)
- `com.google.android.gms:play-services-ads:25.5.0` — AdMob banner + interstitial + app open
- `com.google.android.play:review:2.0.1` — in-app review prompt after save
- `androidx.lifecycle:lifecycle-process:2.6.2` — `ProcessLifecycleOwner` for app-open foreground detection

## Pen Settings

`showPenSettingsDialog()` (triggered by `bt_changecolor`, layout `dialog_pen_settings.xml`) shows a 5-column `GridLayout` of circular swatches built from the `penColors` IntArray (15 colors, defined in `MainActivity`) plus a width `Slider` (range 2–14). Swatches and width are drawn programmatically as `GradientDrawable` ovals; the selected swatch gets a blue border and a `ic_check_mark` tinted for contrast against the swatch luminance. Changes are applied to the pad and written to `SharedPreferences` immediately. The `penColors` comment says "10색" but the array actually holds 15 entries — keep the grid count consistent if you edit it. (There is no third-party color-picker library; it is all hand-drawn.)

## Ad Integration

All ad unit IDs are string resources in `res/values/strings.xml`, in two sets: test IDs (prefixed `TEST_`) and production IDs (prefixed `REAL_`). They are referenced in **five** places, all of which must point at the same set (`REAL_` for a Play Store release):

- `AndroidManifest.xml` — `@string/..._admob_app_id` (AdMob App ID)
- `MainActivity.onCreate()` — banner (`..._banner_ad_unit_id`)
- `MainActivity.loadInterstitialAd()` — `R.string...._FULLSCREEN_ad_unit_id`
- `MainActivity.loadExitBannerAd()` — `..._banner_ad_unit_id` (MEDIUM_RECTANGLE banner for the exit dialog)
- `AppOpenAdManager.loadAd()` — `R.string...._app_open_ad_unit_id`

Note: ad-unit-ID string resources live **only** in `res/values/strings.xml` (marked `translatable="false"`), not in the Korean resource file. The real app-open ad unit is `ca-app-pub-6729344454320392/1091955859`; the code still references the `TEST_` set during development — flip all five to `REAL_` at release.

**Banner** (`MainActivity`): created at runtime (not declared in XML) as an **Anchored Adaptive Banner** — the size is computed from screen width via `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(...)` and the `AdView` is added into the `adMobContainer` FrameLayout in `activity_main.xml`. This intentionally serves varying creative sizes (320×50, 468×60, etc.) for higher eCPM; that variation is expected, not a bug.

**Interstitial**: shown every 3rd clear (`redraw_count`) and every 3rd save (`signature_count`); reloads after dismissal.

**App Open** (`AppOpenAdManager`): shows on **cold start only** (not warm/background return), excluding the first launch after install, with a **3-hour frequency cap** (`CAP_MILLIS`) and a 4-hour ad expiry (`AD_EXPIRY_MILLIS`). `isShowingAd` prevents overlap with the interstitial. Because the ad loads over the network (~1–2 s), it appears shortly after the UI, and `SignMakerApplication.onActivityResumed` re-attempts the show once `currentActivity` is set (covers the race where the ad loads before `onResume`). To observe it: launch once (no ad — first-launch excluded), fully kill from recents, relaunch. To re-test within 3 h, clear app data or temporarily lower `CAP_MILLIS`.

The main banner and the exit banner are initialized once in `onCreate()`. `MobileAds.initialize()` is called in `SignMakerApplication`, not in `MainActivity`.

## Save Flow (`saveImg`)

1. `signaturePad.transparentSignatureBitmap` → Bitmap
2. `createAppDirectoryInDownloads()` ensures `Pictures/SignMaker/` exists, then the bitmap is written to `{timestamp}.png` via `FileOutputStream`
3. `MediaScannerConnection.scanFile` notifies the system gallery
4. Custom toast (`toast_image_layout.xml`)
5. `AlertDialog` (`custom_dialog.xml`) offers "Open" (FileProvider URI + ACTION_VIEW) or "Cancel"
6. `signature_count` is incremented. On every 3rd save the interstitial shows **instead of** the review prompt (so the two popups never overlap); otherwise `reviewApp()` runs the in-app review flow.

`FileProvider` authority: `${applicationId}.fileprovider` — paths in `res/xml/file_paths.xml`.

## Exit Flow

Back press is intercepted by an `OnBackPressedCallback` that calls `showExitDialog()` (`dialog_exit.xml`): a confirm/cancel dialog with the pre-loaded MEDIUM_RECTANGLE banner embedded (re-parented in/out of the dialog each time, reused not reloaded). Confirm calls `finish()`.

## Permissions

- Storage (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`) declared with `maxSdkVersion="32"` and requested at runtime only on API < 33 (`permission_list` is null on API ≥ 33 / Tiramisu)
- App finishes if the user denies storage permission
- `AD_ID` permission declared in manifest for AdMob

## Localization

User-facing strings live in `res/values/strings.xml` (English) and `res/values-ko-rKR/strings.xml` (Korean). Add new user-facing strings to both. Ad-unit-ID strings are `translatable="false"` and live only in the default file. Inline developer comments in `MainActivity.kt` / `AppOpenAdManager.kt` are in Korean.

## Signing & Secrets

Release keystore is `SignMaker.jks` at the project root; `private_key.pepk` is the Play App Signing export. Do not commit passwords or modify the keystore. **Note:** `SignMaker.jks` is currently tracked in git history (committed on the sdk36 line); consider `.gitignore` + history removal before pushing to a remote. `private_key.pepk` is untracked — keep it that way.

## Branches

Active development happened on `sdk36` (SDK 36 upgrade + ad work), which was merged into `master`. `master` had lagged behind because earlier work was never merged back into it.
