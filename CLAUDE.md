# CR Elixir Reader

Android overlay app for Clash Royale that automatically detects opponent's card plays
via screen capture and tracks their elixir in real time.

## Architecture

- **CaptureService** — foreground service with MediaProjection for screen capture
- **OverlayService** — AccessibilityService, shows/hides overlay when CR is active
- **ScreenAnalyzer** — processes captured frames, detects card deployments
- **CardMatcher** — matches detected card images against reference fingerprints
- **Tracker** — pure-Java elixir model (regen, multiplier, pump, cycle)
- **OverlayController** — builds and drives the floating overlay UI

## Code Style

- Java 8 source/target, no Gradle — built with aapt2 + javac + d8
- Package: `com.runetpisun.careelixir`
- Max line length: 120 chars
- No wildcard imports; group: java, android, org, com
- Classes are `final` unless designed for inheritance
- Fields at top, constructors, public methods, private methods
- No comments unless explaining a non-obvious "why"
- Constants: `UPPER_SNAKE_CASE`, fields: `camelCase`
- Pure-Java classes (Tracker, Card, Stats) have no Android deps for JVM testing
- UI is built in code (no XML layouts) to keep the APK minimal
- All user-facing strings in Ukrainian (`res/values/strings.xml`)

## Build

```bash
./tools/build.sh
```

Requires: JDK 21, aapt2, d8/dx, zipalign, apksigner, android.jar (API 34).

## Device Target

Samsung Galaxy A52 — 1080×2400, Android 12+.
