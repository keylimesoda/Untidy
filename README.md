# Untidy

**A standalone TIDAL client for Wear OS.**
Browse, search, and play your TIDAL library straight from your watch — no phone tether required.

<p>
  <img alt="Platform: Wear OS" src="https://img.shields.io/badge/platform-Wear%20OS-1DB954?style=flat-square">
  <img alt="Language: Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="UI: Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Wear-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
  <img alt="Service: TIDAL" src="https://img.shields.io/badge/TIDAL-unofficial%20client-000000?style=flat-square&logo=tidal&logoColor=white">
  <img alt="AOD-safe" src="https://img.shields.io/badge/AOD-safe-2E7D32?style=flat-square">
  <img alt="Min SDK 33" src="https://img.shields.io/badge/minSdk-33%20(Wear%20OS%204)-455A64?style=flat-square&logo=android&logoColor=white">
  <img alt="Status" src="https://img.shields.io/badge/status-public%20beta-F59F00?style=flat-square">
  <img alt="QA" src="https://img.shields.io/badge/QA-emulator%20tested-9E9E9E?style=flat-square">
</p>

---

## Why Untidy?

The first-party TIDAL experience on the wrist is limited, and most "watch" listening still depends on a paired phone doing the real work. **Untidy** is built the other way around: a standalone Wear OS app that signs in directly with your TIDAL account and streams from the watch, with a UI designed for a small round display, a rotating bezel/crown, and an Always-On Display.

It is opinionated about the things that matter on a watch:

- **Battery-aware playback defaults** so a workout doesn't cost you a charge.
- **Low-clutter Wear UX** — large targets, short lists, no desktop-grade chrome.
- **AOD-safe Now Playing** that stays readable in ambient mode without burning pixels.
- **Honest empty states** when TIDAL's API can't surface something, instead of fake content.

## Features

Implemented and working in the emulator today:

- 🔐 **Device-code TIDAL sign-in** — pair the watch by entering a short code from your phone or PC, no in-app keyboard required.
- 🔎 **Search** across tracks, albums, artists, and playlists.
- 🧭 **Discover / For You** surfaces, with clear "unavailable" states where API permissions don't grant access.
- 📚 **Library** — your saved albums, artists, tracks, and playlists.
- 🎤 **Artist pages** with a grouped discography view (albums, singles, EPs, compilations) and a full discography drilldown.
- 💿 **Album and playlist detail pages** with track lists and playback entry points.
- ▶️ **Playback and queue playback** powered by the official TIDAL player SDK and Media3 session.
- 🎚️ **Now Playing screen** with album art, progress, and transport controls sized for a round display.
- 🌑 **AOD / ambient-safe Now Playing** — readable in ambient mode without aggressive backlight.
- ⌚ **Back from Now Playing exits to the watch** while playback continues in the background.
- 🔁 **Explicit / clean duplicate preference** — when both versions exist, Untidy picks the one you want automatically.
- 🎛️ **Rotary / bezel scrolling** across all main lists (search, library, discover, artist, album, playlist, queue).
- ⚙️ **Settings** for playback quality, explicit/clean preference, and account.

> Untidy targets **Wear OS 4+ (API 33)** and declares itself as a **standalone** Wear app — it does not require a companion phone app to function.

## Roadmap

Things that are explicitly **not done yet** and are next up:

- 🧪 Real-watch deployment and QA on Galaxy Watch hardware (currently emulator-tested).
- 🔵 Samsung-specific behavior checks (One UI Watch quirks, bezel ergonomics, BT audio routing).
- 🟢 Ongoing Activity / return-to-playback validation from the watch face and recents.
- ⬇️ Downloads and offline playback.
- 🏪 Distribution channel decisions (sideload today; store listing later).

If you're shipping a Galaxy Watch and want to help shake out real-device issues, watch the issues tab.

## Project layout

```
TidalWearOS/
├─ app/                  # Wear OS application module (com.tidal.wear)
│  └─ src/main/java/com/tidal/wear
│     ├─ ui/             # Compose screens: search, library, discover,
│     │                  #   artist, album, playlist, player, settings, onboarding
│     ├─ ui/components/  # Wear-shaped UI primitives (rotary scroll, art card,
│     │                  #   progress, chips, volume overlay, …)
│     └─ playback/       # Now Playing state holder
├─ core/
│  ├─ model/             # Shared domain models
│  ├─ auth/              # TIDAL device-code auth wrapper
│  ├─ tidal-api/         # TIDAL API client
│  └─ playback/          # Media3 session + TIDAL player service
└─ build.gradle.kts
```

