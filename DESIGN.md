---
name: TrackPro
description: A blackout race dash — luminous markings on an unlit panel, read at a glance through a windscreen.
colors:
  panel: "#0A0C0B"
  field: "#14171A"
  field-live: "#1E2225"
  session-best: "#B473E8"
  on-session-best: "#150E1C"
  marking: "#F2F4F3"
  marking-dim: "#9AA1A4"
  marking-faint: "#5E6669"
  personal-best: "#3FD07A"
  down-on-time: "#E8B33A"
  bezel: "#2A2F31"
  fault: "#E5453A"
  day-panel: "#000000"
  day-field: "#0B0E10"
  day-field-live: "#16191C"
  day-session-best: "#D9A6FF"
  day-marking: "#FFFFFF"
  day-marking-dim: "#C2C8CB"
  day-marking-faint: "#7C8488"
  day-personal-best: "#4FF08C"
  day-down-on-time: "#FFC64A"
  day-bezel: "#333A3D"
  day-fault: "#FF5A47"
typography:
  display-numeric:
    fontFamily: "platform sans-serif (intended: condensed grotesque with hard-edged lining numerals)"
    fontSize: "56sp"
    fontWeight: 900
    lineHeight: "58sp"
    letterSpacing: "-2.2sp"
    fontFeature: "tnum, lnum"
  stat-value:
    fontFamily: "platform sans-serif"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: "24sp"
    letterSpacing: "-0.4sp"
    fontFeature: "tnum, lnum"
  title-large:
    fontFamily: "platform sans-serif"
    fontSize: "16sp"
    fontWeight: 700
    lineHeight: "20sp"
    letterSpacing: "0.3sp"
    fontFeature: "tnum, lnum"
  title-medium:
    fontFamily: "platform sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
    letterSpacing: "0.2sp"
    fontFeature: "tnum, lnum"
  label:
    fontFamily: "platform sans-serif"
    fontSize: "10sp"
    fontWeight: 500
    lineHeight: "14sp"
    letterSpacing: "1.4sp"
    fontFeature: "tnum, lnum"
  body:
    fontFamily: "platform sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "19sp"
    letterSpacing: "0sp"
    fontFeature: "tnum, lnum"
rounded:
  none: "0dp"
  cap: "2dp"
  lamp: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  instrument:
    backgroundColor: "{colors.field}"
    textColor: "{colors.marking}"
    typography: "{typography.stat-value}"
    rounded: "{rounded.none}"
    padding: "10dp 12dp"
  readout:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.marking}"
    typography: "{typography.display-numeric}"
    rounded: "{rounded.none}"
  dash-action:
    backgroundColor: "{colors.field-live}"
    textColor: "{colors.marking}"
    typography: "{typography.display-numeric}"
    rounded: "{rounded.none}"
    padding: "16dp 16dp"
    height: "72dp"
  dash-action-compact:
    backgroundColor: "{colors.field-live}"
    textColor: "{colors.marking}"
    typography: "{typography.title-large}"
    rounded: "{rounded.none}"
    padding: "10dp 16dp"
    height: "48dp"
  dash-action-disabled:
    backgroundColor: "{colors.field}"
    textColor: "{colors.marking-dim}"
    rounded: "{rounded.none}"
  button-primary:
    backgroundColor: "{colors.session-best}"
    textColor: "{colors.on-session-best}"
    typography: "{typography.title-large}"
    rounded: "{rounded.none}"
    padding: "12dp 16dp"
  button-primary-disabled:
    backgroundColor: "{colors.field-live}"
    textColor: "{colors.marking-dim}"
    rounded: "{rounded.none}"
    padding: "12dp 16dp"
  toggle-chip:
    backgroundColor: "{colors.field-live}"
    textColor: "{colors.marking}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.none}"
    padding: "8dp 12dp"
  toggle-chip-selected:
    backgroundColor: "{colors.session-best}"
    textColor: "{colors.on-session-best}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.none}"
    padding: "8dp 12dp"
  field-container:
    backgroundColor: "{colors.field}"
    textColor: "{colors.marking}"
    rounded: "{rounded.none}"
    padding: "{spacing.md}"
  void-stamp:
    backgroundColor: "transparent"
    textColor: "{colors.fault}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "1dp 5dp"
  top-bar:
    backgroundColor: "{colors.field}"
    textColor: "{colors.marking}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    height: "48dp"
