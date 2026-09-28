---
target: find design flaws and improvements
total_score: 21
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 4
target_identity: "file:C:\\Users\\meszaros.martin\\StudioProjects\\TrackPro\\app\\src\\main\\java\\com\\example\\trackpro"
timestamp: 2026-09-25T09-55-06Z
slug: app-src-main-java-com-example-trackpro
---
Method: dual-agent (A: design review · B: deterministic evidence)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Link/fix/rate state is excellent; ending a session produces no confirmation at all (`TimeAttackViewModel.kt:145-152` saves silently in `onCleared()`). |
| 2 | Match System / Real World | 3 | Fluent motorsport language (purple = session best, trap, stint, sectors), undercut by the board calling session *counts* "Track records / Drag records" (`MainActivity.kt:800,806`). |
| 3 | User Control and Freedom | 2 | Ending a live session is an unguarded back tap. No undo, no discard, no VOID, no re-attribution. |
| 4 | Consistency and Standards | 2 | Drag HUD speaks a different vocabulary from the track HUD; drawer and board name the same five destinations differently; `danger` carries three meanings. |
| 5 | Error Prevention | 2 | `DeltaReferenceSwitch` (`TimeAttackScreen.kt:664-700`) looks like a two-option picker, behaves as a blind toggle — tapping the *active* label flips it. |
| 6 | Recognition Rather Than Recall | 3 | Everything labelled, `captionForDelta` exemplary; but readouts and nav targets look identical, with `"›"` the only tappability cue and only on 4 of 6 board fields. |
| 7 | Flexibility and Efficiency | 2 | Good preference persistence; no landscape on drag, no bulk actions, no per-session vehicle memory. |
| 8 | Aesthetic and Minimalist Design | 2 | Track HUD and board are disciplined; drag HUD is 9 identical dim tiles + a smoothed chart + a duplicated clock inside a scroll view. |
| 9 | Error Recovery | 1 | The screen PRODUCT.md names as the hardware-recovery surface says `OFFLINE`/`SEARCHING` and offers no cause and no next step (`ESPConnectionTestScreen.kt:180-196`). |
| 10 | Help and Documentation | 1 | No first-run guidance, no firmware/wiring link, no colour legend, no explanation of the ESP rig anywhere. |
| **Total** | | **21/40** | **Acceptable — bottom of the band (52.5%)** |

No heuristic scored n/a. Applicable maximum 40.

## Design Specificity Verdict

**Split, and the split is the story.** Four surfaces could not be lifted into another product. One — the drag HUD — could be dropped into a running app almost unchanged.

The grounded part is genuinely grounded. `Instrument`, `Bezel`, `Readout`, `DashAction` and `SegmentBar` are a different containment model, not a restyled card system: full-bleed fields on 1dp milled hairlines, zero radius everywhere, placard caps at 10sp/1.4sp, a 21-block bidirectional bar with a narrowed centre datum, tabular figures enforced at the token with tracking that scales with size. `SectorSplitsRow` — where a sector's block is as wide as the time it took — does not exist in AiM or MoTeC. `captionForDelta` has five distinct captions including "no track best yet", which almost nothing in this category bothers with.

Then `DragScreen.kt`. Strip the words out and you have an inset rounded card with a chevron dropdown, a nine-tile KPI grid where every tile is the same size and colour, a 300dp cubic-bezier area chart with an alpha fill, and a full-width primary button pinned at the bottom. It uses `AppCard`/`PrimaryButton` where the world's promoted control is `DashAction`, and it contains **no `SegmentBar` at all** — despite the direction contract naming it as the signature interaction on exactly this surface.

Two further leaks: the **trend raise is dead code** (`Trend`/`TrendMark` exist, `Instrument(trend=)` is wired, and across 23 call sites it is never passed once), and the **VOID raise was never built** (no discard path exists anywhere).

**Deterministic scan:** the bundled detector ran and returned `[]` with exit 0 — **not a pass**. Its `SCANNABLE_EXTENSIONS` list has no `.kt`, so it matched 0 of 102 Kotlin files. It has no verdict on this codebase. Browser injection was skipped: native target, no viewable page. Every mechanical finding below came from source analysis, not from the detector.

## Overall Impression

The visual system is doing better work than the layer around it. The board, track HUD and arming screen are authored, disciplined and in places genuinely original. The score is 21/40 almost entirely because of rows 9 and 10: when something fails — no rig, no GPS, a track with no coordinates, a session that won't save — the app has nothing to say. The single biggest opportunity is not visual. It is that the app never tells you anything went right or wrong.

## What's Working

**`captionForDelta`** (`TimeAttackScreen.kt:639-654`) — the delta states what it is actually measured against, including when that isn't what you chose. PRODUCT.md principle 5 shipped as real behaviour on the surface where it matters most.

**The pit board fits instead of scrolling.** `FittedLapRows` (`:830-874`) subcomposes newest-first and stops when the next row won't fit whole; `LapRow`'s custom `Layout` (`:891-978`) places sector splits only if *all* of them fit, because a row showing S1 and S2 but not S3 reads as a two-sector lap. Correct reasoning, correct implementation.