| Module             | What it does                                                  |
| ------------------ | ------------------------------------------------------------- |
| `:app`             | Wear OS UI, navigation, screens, Now Playing, settings        |
| `:core:model`      | Pure-Kotlin domain models shared by UI, API, and playback     |
| `:core:auth`       | TIDAL device-code OAuth flow and token storage                |
| `:core:tidal-api`  | Typed wrapper over TIDAL HTTP endpoints                       |
| `:core:playback`   | `MediaLibraryService` integration with the TIDAL player SDK   |

## Building

### Prerequisites

- **Android Studio** (Ladybug or newer recommended).
- **JDK 17** (the project compiles to `JavaVersion.VERSION_17`).
- **Android SDK** with platform **API 35** and a **Wear OS 4 emulator image**.
- A **TIDAL developer application** (Client ID / Client Secret) — Untidy authenticates via TIDAL's device-code flow against your own app credentials.

### TIDAL credentials

The build looks for credentials in `.dev-secrets\tidal-app.properties` at the repo root. Create the file and fill in your own values — this path is intentionally ignored from the public repo and **must not be committed**:

```properties
# .dev-secrets\tidal-app.properties
tidal.clientid=YOUR_TIDAL_CLIENT_ID
tidal.clientsecret=YOUR_TIDAL_CLIENT_SECRET
tidal.redirecturi=YOUR_TIDAL_REDIRECT_URI
# Optional: override the default scope set
# tidal.scopes=user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback
```

If the file is missing, the project still compiles but sign-in will not succeed at runtime.

### Build from the command line (Windows)

```powershell
# From the repo root
.\gradlew.bat :app:assembleDebug
```

Cross-platform equivalent:

```bash
./gradlew :app:assembleDebug
```

The debug APK is produced at `app\build\outputs\apk\debug\app-debug.apk`.

### Run on a Wear OS emulator

1. In Android Studio, open the repo as a Gradle project and let it sync.
2. Create a **Wear OS** AVD running **API 33+** (round, small/large profile both work).
3. Select the `app` run configuration and **Run**.

### Run on a real watch

This is the part that is not yet validated by the maintainers — see the roadmap above. The general flow:

1. Enable developer options and ADB debugging on the watch.
2. Pair over Wi-Fi or USB.
3. `gradlew.bat :app:installDebug` with the watch selected as the target device.

## Status

| Area                                  | State                |
| ------------------------------------- | -------------------- |
| Build, packaging, install (debug)     | ✅ Working            |
| Emulator: sign-in, browse, playback   | ✅ Working            |
| AOD / ambient Now Playing             | ✅ Implemented        |
| Real Galaxy Watch deployment + QA     | ⏳ Pending            |
| Samsung-specific behavior checks      | ⏳ Pending            |
| Ongoing Activity / return-to-playback | ⏳ Validation pending |
| Downloads / offline playback          | ❌ Not yet            |

Treat this as a **public beta**. Things will move; APIs and UI may change between commits.

## Contributing

Issues and PRs are welcome, especially:

- Real-device reports from Galaxy Watch (model, Wear OS version, what worked, what didn't).
- Small-screen UX fixes (rotary handling, AOD legibility, target sizes).
- Reproducible playback / queue bugs with logs.

Please keep credentials and any non-public TIDAL material **out of issues and PRs**.

## Legal & responsible use

Untidy is an **unofficial, independent** Wear OS client for the TIDAL music service. It is **not affiliated with, endorsed by, or sponsored by TIDAL, Samsung, Google, or any of their subsidiaries**. All trademarks are the property of their respective owners.

To use Untidy you need your **own active TIDAL account** and your **own TIDAL developer credentials**. Untidy does not bundle, distribute, or bypass any TIDAL audio content; playback is performed via TIDAL's official SDKs against your account. By using Untidy you agree to follow **TIDAL's Terms of Service and API terms**, as well as the terms of any other services you interact with.

---

<sub>Built with Kotlin, Jetpack Compose for Wear OS, AndroidX Media3, and the official TIDAL Auth/Player SDKs.</sub>
