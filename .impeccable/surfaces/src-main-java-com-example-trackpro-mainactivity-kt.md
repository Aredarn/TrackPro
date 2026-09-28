---
version: 1
slug: "src-main-java-com-example-trackpro-mainactivity-kt"
primary_target: "app/src/main/java/com/example/trackpro/MainActivity.kt"
related_targets: ["app/src/main/java/com/example/trackpro/screens/telemetricScreens/TimeAttackScreen.kt","app/src/main/java/com/example/trackpro/screens/telemetricScreens/DragScreen.kt","app/src/main/java/com/example/trackpro/theme/TrackProColors.kt"]
---

Scope: TrackPro status board (MainActivity `MainScreen`), the track HUD
(`TimeAttackScreen`) and the drag HUD (`DragScreen`). Visitor mode: Operate.

Driving surfaces are drag and track ONLY. Everything else (board, lists, analysis,
garage, settings) is read parked and carries no at-speed legibility tax — the previous
build applied that constraint everywhere and came out dull.

Mounting: both orientations first-class. Ambient: daylight and dusk, so luminance adapts
on one face rather than shipping a second design.

## Direction contract

THESIS: A blackout race dash, not an app wearing a dash theme. Refuses the elevated-card
dashboard and the fake-carbon racing skin equally; every element is a real readout or it
does not ship.

OWN-WORLD: Blackout ground #0A0C0B, panel #14171A, bezel hairline #2A2F31, luminous
numerals #F2F4F3. One segmented-bar language everywhere — discrete blocks, never
gradients. Green #3FD07A / amber #E8B33A / red #E5453A carry severity; purple #B473E8 is
session best, per timing convention. Numerals are hard-edged and tabular and are the
largest thing in every frame. Day mode raises luminance on the same face; it is not a
light theme.

STORY: A driver sees a purpose-built instrument at rest, learns what the rig and the last
session did, and reaches the mode they came for. On track the panel collapses to one
number.

FIRST VIEWPORT: Segmented link bar across the top edge. Beneath it the car as an entry
placard — photo left, make/model/power right. Then last session as one wide readout row,
then four corner fields. Two full-width segmented mode entries pinned at the bottom,
thumb-reachable. Empty install: identical panel, dashes on every face.

FORM: AiM/MoTeC blackout dash; candidate 1 of 7 on my grounded list, taken as
IMPECCABLE'S PICK over the roll's assignment and over the winning challenger; seed
72de238c, reroll 1.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review,
the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Raises

- From the ticket wallet (competitive): a discarded session stamps VOID on the face rather
  than vanishing.
- From labanotation (declined): sector marks are sized by the duration they took, not
  labelled with it.
- From the instrument six-pack (won its weighing): every readout carries trend as well as
  value, and every value is damped — GPS is noisy and a snapping number is a lie.

## Signature interaction

The segmented delta bar. Blocks fill outward from a fixed centre datum, damped, never
snapped; the bar is the peripheral read and the numeral is the deliberate one. Same
component drives the track HUD's delta and the drag HUD's split severity. Code-led, so the
finish reviewer audits this in behavior.

## Per-surface

- Track HUD: delta to best is the largest element.
- Drag HUD: the split just crossed, held large until the next one lands.
- Board: last session + rig status are the only data that earned a place.

## Unresolved

- Vehicle photo is a new feature: nullable column, migration 5->6, photo picker. Board
  falls back to a typographic placard when absent.