---

# Design System: TrackPro

> Derived from source only. No finish-review screenshots exist for this build; nothing in
> this file has been visually verified on a device. Every value below is cited to the
> shipped Kotlin, and where a code comment disagrees with the code, the code is recorded.

## Overview

**Creative North Star: "The Blackout Dash"**

TrackPro's surfaces are a purpose-built instrument panel in the AiM / MoTeC / Racelogic
lineage: an unlit ground that says nothing until it has something to say, luminous
markings rather than coloured decoration, and numerals that are the largest thing in any
frame. The reference is real racing hardware plus motorsport print and livery. It is
explicitly not a sim-racing skin and not a premium consumer fitness app; fake carbon,
elevated cards, and glossy hero imagery are the two failure modes this world sits between
(`theme/TrackProColors.kt:3-23`).

The panel has no cards. Containment comes from two things only: a field ground one step
lighter than the panel, and a 1dp milled bezel hairline (`components/Dash.kt:227-235`,
`components/AppCard.kt:18-39`). Nothing carries a radius, nothing casts a shadow, nothing
floats. Density is high on parked surfaces and brutally sparse on the two driving
surfaces, where the panel collapses to one number plus a segmented bar.

The world has one accent, not a palette of brand colours: purple is session best because
that is the sport's own timing convention, and the Material bridge fills
primary/secondary/tertiary with the same value rather than inventing two more
(`extrasForUI/TrackProTheme.kt:36-42`). Severity colour is borrowed from timing gear, not
designed: green personal best, amber down on your own time, red genuine fault only.

**Key Characteristics:**

- Unlit blackout ground; markings are luminous, never decorative.
- Zero radius everywhere; square is a law with two named exceptions.
- No shadows, no elevation, no translucency — depth is ground steps plus hairlines.
- One segmented bar language; no gradients, no smooth fills.
- Tabular numerals at every size, tracking scaled with size.
- Two luminances of one face (Night / Day), not a light/dark pair.

## Colors

A three-step greyscale ground carrying four functional signal colours, all inherited from
timing hardware convention rather than chosen for brand.

### Primary

- **Session Purple** (`{colors.session-best}`): the single accent. It means *session best*
  on a timing surface (`TimeAttackScreen.kt:448`, `:1008`, `:1025`) and, off the timing
  surfaces, marks the one promoted control on a screen — the lit block's edge and fill
  (`components/Dash.kt:308-317`, `components/PrimaryButton.kt:51-54`) and the section
  placard's 18×2dp tick (`components/SectionLabel.kt:33-38`). It is never a background
  wash and never a full-bleed bar (`components/AppTopBar.kt:76-80` renders the section
  accent as a 6dp dot).
- **Knockout** (`{colors.on-session-best}`): near-black ink used only inside an accent
  fill.

### Secondary

Severity, not decoration. These three carry the timing convention and are never used for
emphasis.

- **Gain Green** (`{colors.personal-best}`): personal best, up on your time, link healthy,
  session saved (`TimeAttackScreen.kt:368`, `DragScreen.kt:206`,
  `components/SessionSummary.kt:58-62`).
- **Down Amber** (`{colors.down-on-time}`): down on your own time, and read failure
  (`components/DataGate.kt:54-58`). Being slower is never red.
- **Fault Red** (`{colors.fault}`): genuine fault, destructive action, and the VOID stamp
  only (`components/VoidStamp.kt:25-32`, `components/ConfirmDeleteDialog.kt:38-48`).

### Neutral

- **Panel** (`{colors.panel}`): the unlit ground behind everything
  (`components/ScreenScaffold.kt:53-57`).
- **Field** (`{colors.field}`): an instrument field raised out of the panel; the ground of
  every Instrument, group body, top bar, and dialog.
- **Field Live** (`{colors.field-live}`): the field currently under the eye — the lit
  block's ground and an unselected chip's.
- **Marking** (`{colors.marking}`): luminous numerals and legends.
- **Marking Dim** (`{colors.marking-dim}`): a marking that is present but not being read —
  every placard cap, every unit, every detail line.
