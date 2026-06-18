# Untidy

**Untidy is an unofficial Wear OS companion/player for TIDAL.** It is built for using TIDAL directly from a watch: search, browse, start playback, control the queue, and keep listening without a paired-phone UI doing the work.

<p>
  <img alt="Platform: Wear OS" src="https://img.shields.io/badge/platform-Wear%20OS-1DB954?style=flat-square">
  <img alt="Language: Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="UI: Jetpack Compose for Wear OS" src="https://img.shields.io/badge/Compose-Wear%20OS-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
  <img alt="Media: AndroidX Media3" src="https://img.shields.io/badge/media-AndroidX%20Media3-455A64?style=flat-square">
  <img alt="Service: TIDAL" src="https://img.shields.io/badge/TIDAL-unofficial%20client-000000?style=flat-square&logo=tidal&logoColor=white">
  <img alt="Status" src="https://img.shields.io/badge/status-known--good%20prerelease-F59F00?style=flat-square">
</p>

## Current snapshot

The current baseline is a **known-good physical-watch prerelease snapshot**:

- Tag/release: [`v0.1.0-known-good-watch-2026-06-18`](https://github.com/keylimesoda/Untidy/releases/tag/v0.1.0-known-good-watch-2026-06-18)
- Source commit: `4a874f6ee33de9b74e4ba7a5e198c98ae2b61a77`
- Prior validation tag on the same commit: `v0.1.0-watch-validation.9`
- Evidence: Ric used this build on a real Wear OS watch overnight and reported that it was great.

Treat this as a **prerelease / validation build**, not a production app-store release. It is the baseline for regression checks while release validation continues.

## Why this exists

TIDAL documents official support for [Apple Watch](https://support.tidal.com/hc/en-us/articles/360021018178-Apple-Watch), [Android TV](https://support.tidal.com/hc/en-us/articles/360001264517-Android-TV), TIDAL Connect devices, and Android/iOS phones and tablets. In the current public TIDAL support docs I could verify, I did **not** find an equivalent official Wear OS watch app/support page.

Untidy is aimed at that gap: a Wear OS-first, unofficial TIDAL player that treats the watch as the primary interface instead of a tiny remote for a phone.

## What works today

Untidy currently supports the core standalone-watch listening loop:

- **TIDAL sign-in** using device-code OAuth.
- **Home shortcuts** for Search, Recent, Downloads, Library, Discover, and Settings.
- **Search** for TIDAL content, with a watch-friendly first-entry prompt before opening the keyboard.
- **Library** browsing for saved albums, artists, tracks, and playlists.
- **Album, artist, and playlist pages** with playable track lists.
- **Playback from the watch** through a Media3-backed playback service.
- **Queue playback** for albums/playlists and a Queue surface from Now Playing actions.
- **Now Playing** with large transport controls, album art, progress, action sheet, and AOD/ambient-safe behavior.
- **Background/screen-off listening baseline** from the known-good physical-watch snapshot.
- **Recent shelf** for watch-local explicit play history: tracks, albums, and playlists the user chose to play.
- **Track context** from Recent, with actions such as Play, View album, View artist, Add to playlist, and Download where eligible.
- **Add current track to playlist** flow, subject to TIDAL account/API permissions.
- **View album / View artist** from Now Playing when route metadata is available.
- **Output labeling** tuned for the watch; the built-in speaker appears as `Watch speaker`.
- **Streaming quality presets** in Settings: Battery Saver, Balanced, and High, so users can choose between watch-friendly battery use and higher-quality TIDAL playback where the current backend supports it.
- **Settings** for account, playback quality/preferences, downloads management, and app/legal information.

Recent UX polish in the known-good line includes:

- Playback loading no longer flashes an empty default / tap-to-play state.
- Search entry now explains the transition before opening keyboard input.
- Built-in output copy is simplified to `Watch speaker`.

## Known limitations

Untidy is useful now, but it is still under active validation:

- **Not production-ready.** This repository is tracking prerelease/watch-validation builds, not a store-distributed release.
- **Real-watch QA is still open.** A known-good overnight use snapshot exists, but final release validation still needs structured coverage across watch models, Wear OS versions, input methods, Bluetooth routing, media controls, recents/back-stack behavior, and power/network edge cases.
- **Offline/downloads are narrow.** The app contains a Downloads shelf, local cleanup surfaces, and sanctioned `usage=DOWNLOAD` proof/MVP work, but release builds do not yet promise broad user-facing album/playlist offline downloads. Do not implement offline by caching `PLAYBACK` / `STREAM` manifests.
- **TIDAL API behavior varies by account/scopes.** Library, recommendations, playlist writes, quality tiers, and playback/download eligibility can depend on account region, subscription, scopes, catalog asset, and API availability.
- **Quality selection is present, but still being refined.** Settings exposes Battery Saver / Balanced / High. The current playback path requests conservative AAC-oriented stream qualities for watch reliability; true lossless/full-fidelity behavior should be validated separately before being promised as a release feature.
- **Unofficial client.** Untidy is independent from TIDAL, Google, and Samsung; users are responsible for complying with applicable terms.

## Install / download

For the current known-good build, use the GitHub release/tag:

- [`v0.1.0-known-good-watch-2026-06-18`](https://github.com/keylimesoda/Untidy/releases/tag/v0.1.0-known-good-watch-2026-06-18)

If a release APK asset is attached, sideload that APK to a Wear OS 4+ watch. If not, build the APK locally from the tagged source.

General sideload flow:

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

Untidy targets **Wear OS 4+ / API 33+** and declares itself as a standalone Wear app.

## Development setup

### Requirements

- Android Studio Ladybug or newer recommended.
- JDK 17.
- Android SDK platform API 35.
- Wear OS 4+ emulator image or a physical Wear OS watch.
- TIDAL credentials/scopes appropriate for the flows you are testing.

### TIDAL credentials

The app reads local credentials from `.dev-secrets\tidal-app.properties` at the repo root. This file is ignored and must not be committed.

```properties
# .dev-secrets\tidal-app.properties
tidal.clientid=YOUR_TIDAL_CLIENT_ID
tidal.clientsecret=YOUR_TIDAL_CLIENT_SECRET
tidal.redirecturi=YOUR_TIDAL_REDIRECT_URI
# Optional: override scopes when testing specific API surfaces
# tidal.scopes=user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback
```

If credentials are missing or invalid, the project may still compile, but sign-in/playback paths will fail at runtime.

### Build

```bash
# From the repo root
source scripts/env-android.sh
bash ./gradlew :app:assembleDebug --no-daemon
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK:

```bash
source scripts/env-android.sh
bash ./gradlew assembleRelease --no-daemon
```

```text
app/build/outputs/apk/release/app-release.apk
```

### Run on emulator

```bash
source scripts/env-android.sh
UNTIDY_EMULATOR_INSTALL_LAUNCH=1 scripts/ensure-wear-emulator.sh
```

Or open the project in Android Studio, select a Wear OS AVD, and run the `app` configuration.

### Run on a physical watch

1. Enable Developer Options and ADB debugging on the watch.
2. Pair the watch over Wi-Fi/ADB or USB where supported.
3. Build the desired variant.
4. Install with `adb install -r ...` or Android Studio device targeting.

Record the watch model, Wear OS version, APK variant, commit/tag, and any media/input/Bluetooth observations when reporting results.

## Validation commands

Useful lightweight gates before sharing a build:

```bash
source scripts/env-android.sh
bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
git diff --check
```

Narrower compile/test checks while iterating:

```bash
source scripts/env-android.sh
bash ./gradlew :app:compileDebugKotlin :core:playback:testDebugUnitTest --no-daemon
git diff --check
```

Release snapshot gate:

```bash
source scripts/env-android.sh
bash ./gradlew assembleRelease --no-daemon
sha256sum app/build/outputs/apk/release/app-release.apk
```

## Project layout

```text
Untidy/
├─ app/                  # Wear OS app, Compose UI, navigation, settings
│  └─ src/main/java/com/tidal/wear
│     ├─ ui/             # Home, search, library, album, artist, playlist, player, recent, downloads
│     ├─ playback/       # Now Playing state bridge
│     └─ recent/         # Watch-local Recent history
├─ core/
│  ├─ model/             # Shared domain models
│  ├─ auth/              # TIDAL auth wrapper
│  ├─ tidal-api/         # TIDAL API client
│  └─ playback/          # Media3 service, playback backend, queue/offline helpers
├─ docs/                 # Specs, release readiness, snapshots, work board
├─ reports/              # Local validation artifacts, not the product source of truth
└─ build.gradle.kts
```

## Roadmap / release status

Current release-track priorities:

1. Finish structured real-watch validation for the known-good line.
2. Finish the README/source-backed repo presentation pass.
3. Decide the beta scope for downloads/offline: keep as narrow proof/MVP surfaces or expand after more real-watch evidence.
4. Validate and, if appropriate, add a true full-fidelity/lossless quality tier beyond the current Battery Saver / Balanced / High presets.
5. Keep README, release checklist, and work board aligned with shipped behavior.
6. Only then promote from prerelease validation to a broader beta/release channel.

## Contributing

Helpful contributions include:

- Real Wear OS device reports with model, Wear OS version, APK/tag, reproduction steps, and logs when possible.
- Small-screen UX fixes: round safe areas, rotary/crown behavior, keyboard/input transitions, AOD legibility, and copy clarity.
- Playback/queue bug reports with the track/album/playlist path used and whether the watch was foregrounded, backgrounded, screen-off, on Wi-Fi, or on Bluetooth.
- Documentation fixes that keep public claims aligned with current validation evidence.

Please do not include private tokens, TIDAL credentials, license data, manifest URLs, or non-public account/library details in issues or pull requests.

## Legal and responsible use

Untidy is an **unofficial, independent** Wear OS client for the TIDAL music service. It is not affiliated with, endorsed by, or sponsored by TIDAL, Samsung, Google, or their subsidiaries. All trademarks belong to their respective owners.

Use your own TIDAL account and credentials. Untidy must not be used to bypass subscriptions, DRM, license checks, regional restrictions, or TIDAL API/SDK terms. Offline/download work must use sanctioned download/offline surfaces only; do not cache or redistribute streaming manifests or protected audio outside allowed APIs.

---

<sub>Built with Kotlin, Jetpack Compose for Wear OS, AndroidX Media3, and TIDAL auth/playback/API integrations.</sub>
