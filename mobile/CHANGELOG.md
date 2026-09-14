# Changelog — Monk for Android

Sections are keyed by version; `mobile-release.yml` publishes the matching section as the
release notes, and the app shows them in the update card. Keep entries short and user-facing.

## 1.5.1

- Russian labels shrink to fit instead of wrapping in segmented controls, the dock and chips.
- Tablets and unfolded foldables: a side rail instead of the dock, content capped at a readable width.
- Landscape and small windows: the pause screen scales its orb and scrolls instead of clipping.
- Large system font: the setup button grows instead of clipping its label.
- An app that surfaced under the lock screen is judged again on its next window if the unlock event never arrived.

## 1.5.0

- Russian: pick the language in Settings (system / English / Русский); on Android 13+ the tiles, notifications and service name follow.
- Liquid glass: a violet-black top bar that condenses out of the page as you scroll, and a floating glass dock.
- Settings rebuilt as proper rows with icons, value pills and plain-language hints for every control.
- "How Monk works" on first run and in Settings; a step-by-step card for turning the accessibility service on.
- Suggested apps: Instagram, YouTube, TikTok, Threads, Telegram and other known time sinks, only the ones installed, one tap to add.

## 1.4.0

- Theme choice applies everywhere: window background and status-bar icons follow it from the first frame; Material You option on Android 12+.
- Update download is 8× smaller (R8 shrinking).
- Focus started from the tile now wins over a pause screen that was already open.
- Closing a pause screen right before reopening the same app can no longer let it through.
- Overlay respects the status bar and gesture area; its countdown pauses under the shade.
- Unreadable settings are kept as a backup instead of being reset.

## 1.3.0

- A face of its own: Montserrat, white cards on a tinted ground, a wordmark with the gradient underscore.
- Status pills instead of chips in app rows, editorial section titles, a 7-day sparkline on the week card.
- Weekday labels under the daily chart, animated list changes.

## 1.2.0

- Quick Settings tiles: "Monk pause" (15 min, toggle) and "Monk focus" (25 min, confirmed).
- Overlay mode: the pause screen drawn on top of everything, for split-screen and ROMs that drop it; also the automatic fallback.
- A notification when the accessibility service stops (after reboot, update, or a system kill). Opt-in, on by default.
- Your own line on the pause screen.

## 1.1.0

- Focus session: 25 or 50 minutes during which every watched app is blocked, no early exit.
- Pause screen: a draining countdown ring, a buzz when the wait is over, "3rd time today".
- App card shows today's pauses; stats by app show the walk-away percentage.
- Chips wrap on narrow screens; empty states in stats.

## 1.0.0

First release.

- Pause or block the apps you open on autopilot: breathing countdown, "open for N minutes", hard block.
- Daily open limit per app, schedule, pause protection, one-way strict mode.
- "Why?" prompt before opening; stats by day, app, hour and reason.
- Self-update from GitHub Releases.