- **Marking Faint** (`{colors.marking-faint}`): documented as large marks only. See the
  honest-limitations note; the build does not honour that constraint.
- **Bezel** (`{colors.bezel}`): the hairline between fields, the unlit segment, and the
  disabled control's edge. Note that one token carries all three roles by design —
  `sectorLine` is aliased to both `bezel` and `segmentOff` (`theme/TrackProColors.kt:70-76`).

### Named Rules

**The Two Luminances Rule.** Night and Day are one face at two brightnesses, not a
light/dark pair. Day drops the ground to true black and drives every marking harder,
because against direct glare the limiting factor is marking-to-ground difference, not
page brightness (`theme/TrackProColors.kt:14-21`, `:98-124`). Never author a light surface:
if you find yourself picking a dark foreground on a pale ground, you have left the world.

**The One Accent Rule.** The palette has exactly one accent. Material needs three slots
filled and all three take the same value (`extrasForUI/TrackProTheme.kt:36-42`). A new
brand colour is not a design decision available to a new screen.

**The Convention Rule.** Severity colour is the sport's, not ours: purple session best,
green personal best, amber down on your own time, red genuine fault. Red never means
"slower" (`theme/TrackProColors.kt:24-26`).

**The Fault Is Never Dimmer Rule.** A fault state is drawn at least as bright as its
healthy state. `NO SIGNAL` takes `deltaBad` minimum, never `textFaint`
(`DragScreen.kt:203-207` implements this correctly).

## Typography

**Display Font:** platform sans-serif — intended to be a condensed grotesque with
hard-edged, high-x-height numerals where a 9 and an 8 can never be confused at a glance.
No face is bundled; `TrackProFontFamily` resolves to `FontFamily.SansSerif`
(`theme/Type.kt:10-18`).
**Body / Label Font:** the same family. The system has one face and six roles.

**Character:** top-heavy to the point of severity. One readout dominates every frame and
everything beneath it is a placard. There is no display tier for prose, because a dash has
no prose (`theme/Type.kt:50-54`).

### Hierarchy

- **Display Numeric** (Black 900, 56sp, 1.04 leading, −2.2sp tracking): the dominant
  readout — live delta, the split just crossed, the running clock. Also the label face of
  a full-size lit block at 34sp (`components/Dash.kt:328`).
- **Stat Value** (Bold 700, 20sp): a secondary instrument value — best, last, split, speed,
  count. The default Instrument value size.
- **Title Large** (Bold 700, 16sp, +0.3sp): a field's own name, a car on its placard, and
  the label of a compact control at 15sp.
- **Title Medium** (Medium 500, 13sp, +0.2sp): a row label; the empty-state headline
  (`components/EmptyState.kt:26-32`).
- **Label** (Medium 500, 10sp, +1.4sp, always uppercased at the call site): the placard cap
  under every instrument, every section title, every status word.
- **Body** (Regular 400, 13sp): running text, where any survives.

### Named Rules

**The Tabular Rule.** Every type token carries `tnum, lnum` (`theme/Type.kt:27`). Without
fixed advances a running lap timer physically shifts left and right as digits change — the
number dances while the driver is reading it. Tabular figures are enforced at the token,
so no call site can opt out by accident.

**The atSize Rule.** Never resize a style with `copy(fontSize = …)`. Use
`TextStyle.atSize()`, which scales leading *and tracking* by the same ratio
(`theme/Type.kt:38-48`). Display Numeric's −2.2sp is correct at 56sp; carried unchanged to
15sp it is roughly −15% per character and glyphs physically overlap.

**The Value Leads Rule.** A number is never centred under a heading. The value is read
first and identified second, so the readout leads and its placard caption follows beneath
it, uppercased and dim (`components/Dash.kt:237-280`).

**The Placard Rule.** All caps for labels, captions, section titles, status words, and
control labels — uppercasing happens at the call site, not in the token. The type scale
is reserved for readouts; a section never announces itself with a larger heading
(`components/SectionLabel.kt:18-25`).

## Layout

Spacing is a five-step scale (`theme/Spacing.kt:6-12`): 4 / 8 / 12 / 16 / 24dp. Screen
gutters are 16dp, field interiors 12–14dp, and instrument padding is 12dp horizontal /
10dp vertical (`components/Dash.kt:199`).

