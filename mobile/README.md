# Monk mobile

Kotlin Multiplatform companion of the `monk` CLI: a OneSec-style pause between you and the apps
you open on autopilot. Android is the working target; iOS compiles the shared UI but every
blocking API is a stub until the Screen Time entitlement is wired in.

## How it works (Android)

1. `MonkAccessibilityService` listens to `TYPE_WINDOW_STATE_CHANGED` events only. Window content
   retrieval is disabled in `res/xml/monk_accessibility_service.xml`, so the service sees package
   names and nothing else.
2. On every switch into a new package the shared `BlockPolicy` decides: not watched, protection
   off, outside the schedule or inside a live allowance → allow; otherwise intercept.
3. `InterceptActivity` (own task, no Recents, `noHistory`) lands on top of the app:
   - **Pause** — breathing orb + countdown; "Open for N min" unlocks after the countdown and
     grants a per-app allowance; "Not now" sends you Home.
   - **Block** — only "Back to focus".
4. Config, allowances and stats live in `SharedPreferences` as JSON (`MonkStore`).

"Display over other apps" is optional: accessibility services are exempt from background
activity-start limits, but some OEM ROMs ignore that, so the setup card offers it.

Android 13+ and sideloaded APKs: the accessibility toggle is greyed out until you open
*App info → ⋮ → Allow restricted settings*. The setup card links there.

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