**`SectorSplitsRow` sizes a sector by its duration** (`:719-764`). A slow sector is physically longer before a digit is read, with a floor so nothing collapses. The most original thing in the build.

**Flagging rather than hiding:** `CompletedLap.signalGap` renders a red `GPS GAP` tag on the affected lap instead of dropping it — "the driver judges." The one place the build handles uncertainty with confidence.

## Priority Issues

### [P0] The app says your data is gone while it is still loading
Four list screens branch on `.isEmpty()` with no loading state (`CarListView.kt:106`, `DragTimesList.kt:81`, `TimeAttackListView.kt:81`, `TrackListView.kt:80`). Their flows start at `MutableStateFlow(emptyList())`, so the first frame after navigation reads "No sessions recorded — run a drag session to see it here" before the query returns. `TrackScreen.kt:195-207` is worse: loading, empty and error are one branch, so a track with no stored coordinates spins forever. And `TimeAttackScreen.kt:180-182` is the only try/catch in `screens/` — it logs and continues, so a failed track load drops you into a live timing HUD with no track.

**Why it matters:** we fixed a real drag-session data-loss bug this week. A driver who has just lost a session once will read "No sessions recorded" as confirmation it happened again. The app cannot distinguish "nothing here" from "haven't looked yet", and neither can they.

**Fix:** a three-state seal on every data screen — loading / empty / error, never two of them sharing a branch. `DragScreenListItem.kt:126` already does this correctly and even carries a comment saying the two states "used to render identically"; make that the pattern. Surface the swallowed exception.
**Suggested command:** `/impeccable harden`

### [P1] The drag HUD is not built to the direction
No landscape branch anywhere in the file — `LocalConfiguration` appears in exactly one file in the app, and it isn't this one. The whole HUD is inside `verticalScroll` (`:160`), so the readout's position depends on a scroll offset. No `SegmentBar`. Nine identical 22sp tiles (`:352-369`), most reading `––.–`, plus a `CUBIC_BEZIER` filled chart (`:419`) that interpolates speed never reached.

**Why it matters:** at the strip, mounted landscape, gloves on, the only thing that matters is the split that just landed — and it is in an orientation never laid out, at a scroll position the driver can't set.

**Fix:** mirror `TimeAttackScreen`'s structure. Landscape branch; drop the scroll; run-time surface is `Readout` + live instruments + a `SegmentBar` for split severity and nothing else; move the tile table and chart behind the same toggle `HudTrailing` already proves. Chart to `LINEAR`, drop the fill.
**Suggested command:** `/impeccable adapt`

### [P1] Faults render dimmer than health, below AA, on the sunlight surface
`NO SIGNAL` uses `textFaint` at **3.35:1 measured** (`TimeAttackScreen.kt:601-603`); `LIVE` uses `deltaGood` at **9.82:1**. The fault is three times less visible than the healthy state. The board repeats the inversion with a third treatment (`NO LINK` in `markingDim`).

`textFaint` is documented as "large marks only" and is used at 10–13sp at **all 22 call sites** — every empty state, every loading label, every not-found message. Worst case: `EmptyState.kt:31` composites to **2.21:1**, and that is first-run copy telling users what to do.

Four of six contrast claims in the `TrackProColors.kt` KDoc are wrong (`:86` claims 16.9, measures 17.76; `:88` claims 3.2, measures 3.35; `:117` claims 4.8, no ground yields it).

**Fix:** a fault is never dimmer than health — `NO SIGNAL` takes `deltaBad` minimum. Retire `textFaint` below 14sp or lift it to 4.5:1. Correct the KDoc to measured values. Resolve `danger`'s three meanings (delete / genuine fault / "recording live") — move REC to a lit block, which is how real hardware says "on".
**Suggested command:** `/impeccable audit`

### [P1] Ending a session has no guard, no acknowledgement, and fails silently
The only exit from a live session is the generic 48dp back arrow, top-left — the corner your hand lands on adjusting a mount. It pops the nav entry, `onCleared()` fires, `endSession()` runs, nothing is shown. No "End session?", no "Saved — 12 laps, best 1:42.881", no discard. A failed persist is `Log.e` only (`DragRecordingViewModel.kt:205`).

**Why it matters:** this is a driver's whole track day, and the app's riskiest action has less friction than deleting a saved session, which does get a confirm dialog.

**Fix:** while live, replace the back arrow with an explicit `DashAction("End session")` behind a confirm. On end, show a summary face — laps, best, theoretical gain — before returning. That is also where VOID belongs.
**Suggested command:** `/impeccable harden`

### [P1] The rig screen diagnoses nothing
`Link: OFFLINE`, `Fix: SEARCHING`, and a `gpsData.toString()` dump (`ESPConnectionTestScreen.kt:180-196`, `:230-238`). No cause, no next step, no retry, no firmware link. PRODUCT.md principle 2 — "a stranger has to succeed alone" — was written about this screen.

The model already exists in the codebase: `SettingsScreen.kt:212-216` says "No paired devices — pair the ESP32 in Android Bluetooth settings first."

