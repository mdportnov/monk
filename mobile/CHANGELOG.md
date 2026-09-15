# Changelog — Monk for Android

Sections are keyed by version; `mobile-release.yml` publishes the matching section as the
release notes, and the app shows them in the update card. Keep entries short and user-facing.

## 1.8.0

- Routines: named hours with a strictness of their own, over whatever each app is already set to. Four come with Monk — Focus, Morning, Evening wind-down, Work hours — and you can make your own, name it and give it an emoji. A routine can only tighten: it never opens what you closed.
- Start one by hand and it holds until its time: no stopping it, no break lifting it, no switching Monk off under it. Leave it to its own hours and it behaves like any other layer.
- Routines keep their own hours and work outside the base hours, which is the only way to be protected out there.
- Focus is now one of the routines rather than a thing of its own; a session already running carries over.
- The base hours moved onto the routines page, next to what they interact with. An app outside those hours says "off now" instead of promising a pause it would not give.
- Apps join a routine in one step: search every app on the phone from inside the routine, and what you pick lands on your list and in the routine at once.
- Under strict mode a routine — and the base hours — can be tightened but never softened. Switching the base hours off counts as tightening: off means every minute.
- A break is now visible and can be ended wherever it is running, including outside the base hours, where it used to disappear.
- Fixed: a break could lift a routine while every screen still said the routine was holding.
- Fixed: the top bar could freeze after returning from a page — opaque over a page scrolled to the top, or transparent over one that was not.
- Fixed: "closed until" counted only the rule's own window and promised an hour that opened nothing; "back at" could show the minute before the hours actually return.
- Fixed: the shade tiles offered breaks they would then refuse, invented a next-break time, and claimed another routine's session as focus.

## 1.7.5

- A Morrow entry under "About": the other app from the same workshop, one tap to its page, where it is described and downloaded for Android.

## 1.7.4

- The home list can be scrolled past the "Add apps" button: with a short list the last app stayed pinned under it, and there was never enough scroll left for the header to settle into the bar.
- The top bar never goes blank: before protection is set up, scrolling used to leave an empty strip of glass where the wordmark had been, and the condensed status line went missing once its card had scrolled away.
- The quote of the day drops the coloured rule beside it.

## 1.7.2

- One truth for the protection state everywhere: the card, the compact bar, the shade tiles and the notification agree. Off by schedule says when it comes back, the switch turns Monk off without a countdown then, breaks are refused while the schedule has protection off, focus stays available.
- A break ended by focus, strict mode or switching off still counts for the half-hour cooldown; switching to Block drops a live "open for" allowance; app rows show the mode in force right now; forgotten apps keep their names in stats.


- Fixed: after returning to the app the glass bar could stay drawn, empty, over a fully open home screen.

## 1.7.0

- Per-app time rules: block or free an app on chosen days and hours, several rules per app, windows across midnight. Reset in one tap, or lock the app so only deleting it changes anything.
- A "How it works" sheet: the compulsion loop, why a 10-second pause works, and the setup steps.
- Protection card rebuilt: animated state (on / break / focus), explanations on tap, a countdown before turning protection off, a gentle notice when focus cannot be stopped.
- Focus 15 / 30 / 45 / 60 min, break options, a living breathing orb on the pause screen with inhale / exhale.
- Home: today's numbers, when the current state ends, week trend, a quote of the day.
- Haptics on sliders and day toggles, optional live status notification, Quick Settings tiles.
- Page transitions with a synced glass header; settings rows aligned and shorter; natural Russian copy throughout.
- Removing an app keeps its settings and restores them when it is added again; timezone changes re-evaluate rules at once.
- Pause screen follows the app language; rule boundaries are computed in wall-clock time (DST safe); stats live in their own file and grow slower.
- One motion system: shared-axis page transitions with the header moving as one, a sheet that follows the finger and animates out, tab pill that glides, counters that roll.
- Starting a break takes the same 10-second breath as switching off, in the app and in the shade tile; a new break waits 30 minutes after the last; strict mode refuses off, breaks, removing apps and resetting stats; clock changes cannot shorten focus.
- Quick Settings tiles: the app knows which ones are already in the shade and asks only for the missing one; instructions on Android 12 and older.
- Survives being swiped away or restarted by the system: foreground app and timers are restored, clock drift while asleep is applied; a checklist for battery optimisation, app hibernation and Samsung sleeping apps.
- Screen time from the phone's own usage record: whole phone and your list, by day and by app, a full page with per-app detail, a daily archive so the record outlives the system's ten days; usage access is opt-in.
- The protection card is the heart of the home screen: a living surface whose colour is the state, big countdowns, a ring; on scroll it flows into the glass bar with its switch.
- Stats explained in place: every counter, colour and bar has a caption; pauses without an outcome are visible; leaving the pause screen with a gesture counts as walking away.
- Back from a page two levels deep returns where you were, tab and scroll included.
- Strict mode: until midnight, one hour, or any length on a slider.
- A smoother pause screen: it fades in as one, the ring fills continuously, the breath settles instead of snapping.
- Newcomers are asked for notification permission once on the home screen; back arrows and icon buttons are described for TalkBack; dialogs and pages survive a theme or language change.
- Fixed: a cold-started app was counted as two pauses; an app removed from the phone now stays listed as "not on this phone" with its settings kept.
- Android 10 and newer. Fixed: stats rows showing another app's icon; leaving the pause screen with a gesture now counts as walking away; Android 16 back gesture on the overlay; live status permission.

## 1.6.0

- First screen tells a story: turn the service on, see how it works, pick apps. Pause / focus controls and the week card appear once there is something to control.
- Clearer wording in both languages: "Your apps", "Pick your apps", "Usual suspects", "Add all" / "Choose manually", "Same as in Settings", "Start" / "End".
- Weekday toggles that never wrap; a "made with ♥" link to mikeportnov.com in Settings.

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
