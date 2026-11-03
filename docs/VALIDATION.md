# Validation scope

## Automated checks

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- 11 tests for elapsed work, short upgrades, potion stacking, early cancellation, boost expiration, research/pet exclusion, and progress limits.
- 9 import tests including a real-export-shaped anonymized fixture, unknown IDs, validation errors, stale timestamps, Goblin slots, research boost import, and a loop over Town Hall levels 1–18.
- 15 boost/format tests for category isolation, potion/snack overlap, Clock Tower queues, additive helpers, opt-in repeated sessions, event windows, boost serialization, the supplied Apprentice export shape, old snapshots, and finish-time boundary checks.
- 4 countdown precision tests covering clock seconds, minute/hour/day carries, subsecond rounding, completed timers, and minute-based word formats.
- 10 reminder planner tests covering future-only arming, one-time completion delivery, potion/timer rescheduling, deletion, category filtering, old snapshot imports, missed alarms after reboot, and separate village keys.

The TH1–18 test varies the Town Hall field in a fixed fixture. It verifies parser flexibility; it does not validate all possible real village layouts, units, special upgrade types, or future game changes.

## Device checks

Android 16 / API 36 x86_64 emulator:

- Install/update and launch the app with preserved data.
- Paste → preview → import an export, including Town Hall artwork and 13 upgrade timers.
- Home Village and Builder Base tab selection.
- Android text-share → preview → import using the supplied Apprentice export, preserving its level-8 assignment and original timestamp.
- JSON document picker → preview, with cancel preserving the current village.
- Pet/Research Potion recording, persisted boost windows, repeat-helper preference, and countdown format choices.
- Clock countdowns observed advancing while the app was in the background. Active Chronometers can prevent UI Automator from reaching idle, so widget ticking is also checked with emulator screenshots.
- README screenshots captured directly from the API 36 emulator with anonymized demo data.
- Pin the widget with imported data, artwork, scrolling rows, and live countdowns.
- Notification permission and exact-alarm settings flows.
- A scheduled ten-second notification arrived while the app was in the background.
- A one-minute upgrade created through the UI survived an emulator reboot and produced its completion notification without reopening the app. The completed timer was removed from the armed schedule afterward.
- Notification dismissal through its action button.
- A scheduled alarm-mode notification used the repeating alarm flag; **Stop alarm** canceled it before its timeout. The emulator was started with host audio disabled, so physical speaker/vibration and Do Not Disturb behavior still require a real-device check.

Further Android versions, physical devices, manufacturer battery policies, accessibility font scales, rotation states, and multiple launcher implementations still need coverage before claiming broad production stability.

## Known data limits

- The app works from an imported snapshot (paste, file or Android share); it does not observe game activity continuously.
- Builder, Research and Pet Potions, Builder Bite, Clock Tower and Study Soup are modeled. Active Apprentice/Lab Assistant assignments use exported helper data; future repeated sessions are optional forecasts. Other helper types are not modeled.
- The active Apprentice case is checked against a supplied real export. Other boost combinations have synthetic coverage; a real active Pet Potion export is still needed to confirm its export key. Manual Pet Potion recording is available.
- Event multipliers are manual forward-looking adjustments, off by default. Imported timers may already include event/Gold Pass duration discounts; applying those again would shorten estimates incorrectly.
- Unknown IDs retain their timers under editable placeholder names and generic artwork.
- Building art is illustrative and may show a different level from the exact upgrade.
- Progress starts from the remaining work at import, rather than a complete upgrade duration database.
- Notification delivery can be delayed by Android or device restrictions. Alarm tones respect Android sound settings and stop automatically after one minute.
