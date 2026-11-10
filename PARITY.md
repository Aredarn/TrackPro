# Android and web parity

TrackPro has two clients: this Android app and the web app in
[TrackPro-WEB](https://github.com/Aredarn/TrackPro-WEB). They should do the same things the same
way. This file is the list of what each one has, and the rules that keep them in step.

**Android is the source.** Wording, shared test cases and this file are edited here, then copied
to the web with `npm run sync:android` in TrackPro-WEB.

## The rules

1. **Port in the same piece of work.** A change to timing, track geometry, drag or comparison
   maths, the TrackBoard payloads or the sync rules goes into both apps before either is released.
   If one side can't have it, add it to "On purpose" below with the reason.
2. **Shared behaviour gets a shared case.** Put inputs and expected outputs in `shared/fixtures/`.
   `SharedFixturesTest` (Android) and `src/shared/fixtures.test.ts` (web) both run every file
   there. A case that passes on one side and fails on the other is a parity bug.
3. **Text lives in `strings.xml`.** The web builds its text from it. Never add a string only on
   the web, except for browser-only text in `src/i18n/web.ts`.
4. **Update this table** in the same commit as the feature: version or branch, on both sides.

## Features

`v1.5` means released in that Android version. "web" means it is on the web's deployed branch.
A branch name means it is not merged yet.

| Feature | Android | Web | Notes |
|---|---|---|---|
| Circuit and sprint timing, sectors | yes | yes | Same gate rules; tested by `timing`/`trackGeometry` tests on both |
| Live delta (session best, track best) | yes | yes | |
| Drag mode: splits, quarter and half mile | yes | yes | Shared case: `drag-run.json` |
| GPS: phone | yes | yes | Phone GPS is about 1 Hz: drag power per kg and gear changes need 10 Hz |
| GPS: Bluetooth / ESP32 receiver | yes | Web Serial only | Browsers have no Bluetooth serial |
| GPS simulator | yes | yes | |
| Garage: cars, specs | yes | yes | |
| Car photo upload | yes | shows only | |
| Vehicle catalogue picker | `feature/vehicle-picker` | no | |
| Track builder with road snapping | yes (gates fix on `claude/musing-blackwell-6c3808`) | yes | Android `main` still builds gates along the road until that branch merges |
| Premade tracks | yes | yes | Same names and ids |
| History, filters, session and lap detail | yes | yes | |
| Lap against lap overlay | yes | yes | |
| Drag and track comparison | v1.3 | yes | Shared case: `drag-run.json` (`pull`) |
| Weather at session start | yes | yes | |
| TrackBoard account, garage sync, restore | yes | yes | |
| Leaderboards per car | v1.5 | yes | |
| Lap top speed on TrackBoard | v1.5 | yes | Shared case: `lap-top-speed.json` |
| Drag leaderboard | v1.4 | yes | |
| Events | yes | yes | |
| Profile edit, export, account deletion | yes | no | |
| Full session backup | `feature/full-backup` | no | Needs `/me/backup` on TrackBoard |
| Settings backup | `feature/full-backup` | no | |
| Settings: units, language, sunlight, delta reference | yes | yes | |
| Settings: theme, GPS rate | yes | no | |

## On purpose

Differences that are meant to stay:

- **Bluetooth GPS:** the web reaches a receiver over Web Serial (desktop Chrome and Edge), not
  Bluetooth.
- **Stored laps on the web:** rows recorded before the web kept the finish-line fix in the lap it
  closes are repaired when read (`lapTrace`). Android rows never had that gap.
- **Simulator sessions** stay in the browser that recorded them and are never uploaded, on both.

## Shared fixtures

| File | Covers |
|---|---|
| `lap-time.json` | Parsing and formatting `MM:SS.hh` lap times and deltas |
| `lap-top-speed.json` | A lap's top speed for TrackBoard |
| `drag-run.json` | One 10 Hz recording: drag splits in both unit systems, and the comparison's reading of the pull |

When you add one: write the file here, run it from `SharedFixturesTest`, run `npm run
sync:android` in TrackPro-WEB and add it to `src/shared/fixtures.test.ts` there.