Fields are **full bleed**. A milled panel does not inset its apertures from the edge, so
groups run edge to edge and are closed top and bottom by bezels rather than floated in a
margin (`components/Dash.kt:361-393`). Vertical rhythm on a panel comes from the bezel
hairline, not from whitespace: rows in a summary are separated by 1dp lines, not by gaps
(`components/SessionSummary.kt:75-81`).

The shared chrome is a 48dp top bar (`components/AppTopBar.kt:30`) and the scaffold insets
content by exactly that (`components/ScreenScaffold.kt:58`). Back is a full 48dp target
because it is the primary nav control on nearly every screen
(`components/AppTopBar.kt:58-73`). Interactive targets meet 48dp minimum; a full lit block
is 72dp tall, a compact one 48dp (`components/Dash.kt:318`).

Orientation: the two driving surfaces are the only ones with a real landscape branch —
`LocalConfiguration` appears in exactly two files in the app, `TimeAttackScreen.kt` and
`DragScreen.kt`. Parked surfaces are portrait-shaped and carry no at-speed legibility tax.

### Named Rules

**The Full-Bleed Rule.** Panel apertures touch the screen edge. Do not inset a group in a
margin to make it look like a card; the hairline is the boundary.

## Elevation & Depth

**This system has no shadows and no elevation.** There is no `shadow()` modifier, no
`Card`, no tonal surface elevation anywhere in the component layer. Depth is conveyed by
exactly two devices: a one-step ground change (panel → field → field-live) and a 1dp bezel
hairline (`components/Dash.kt:227-235`). Disabled states keep their block and drop the
edge to the bezel rather than fading out (`components/Dash.kt:309-310`,
`components/PrimaryButton.kt:52-54`).

Chrome is opaque. `TranslucentChromeAlpha` is `1f` (`components/ScreenScaffold.kt:107-110`)
— the name survives from the previous visual direction and the comment above it says so
explicitly. A milled panel does not show what is behind it, and a half-transparent bar over
live telemetry costs legibility for nothing. The scroll-edge fade
(`components/ScreenScaffold.kt:91-101`) is the one gradient in the system and, at alpha 1,
it now renders as an opaque-to-transparent scrim rather than the soft blur the KDoc at
`:23-39` still describes. **The KDoc describes the old direction; the constant is the
truth.**

### Named Rules

**The Flat Panel Rule.** No shadow, no elevation, no blur, no translucency. If a surface
needs to separate from what is under it, change its ground or draw a bezel.

## Shapes

**Square is a law.** `TrackProShapes.badge`, `.control`, and `.card` are all
`RoundedCornerShape(0.dp)` (`theme/Spacing.kt:21-25`). They are kept as
`RoundedCornerShape` rather than `RectangleShape` only so per-corner call sites still
compile. Apertures on a milled panel are cut, not moulded.

Two exceptions are deliberate and both are functional:

- **Status lamps** use `CircleShape` at 6dp (`components/AppTopBar.kt:76-80`,
  `MainActivity.kt:500`, `DragScreen.kt:599`). A lamp is a lamp.
- **Bar caps ≤2dp** (`TimeAttackListView.kt:179`, `TimeAttackListItem.kt:681`,
  `DraggableSheet.kt:209`). At 2dp a radius is an edge finish, not a corner.

Borders are always 1dp and always hard. The segment strip's gap is 2dp
(`components/Dash.kt:95`); the centre datum block is drawn at 0.6× the width of a normal
segment so it reads as a datum rather than a lit value (`components/Dash.kt:112`).

## Components

### Buttons

- **Lit Block (`DashAction`)** — the dash's only promoted control and its signature
  action. **Shape:** square (0dp). **Full size:** field-live ground, 1dp accent border,
  72dp min height, label in Display Numeric at 34sp uppercased, optional dim detail line,
  and a 4-segment strip on the trailing edge that lights when enabled
  (`components/Dash.kt:294-359`). **Compact:** 48dp, centred, Title Large at 15sp, no
  segment strip — "a numeral face shrunk to button size is a display face pretending to be
  a label" (`components/Dash.kt:326-328`). **Disabled:** the block stays, the border drops
  to the bezel, the strip unlights, the ink drops to marking-dim.