**Fix:** a diagnosis field that reads the actual failure — wrong SSID, socket refused at `192.168.4.1:4210`, no bytes in N seconds, bytes unparseable — each with one recovery sentence in that same voice. A "Retry link" action. A persistent firmware-repo link.
**Suggested command:** `/impeccable clarify`

## Persona Red Flags

**Driver mid-session (mounted, gloves, sunlight)**
- The delta boils: `%+.3f` undamped at 60–84sp. `SegmentBar` springs its fill precisely because "a snapping readout is a lie" — the numeral above it does not. At 1Hz phone GPS, three decimals is a precision claim the source can't back.
- Loss of signal at 3.35:1 / 10sp in direct sun while every number below it stops meaning anything.
- `DeltaReferenceSwitch` is a trap: looks like a segmented picker, tapping *anywhere* — including the active label — flips what every delta is measured against. Colour is the only state cue (6.13:1 vs 3.35:1), no `Role`, no `stateDescription`.
- `MAP` defaults on, costing the delta 24sp (84→60) and putting a MapLibre surface redrawing every GPS tick in front of a driver the screen's own comment says "needs it least."
- Correct and worth keeping: `KeepScreenOn`, the lap haptic keyed to the counter so it fires exactly once, `mapVisible` in `rememberSaveable` so rotating in a mount doesn't undo the choice.

**Stranger from GitHub (no vehicles, no tracks, no rig)**
- **They are told they own a 1999 Lexus IS200.** `VehicleSeeder.kt:15-33` seeds it every launch and the board reads `vehicles.firstOrNull()`, so the placard shows fabricated data — while the direction contract says "empty install: dashes on every face" and the code comment claims that behaviour.
- `Database Status: Connected` is a hardcoded literal, not a readout — on a panel whose thesis is "every element is a real readout or it does not ship."
- `App Version: 1.0.4-PRO` disagrees with Gradle's `1.1`; they'll file issues against the wrong version.
- Settings offers "Theme: Dark / Light" for a world that is one blackout face at two luminances. Default is Light → `#000000`.
- Five destinations have two names each (drawer vs board): ESP Connection/Rig, Settings/Setup, Track Sessions/Track records. They learn the app twice.
- Delete the seeded Lexus and the selector shows amber text with no action; the drag screen opens an empty popup.

## Minor Observations

- **206 hardcoded UI strings** across 22 files; `strings.xml` has one entry. 0.5% externalised, on an app shipping publicly.
- **28 of 30 interactive sites pass no `Role`**; zero `semantics{}`/`stateDescription` repo-wide. TalkBack hears undifferentiated clickable text. One fix in `DashAction` covers many sites.
- **Touch targets under 48dp:** "Clear sectors" — destructive — at 22dp with no horizontal padding (`TrackScreen.kt:267`); "Change" at 22dp (`ESPConnectionTestScreen.kt:124`); heatmap chips at 26dp with 2dp spacing vs M3's 8dp (`LapDetailScreen.kt:210-214`).
- **One screen of 17 handles landscape.** No `-land` qualifier, no `WindowSizeClass`, no `configChanges`. `TrackVehicleSelectorScreen` is a non-scrolling Column ≈480dp tall — the Start button goes off-screen in landscape.
- **30 `String.format` calls with no `Locale`.** On a Hungarian phone — and the bundled tracks are Hungarian — `%+.3f` yields `+0,123`.
- `AWAITING GPS` is the wrong diagnosis: it fires on `gpsPoints.isEmpty()`, which is stored track geometry, not live position.
- Elapsed time rendered twice simultaneously on the drag HUD (`:142-146` and `:292-296`).
- `ScreenScaffold` is half-demolished: alpha is 1f but the KDoc still describes translucency and `ScrollEdgeFade` still paints a gradient, in a world whose rule is "never gradients."
- `ConfirmDeleteDialog` is a stock Material `AlertDialog` — the only rounded surface left in a zero-radius world.
- Material's light scheme leaks into Day mode: `surfaceContainer` and friends are unset, so M3 dropdowns may render near-white on `#000000`.
- `UserProfileView.kt` is entirely dead — empty class, empty composable, zero references, not in the manifest.
- Analysis screens hand-roll `DashGroup` from `SectionLabel` + `bgCard` + `HorizontalDivider`. Two parallel section systems.
- `ExpandableGroup` animates height but the content pops — the KDoc says the pop was the thing being fixed.
- **DESIGN.md is still absent.** The direction contract's FINISH line is undischarged; there is no recorded system to check work against.

## Questions to Consider

1. If the driver reads exactly one number, why does the drag HUD ship nine tiles and a chart, and why does the track HUD's map default to *on* at a 24sp cost to the delta?
2. What does the driver see in the five seconds after finishing a stint? Right now: nothing. That is the emotional peak of the product and the one moment the app is silent.
3. `Trend` exists and nothing calls it. Was the six-pack raise wrong, or unfinished? A design system with a dead law teaches the next contributor that laws are optional.
4. The colour law reserves red for genuine fault, but recording is not a fault. What is the dash's colour for "this is live and being written down"? Real hardware answers with a lit block, not a hue — and that same answer would fix `NO SIGNAL` being dimmer than `LIVE`.
