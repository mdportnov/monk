# Monk mobile

Kotlin Multiplatform companion of the `monk` CLI: a OneSec-style pause between you and the apps
you open on autopilot. Android is the working target; iOS compiles the shared UI but every
blocking API is a stub until the Screen Time entitlement is wired in.

## How it works (Android)

1. `MonkAccessibilityService` listens to `TYPE_WINDOW_STATE_CHANGED` events only. Window content
   retrieval is disabled in `res/xml/monk_accessibility_service.xml`, so the service sees package
   and class names and nothing else. Only windows whose class resolves to an Activity count:
   dialogs, PiP, bubbles and toasts are ignored.
2. On every switch into a new package the shared `BlockPolicy` decides: not watched, protection
   off, outside the schedule or inside a live allowance → allow; otherwise intercept. Launchers
   reset the "current app"; system UI, keyboards, permission dialogs and the dialer are transparent.
   Nothing fires while the keyguard is up or a call is ringing / in progress.
3. `InterceptActivity` (own task, no Recents, `noHistory`) lands on top of the app:
   - **Pause** — breathing orb + countdown (ticks only while resumed, so the shade cannot wait it
     out); "Open for N min" unlocks after the countdown and grants a per-app allowance; "Not now"
     sends you Home through `performGlobalAction`.
   - **Block** — only "Back to focus".
   When an allowance runs out while the app is still open, the service re-intercepts on the spot.
   An app resurfacing above a live intercept (notification deep link) gets covered again.
4. Fail-closed: starting an Activity from a bound accessibility service is exempt from
   background-launch limits, but if an OEM ROM drops it anyway and the screen has not resumed
   within 2.5 s (three retries for a slow cold start), the pause screen is drawn as an overlay
   instead, and Home is the last resort. After two misses in a row the overlay goes first.
5. Config, allowances and stats live in `SharedPreferences` as JSON (`MonkStore`), stats in
   their own file. `allowBackup=false` and no cloud / device-transfer extraction; the only network
   call is the update check against GitHub Releases.

Never gated, and hidden from "Add apps": Monk itself, launchers, Settings, the dialer / telecom /
in-call UI, emergency, permission controller, package installer, keyboards. This is enforced in
the service too, so an edited config cannot lock the phone.

Android 13+ and APKs installed from a downloaded file: the accessibility toggle is greyed out.
Tap it once anyway, then *App info → ⋮ → Allow restricted settings*. The setup card links there.
`adb install` is not affected.

**Overlay mode** (Settings → Pause screen) draws the pause screen as a `TYPE_ACCESSIBILITY_OVERLAY`
window owned by the service instead of an Activity: no permission, immune to background-launch
rules, covers split-screen. It is also the automatic fallback when the Activity never resumes.

**Quick Settings tiles**: "Monk pause" toggles a 15-minute pause, "Monk focus" starts a 30-minute
focus session after a confirmation. **Service watchdog**: a notification when the accessibility
service is off while apps are watched (on unbind, after boot, after an update); opt-in in Settings.

Known gaps: work-profile clones of a watched app are intercepted too and "Open" launches the
personal instance. Moving the system clock shifts break / focus / strict deadlines with it (measured
against the boot clock), so it neither shortens nor stretches them; Settings is deliberately never gated.

## Architecture

- `shared` owns the model (`BlockPolicy`, `MonkStore`), the Compose UI and the platform
  contracts (`MonkPlatform`, `Updater`). The UI receives a `MonkGraph` explicitly; there is no
  global service locator in common code.
- `androidApp` builds an `AndroidGraph` in `MonkApplication` (store, platform, updater,
  `InterceptRegistry`), reachable as `context.monkGraph`.
- `ForegroundGate` is the accessibility service's decision tree without Android types (JVM unit
  tests in `androidApp/src/test`); `MonkAccessibilityService` is the adapter that feeds it window
  events and carries out its effects (launch, overlay, Home, timers).
- One intercept = one `InterceptSession` keyed by a token, created and counted once by the
  service; `InterceptActivity` and `InterceptOverlay` are two renderers of its `InterceptUiState`.

## Layout

```
shared/        KMP library: model + BlockPolicy, MonkStore, Compose Material 3 UI, EN/RU strings
  commonMain   everything platform-neutral
  androidMain  SharedPreferences store, app icons via PackageManager
  iosMain      NSUserDefaults store, inert IosPlatform, MainViewController()
  commonTest   BlockPolicy / schedule / store round-trip tests
androidApp/    Application, MainActivity, InterceptActivity, MonkAccessibilityService, icon
iosApp/        XcodeGen spec + SwiftUI shell (needs Xcode + `xcodegen`)
```

## Install and update

Download `monk-android-X.Y.Z.apk` from the latest `mobile-vX.Y.Z` release on GitHub, install it,
enable the accessibility service from the setup card. From then on Monk checks GitHub Releases
itself (at most every 6 hours, on foreground) and offers the update in the app: the APK is
streamed to private cache, its SHA-256 compared with the published one, and handed to the system
installer. Android accepts the update only if it is signed with the same key, so releases are
signed with one keystore that must never change (`~/.monk/release-signing` locally,
`MONK_KEYSTORE_*` secrets in CI).

## Release

```sh
git tag mobile-v1.2.3 && git push origin mobile-v1.2.3
```

`.github/workflows/mobile-release.yml` builds the signed APK, writes the checksum and publishes
both to the release `mobile-v1.2.3`. Versions come from the tag; local builds are `0.0.0-dev`.
A local release-signed build:

```sh
set -a; source ~/.monk/release-signing/credentials.env; set +a
./gradlew :androidApp:assembleRelease -PmonkVersion=1.2.3
```

## Build

```sh
./gradlew :androidApp:assembleDebug        # APK → androidApp/build/outputs/apk/debug/
./gradlew :shared:testAndroidHostTest      # shared unit tests on the JVM
./gradlew :androidApp:testDebugUnitTest    # service decision tree + version compare
./gradlew :androidApp:installDebug         # onto a connected device / emulator
```

Enable the service from a shell instead of clicking through Settings:

```sh
adb shell settings put secure enabled_accessibility_services com.mdportnov.monk/com.mdportnov.monk.MonkAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Requires JDK 17, Android SDK 36, Gradle wrapper 9.6 (bundled), Kotlin 2.4, AGP 9.2,
Compose Multiplatform 1.11 — same toolchain as `punto-cero/apps/mobile`.