- **`PrimaryButton`** — a lit block at smaller scale: accent fill, accent 1dp edge,
  Title Large at 14sp uppercased in knockout ink, 16dp/12dp padding
  (`components/PrimaryButton.kt:41-64`). Not a pill; the legacy name survived a restyle.
- **Press feedback:** pointer-*down* scale, not a ripple. Rows take a 6% marking-tint
  highlight; controls scale to 0.96 (cards ~0.98) on a snappy spring, and the Material
  ripple is disabled because the scale *is* the feedback
  (`components/Interaction.kt:45-90`). Under reduced motion the transform becomes a brief
  opacity dip — non-vestibular.
- **Haptics are opt-in and default off.** Reserved for genuine commits: recording started,
  a sector marked, a track saved, a destructive confirm
  (`components/Dash.kt:305-306`, `components/ConfirmDeleteDialog.kt:39-42`).

### Chips

- **`ToggleChip`** — mode / unit / sector-count pickers. Square. Unselected: field-live
  ground, 1dp bezel border, marking ink. Selected: accent fill, transparent border,
  knockout ink, and a selection haptic on the same frame the fill flips
  (`components/PrimaryButton.kt:68-104`).

### Cards / Containers

There are no cards.

- **Field (`AppCard`)** — flat field ground, 1dp bezel border, 12dp padding, zero radius,
  zero elevation (`components/AppCard.kt:26-39`). The name was kept so existing call sites
  inherit the world without a rename sweep; do not read it as a card.
- **`DashGroup`** — a section placard on the panel ground, a bezel, the contents on a field
  ground at 14dp/12dp padding, a closing bezel. Full bleed
  (`components/Dash.kt:368-393`).
- **`SectionLabel`** — an 18×2dp accent tick, 6dp gap, then the caption in Label
  (`components/SectionLabel.kt:26-45`).

### Inputs / Fields

TrackPro has no bespoke text field. Stock Material 3 inputs are used and inherit the
palette through the Material bridge (`extrasForUI/TrackProTheme.kt:32-72`), which exists
precisely so unstyled Material components do not fall back to Material's default purple
light scheme.

### Navigation

- **`AppTopBar`** — 48dp, field ground, a 6dp accent lamp, the title in Label at 12sp
  uppercased, optional subtitle in Body at 11sp. The section accent appears only as the
  lamp, never as a full-bleed fill (`components/AppTopBar.kt:32-110`).
- **`ScreenScaffold`** — panel ground, full-bleed content inset by the bar height, opaque
  chrome, and a scroll-edge fade that renders only once content has actually scrolled
  underneath (`components/ScreenScaffold.kt:41-101`). An optional `header` slot holds a
  `SectionSwitch` under the bar; content is inset by it too.
- **`DashTabBar`** — the app's four places: Drive, History, Garage, Profile
  (`components/DashNavigation.kt`, `MainActivity.kt`). Each tab is an aperture split by
  vertical bezels; the current one is lit — field-live ground, marking ink, and a strip of
  four accent segments across its top edge. The others keep their strip unlit rather than
  omitted. Shown on the four tab roots only: every other screen is a step down with its own
  back, and the two HUDs never carry it.
- **`SectionSwitch`** — splits one tab into sections (Track | Drag, Cars | Tracks). Quieter
  than the tab bar on purpose: the selected section is lit field in marking ink with a
  two-segment accent underline, not a filled block, so "which tab" still outranks "which
  half". A tab keeps one title whichever section is showing.
- **`PhotoFrame`** — a car or driver photo in a square-cornered, bezel-edged aperture, from a
  local file. With no photo the frame is still drawn, with initials or a car mark in
  marking-dim, so a garage without photos is a set of empty frames, not a layout that jumps.
- **Placard counts** — bar trailing counts go through `countLabel()`: uppercased, singular
  at one ("1 CAR", "7 TRACKS").

### Signature Component: the segmented bar

`SegmentBar` (`components/Dash.kt:68-118`) is the system's one bar language and drives both
HUDs' delta and severity, the lit block's enable strip, and the loading state.

- **Discrete blocks only.** 21 segments by default, 2dp gaps, never a gradient and never a
  smooth fill. A driver counts blocks peripherally faster than they judge the length of a
  continuous bar; the quantisation is the feature.
