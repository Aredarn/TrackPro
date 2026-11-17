# TrackPro (Android)

This app has a web twin, TrackPro-WEB, normally checked out next to this repo at
`../TrackPro-WEB`. The two are kept doing the same things; `PARITY.md` has the rules and the
feature table.

- When a change touches timing, track geometry, drag or comparison maths, the TrackBoard payloads,
  the sync rules or user-visible text, port it to TrackPro-WEB in the same piece of work, or say
  clearly why not and record it in `PARITY.md`.
- Behaviour both apps share gets a case in `shared/fixtures/`, run by `SharedFixturesTest`. Edit
  fixtures here, never in the web copy.
- Update the feature table in `PARITY.md` in the same commit as the feature.
