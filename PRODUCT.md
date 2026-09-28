# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Automotive enthusiasts, amateur racers, and track-day / karting drivers who want real
performance data from their own driving without buying a professional data logger.

The primary user is **the driver, mid-session, with the phone mounted in the car**. This is
the moment the app most has to get right: the screen is read in glances between corners,
often in direct sunlight, by someone whose attention belongs on the track.

Post-session analysis (session and lap detail, comparison, heatmaps) is a real and
important second context, but it is the calmer one. When the two conflict, the driving
moment wins.

## Product Purpose

Measure and analyse real driving performance — lap times, sector splits, drag runs,
acceleration and braking — from GPS, and keep a personal history of it against specific
vehicles and tracks.

Success is a driver finishing a track day with an accurate, complete record of every lap
they drove, and being able to see where the time actually went.

## Positioning

Professional timing gear (VBOX, AiM, Racelogic) costs more than most amateurs will spend on
a hobby. TrackPro's mechanism is that the timing hardware is a **DIY ESP32/ESP8266 plus a
GPS module** — parts costing a fraction of a commercial logger — talking to the phone over
Wi-Fi TCP or Bluetooth, with open firmware published alongside the app.

The app is open source (GPL-2.0) and the companion firmware is a separate public repo.
Nothing in the app depends on an account, subscription, or network. An optional, free
TrackBoard account (the author's own server, also open source) adds a driver profile, a
backup of the garage with car photos, and per-track leaderboards. Signing in is the consent
for the garage backup; posting laps to leaderboards is a separate, off-by-default switch.

## Operating Context

- **At the track or strip.** Phone mounted, engine running, driver possibly in a helmet and
  gloves. Sunlight, vibration, and no free attention.
- **Hardware pairing happens before the session,** not during: the ESP rig has to be
  powered, joined, and holding GPS lock before a run is useful. A dedicated
  connection-test screen exists for exactly this.
- **Sessions are recorded, then reviewed** — often at home, off the track, unhurried.
- **Tracks are either pre-loaded or user-built.** The track builder lets a driver record
  their own circuit geometry, start/finish and sector lines.
- **Network is not guaranteed.** Recording must work with no connectivity; only optional
  enrichment (weather, map tiles) needs it.

## Capabilities and Constraints

**Shipped capabilities**

- Circuit lap timing with live delta against the session best, plus sector/checkpoint
  splits.
- Sprint (point-to-point) timing.
- Drag timing — quarter mile, 0–100 km/h / 0–60 mph, and related metrics.
- Track builder: record and store custom track geometry, start/finish and sector lines.
- Vehicle garage: store vehicles with full spec and a photo, attribute sessions to them, and
  (signed in) back them up to the account and restore them on a new phone.
- Driver profile: a career sheet computed on the phone (laps, sessions, tracks, distance, a
  personal best per track, main car); signed in, it adds a name, photo, bio, and the
  leaderboard place next to each best. Export and account deletion live here too.
- Session and lap analysis: lap breakdown, lap-vs-lap comparison, speed heatmap traces on a
  map, theoretical best from best sectors.
- Automatic weather and track-conditions capture per session (Open-Meteo).
- Three interchangeable GPS sources: ESP32 over Wi-Fi TCP, ESP32 over Bluetooth Classic,
  and the phone's own GPS.
- Metric/imperial toggle; dark and light themes.

**Confirmed constraints**

- **Both GPS paths are first-class.** Real usage is genuinely split between the ESP rig and
  phone GPS, so nothing may assume ESP-grade sample rate or precision. Features must stay
  honest at roughly 1 Hz phone GPS while still exploiting high-rate external GPS when it is
  present.
- **Distribution is public GitHub releases.** Strangers install this with no support from
  the author, so first-run guidance, hardware-setup recovery, and failure states are
  product requirements, not polish.
- Android only. Native Jetpack Compose; minSdk 26, targetSdk 34 (deliberately behind
  compileSdk 35, not yet verified against Android 15 behaviour changes).
- All data is local (Room). There is no account system, backend, or sync.
- Licensed GPL-2.0; the firmware lives in a separate repo and the two version together.
- Speeds are stored canonically in km/h regardless of the display unit.
- Map tiles and track data require OpenStreetMap attribution.
- Open-Meteo's free tier is non-commercial; commercial distribution would need revisiting.

**Explicitly undecided / known gaps**

- `applicationId` is still `com.example.trackpro`. Fine for sideloaded GitHub releases; it
  would block Google Play publication if that is ever wanted.
- Version strings disagree: Gradle declares `1.1`, the Settings screen hardcodes
  `1.0.4-PRO`. Which is authoritative is unresolved.
- No internationalisation: `strings.xml` holds one entry (the app name) and all UI copy is
  hardcoded Kotlin literals.
- `UserProfileView.kt` exists but is wired into no navigation route; whether user profiles
  are a real direction is undecided.
- README lists live sharing, an online leaderboard, and data-to-video as future plans.
  These are aspirations, not commitments — no backend work exists. (Checkpoint/sprint
  timing, also once listed as future, has since shipped.)

## Brand Commitments

- Name: **TrackPro**. App repo `github.com/Aredarn/trackpro`; firmware
  `github.com/Aredarn/TrackPro_ESP`.
- Open-source and hardware-hackable is part of the identity, not just the licence.
- OpenStreetMap contributor attribution is a standing obligation wherever map or track data
  appears.

## Evidence on Hand

- **Seven real circuits ship with the app** (`app/src/main/res/raw/tracks.json`):
  Pannonia-ring, Nordschleife (BTG), Euro-ring, Hungaroring, Mugello, Circuit Paul Ricard
  (GP layout), and Kakucs ring including a reverse layout.
- Real companion hardware and firmware, publicly published.
- README screenshots of the main menu and GPS connection test, hosted on GitHub.
- A GitHub release-downloads badge exists, but actual download numbers, user counts,
  reviews, and testimonials are **not** available — future work must not invent them.
- No benchmark data comparing TrackPro's accuracy against commercial loggers exists. Any
  accuracy claim would have to be measured first.

## Product Principles

1. **The driver reads this at speed.** On session surfaces, glanceability, contrast in
   sunlight, and target size outrank density, detail, and cleverness.
2. **A stranger has to succeed alone.** Anything that can fail during hardware setup or a
   first run needs a recoverable path, because there is nobody to ask.
3. **Never assume the good GPS.** Every feature must degrade honestly to phone-grade sample
   rates rather than silently producing confident, wrong numbers.
4. **Recording outranks enrichment.** A session must record completely with no network, no
   weather, and no map tiles. Optional data fails quietly and never blocks capture.
5. **Don't present derived numbers as measured.** Theoretical bests, predictions, and
   interpolations must be labelled as what they are, including how much data backs them.

## Accessibility & Inclusion

The primary usage scene sets a real, non-optional bar: a mounted phone read in glances, in
direct sunlight, by a driver who may be wearing gloves.

- High contrast against daylight glare is a functional requirement on session surfaces, not
  an aesthetic preference.
- Interactive targets must meet the 48dp minimum; anything the driver touches mid-session
  should exceed it.
- Reduced motion is honoured via the system animator duration scale.
- No specific assistive-technology need (screen reader, colour vision) has been established
  with real users yet. Note that the speed heatmap deliberately uses a green-to-red ramp —
  the convention for the category, but the one axis red-green colour-blind users cannot
  separate. If that audience is ever confirmed, the ramp needs revisiting.