- **Unlit segments are drawn, not omitted** (`:105-109`), so the bar's full range stays
  visible and a value near zero reads as *near zero* rather than *no data*.
- **Bidirectional bars fill outward from a fixed centre datum**, and the count is forced
  odd so a true centre block exists (`:86-88`).
- **The fill is spring-damped**, snapped only under reduced motion (`:78-84`).

### Signature Component: the trend mark

`Trend` / `rememberTrend` / `TrendMark` (`components/Dash.kt:120-177`). Every readout can
carry where the value is going, not just where it is. The mark is **drawn as geometry,
never a glyph or an icon font** — a filled triangle up/down, a 1.5dp bar for steady. It
carries hysteresis: the value must travel past a caller-set threshold from the last
committed reading before the direction is allowed to change, so a delta jittering by
milliseconds holds Steady.

### Signature Component: the data gate

`DataGate` + `LoadState` (`components/DataGate.kt:34-108`, `models/LoadState.kt:13-18`).
Four branches, never two sharing one: **failed** (a `READ FAILED` placard in amber, the
reason in plain language, an optional compact Retry block), **loading** (an uppercased
placard and an *unlit* segment bar — range visible, value unknown), **empty**
(`EmptyState`), **content**. Loading is deliberately a placard and not a spinner: a spinner
says something is happening, a placard says what.

### Signature Component: the VOID stamp

`VoidStamp` (`components/VoidStamp.kt:22-33`). A discarded run is stamped, not deleted:
outlined in fault red with the word VOID in Label, so it reads as something applied on top
of the row rather than as the row's own state colour. The session stays openable and keeps
its trace; it just stops counting. The discard decision lives on the post-session summary
(`components/SessionSummary.kt:27-38`), made once while the run is still in mind, with Keep
as the default and larger target.

## Do's and Don'ts

### Do

- **Do** draw every new value as a real readout: a placard cap in Label, uppercased and
  dim, over a value in the numeral face. `Instrument` and `Readout` are the containers.
- **Do** use `TextStyle.atSize()` for every resize, so tracking and leading scale with the
  size (`theme/Type.kt:38-48`).
- **Do** keep corners at 0dp. The only radii in the system are 6dp status lamps and ≤2dp
  bar caps.
- **Do** negate a lower-is-better value before handing it to `SegmentBar` — positive fills
  right and means better, always (`TimeAttackScreen.kt:375`).
- **Do** damp every live value and give every trend mark a hysteresis threshold. GPS is
  noisy and a snapping number is a lie about how confidently the value is known.
- **Do** gate every data surface through `DataGate` with a real `LoadState`. Four states,
  four branches.
- **Do** draw disabled states rather than hiding them: keep the block, drop the border to
  the bezel, unlight the strip.
- **Do** guard destructive-by-omission actions and land them on a summary
  (`components/SessionSummary.kt`), and prefer a reversible VOID stamp to a delete.
- **Do** honour reduced motion through `rememberReducedMotion()`
  (`components/Interaction.kt:33-43`) — swap springs for `snap()` and transforms for an
  opacity dip.
- **Do** reserve haptics for commits. Every-tap haptics train people to ignore haptics.
- **Do** draw indicators as geometry (`Canvas`, `Box`) rather than reaching for a glyph.

### Don't

- **Don't** add a shadow, an elevation, a blur, or a translucent chrome layer. Depth is a
  ground step plus a hairline.
- **Don't** add a radius to a surface, a button, or a chip. Square is the law; lamps and
  2dp caps are the only exceptions.
- **Don't** draw a continuous or gradient-filled bar for a value. Segments, always.
- **Don't** introduce a second accent or a decorative colour. The palette has one accent
  and three severity colours, all conventional.
- **Don't** use red for "slower". Red is genuine fault and destruction only; amber is down
  on your own time.
- **Don't** render a fault state dimmer than its healthy state.
- **Don't** use `marking-faint` below 14sp, or under an alpha. It is documented as large
  marks only and it does not clear 4.5:1 at any measured combination.
- **Don't** author a light surface. If a screen needs to be readable in sunlight, take it
  to Day — true black ground, markings driven harder.
- **Don't** build a new "card". If the answer to containment is a box with a radius and a
  shadow, the answer is wrong for this world.
