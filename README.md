# Gridline

An offline Android app for daily drawing practice: lines, ellipses, boxes in
perspective, and a plain sketch canvas. Built for the Wacom MovinkPad
(Android 14/15) and the Pro Pen 3, but works on any Android 10+ device with a pen.

No account, no network permission, no ads, no analytics. Everything stays on the device.

## Install on the tablet (no PC)

1. On the MovinkPad, open this repo in the browser and download
   [`release/gridline.apk`](release/gridline.apk) (tap the file, then "Download raw").
2. Open the downloaded file. Android will ask to allow installs from your browser:
   **Settings → Allow from this source**, then go back and tap **Install**.
3. Updating later: install the new APK over the old one. Your history and sketch stay
   (every build is signed with the same key, `keystore/gridline-sideload.jks`).

## What's in it

**Daily warm-up.** Lines → ellipses → 2-point boxes in one go (about 10 minutes on Normal).

**Lines.** Two target points. Connect them in one stroke. After each stroke you get:
- *Wobble*: shake around a smooth path (mm). Slow, careful strokes wobble.
- *Bow*: how much the line curves (mm). Usually the wrist or elbow pivoting.
- *Ends*: how far you missed the points. *Angle*: direction error.
- Stroke time, because speed and wobble are related.

The ideal line stays on screen in green next to your stroke until the next one.

**Ellipses.** A target ellipse shown as axes, a box, or a faint trace (tap *Guide*).
Draw it in one stroke; extra laps are fine. The app fits a real ellipse to your stroke
and reports shape error (evenness), size, ratio (roundness), tilt of the long axis,
center offset, and whether you closed the loop.

**Boxes · 2-point.** A horizon, two vanishing points (often off the screen, like in
real drawings), the front vertical edge and the start of both top edges. Draw the rest.
Press *Check* and:
- every edge you drew is extended across the page, colored by which vanishing
  point it belongs to, so you can see where it actually goes;
- a dashed green line shows where that edge should have gone (same start, same length);
- each edge gets its angle error in degrees (green < 1.5°, amber < 4°, red above);
- the summary says whether lines *converge too fast* (they'd hit the horizon before
  the VP), are *too parallel* (past it) or *point away*, which is the actual cause of
  boxes that look warped.

Guides: *Rays* (construction rays shown), *Horizon* (just the horizon and VP markers),
*Stubs only* (nothing but the given edges, for when you're ready).

**Boxes · 1-point.** Same idea with a given front face and one vanishing point.

**Boxes · free.** Draw any box with nothing given. Edges are grouped by direction and
each group is checked against its own best-fit vanishing point, so it tells you whether
your box is consistent with itself. Needs 3+ lines per direction (any two lines meet
somewhere, so two can't be wrong).

**Sketch.** Pressure-sensitive pen, eraser, 5 colors (including a light grey for
construction lines), size slider, 150 steps of undo/redo, pinch to zoom and drag to
pan with fingers, 1-point and 2-point perspective guides with draggable vanishing
points, and Save PNG to `Pictures/Gridline`. The page autosaves when you leave.

**History.** Every finished session is logged with its averages. Trend lines show
the main number for each drill over your last 20 sessions. The home screen shows
today's sessions, minutes, and how many days in a row you've practised.

Drills never smooth, straighten or snap your strokes. What you draw is what gets scored.

## Pen

| | Sketch | Drills |
|---|---|---|
| Pen tip | draw | draw |
| First side button (usually nearer the tip) | hold while drawing = erase | undo / discard last |
| Second side button | press = undo | Check / Next box |
| Fingers | pan, pinch zoom, drag VP handles | ignored |

- *Settings → Swap pen side buttons* if they feel backwards.
- If a side button opens a Wacom or system menu instead, the tablet has an action
  assigned to it. Set that button back to its default in the tablet's pen settings
  (Wacom Center, or Settings → pen). Android only passes the button to apps when
  nothing else has claimed it. I couldn't test this on a real MovinkPad, so treat the
  button mapping as the least certain part of the app.
- The third button on the Pro Pen 3 isn't used.
- *Pen only* (on by default) is the palm rejection: only the pen draws. Turn it off
  if you want to draw with a finger.
- *Ink prediction* uses Android 14's motion predictor to draw a short estimated tail
  ahead of the pen to hide lag. It's display-only and never scored.

## Build it yourself

Requirements: JDK 17+, Android SDK with platform 35.

```sh
./gradlew test assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The project has no third-party dependencies, just the Android framework and Kotlin's
standard library. JUnit is only used for the geometry tests.

## Code map

```
app/src/main/java/com/gridline/app/
  geom/Geometry.kt       line fit, wobble/bow split, ellipse fit (Fitzgibbon / Halir-Flusser),
                         least-squares vanishing point, direction clustering
  ink/                   stroke storage, pressure rendering, motion prediction
  drill/                 LineDrill, EllipseDrill, BoxDrill, DrillView (raw pen input)
  sketch/                SketchView (canvas, undo, zoom), PerspectiveGuide
  ui/                    history log, prefs, small view helpers
  MainActivity, DrillActivity, SketchActivity, HistoryActivity
```

## Known limits

- Rotating the screen mid-drill starts a new target (finished reps are kept).
- Sketch undo history is lost when you leave the sketch screen; the page itself is kept.
- Measurements in mm come from the screen DPI the tablet reports, so expect them to be
  close, not lab-exact. They're consistent from day to day, which is what matters for
  tracking progress.
