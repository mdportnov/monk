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
   within 1.5 s, the user is sent Home rather than left in the app.
5. Config, allowances and stats live in `SharedPreferences` as JSON (`MonkStore`). No INTERNET
   permission, `allowBackup=false`: nothing leaves the device.

Never gated, and hidden from "Add apps": Monk itself, launchers, Settings, the dialer / telecom /
in-call UI, emergency, permission controller, package installer, keyboards. This is enforced in
the service too, so an edited config cannot lock the phone.

Android 13+ and APKs installed from a downloaded file: the accessibility toggle is greyed out.
Tap it once anyway, then *App info → ⋮ → Allow restricted settings*. The setup card links there.
`adb install` is not affected.

Known gaps: work-profile clones of a watched app are intercepted too and "Open" launches the
personal instance; in split-screen / desktop windowing the intercept may open as its own window
instead of covering the target (an accessibility overlay would fix both, not built yet).

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

## Build

```sh
./gradlew :androidApp:assembleDebug        # APK → androidApp/build/outputs/apk/debug/
./gradlew :shared:testAndroidHostTest      # shared unit tests on the JVM
./gradlew :androidApp:installDebug         # onto a connected device / emulator
```

Enable the service from a shell instead of clicking through Settings:

```sh
adb shell settings put secure enabled_accessibility_services com.mdportnov.monk/com.mdportnov.monk.MonkAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Requires JDK 17, Android SDK 36, Gradle wrapper 9.6 (bundled), Kotlin 2.4, AGP 9.2,
Compose Multiplatform 1.11 — same toolchain as `punto-cero/apps/mobile`.