- **Don't** resize a type token with `copy(fontSize = …)`.
- **Don't** put the display numeral face on a small control. Below ~20sp the numeral face
  is a display face pretending to be a label; use Title Large.

## How to extend this

Add to the world, not beside it.

1. **Start from the components, not the tokens.** If you are writing a new value on a
   screen, the answer is `Instrument`, `Readout`, `StatCell`, or `DashGroup`. Reach for a
   raw `Box` + `Text` only when none of those fits, and then say in a comment why.
2. **Read colour through the world-native aliases** — `panel`, `field`, `fieldLive`,
   `marking`, `markingDim`, `bezel`, `segmentOff` (`theme/TrackProColors.kt:55-80`) — not
   the legacy field names (`bgDeep`, `bgCard`, `textPrimary`). The legacy names still
   resolve so old call sites compile; new code should not add to them.
3. **A new token must earn its place twice.** Used once, it is a one-off, not a system
   entry. Add it to `theme/` only when a second surface needs it, and add it to both Night
   and Day in the same commit.
4. **New behaviour inherits the laws above.** Damped, hysteretic, four-state-gated,
   reduced-motion-aware, 48dp minimum.
5. **Legacy names are restyled, not deprecated.** `AppCard`, `SectionLabel`,
   `PrimaryButton`, `StatCell`, `ScreenScaffold` all kept their names through the world
   change. Read the file before assuming the name describes the thing.
6. **If you bundle the font, that is one line.** `TrackProFontFamily`
   (`theme/Type.kt:18`) plus a file in `res/font/`. Every token flows from it.

## Honest limitations

These are defects the build carries. They are recorded here so nobody inherits them as
house style.

- **No bundled typeface.** The world's face is a condensed grotesque with hard-edged
  numerals; the build ships the platform sans (`theme/Type.kt:18`). Everything in
  Typography is correct in structure and provisional in character.
- **`marking-faint` (`textFaint`) is used below its own documented constraint** at 23 call
  sites across the app, at 10–13sp. Worst measured case in the critique composited to
  2.21:1. The rule above is the rule; the build violates it.
- **Four of six contrast claims in `theme/TrackProColors.kt`'s KDoc are wrong**
  (`:86` claims 16.9:1, measures 17.76; `:88` claims 3.2:1, measures 3.35; `:117`'s 4.8:1
  is not produced against any ground in the system). Treat the numbers in that file as
  unverified; the hexes are authoritative, the annotations are not.
- **The fault-is-never-dimmer rule is not universally applied.** `DragScreen.kt:203-207`
  implements it; `TimeAttackScreen.kt:697-699` still renders `NO SIGNAL` in `textFaint`
  against `LIVE` in `deltaGood`, and `MainActivity.kt:702-705` renders `NO LINK` in
  `markingDim`. Two open violations of a recorded law.
- **Settings still labels the two luminances "Dark" and "Light"**
  (`screens/SettingsScreen.kt:110`). The system ships Night and Day, one face at two
  luminances; the user-facing control names a light/dark pair that does not exist.
- **`DataVizColors.kt`'s header comment is stale.** It says the values are "sourced from
  the same 5-color palette" (#16262E / #2E4756 / #3C7A89 / #9FA2B2 / #FEEA00) and mirror
  the dark palette, but `seriesPrimary`, `chartLine` and `seriesCompare`
  (`theme/DataVizColors.kt:23-29`) are the dash accent and amber. The map, chart, and gauge
  layers are a **second, teal/yellow palette** that did not follow the world change; the
  code is the truth and the comment describes an earlier state.
- **No internationalisation.** `res/values/strings.xml` holds one entry; every UI string in
  this document's components is a hardcoded Kotlin literal. Any type rule here is untested
  against a longer language.
- **Accessibility semantics are thin.** `Role` is set at two call sites in the whole app
  (`components/PrimaryButton.kt:49`, `:84`) and there is no `semantics {}` or
  `stateDescription` anywhere. Colour is the only state cue on several controls, including
  the delta-reference switch.
- **Landscape is not a system-wide behaviour.** Only the two HUDs branch on orientation;
  every other screen is portrait-shaped by default.
- **This document was written from source, not from a device.** No finish-review
  screenshots exist for this build.
