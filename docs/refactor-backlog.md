# Refactor Backlog

_Last updated: 2026-06-16_

This backlog tracks large-file / high-coupling cleanup that should happen after the current emulator validation pass. None of these are required to boot or smoke-test the app, but each is worth paying down before the codebase grows much further.

## Priority 1 — API client decomposition

### `core/tidal-api/src/main/java/com/tidal/wear/core/api/TidalApiClient.kt`

- Current size: ~1,441 lines.
- Symptoms:
  - Retrofit service definitions, legacy API service definitions, domain parsing, search/discover/library orchestration, artwork heuristics, favorite write helpers, and JSON utilities all live in one file.
  - Changes for unrelated features can collide in the same file.
  - Pure parsing logic is hard to test in isolation because it is buried near network orchestration.
- Suggested split:
  - `TidalApiClient.kt` — public facade/orchestration only.
  - `TidalV2Service.kt` — Retrofit v2 interface.
  - `TidalLegacyService.kt` — legacy Retrofit interface.
  - `TidalJsonParsers.kt` — JSON:API / legacy parsing extensions.
  - `TidalArtworkParser.kt` — artwork/image URL heuristics.
  - `TidalFavoritesApi.kt` — collection/favorites read/write helpers.
  - `TidalDiscoverApi.kt` — discover/recommendation sections.
- Verification:
  - Move code without behavior changes first.
  - Add/keep pure parser tests.
  - Run `:core:tidal-api:testDebugUnitTest :core:tidal-api:compileDebugKotlin` after each slice.

## Priority 2 — Player screen decomposition

### `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`

- Current size: ~645 lines.
- Symptoms:
  - Main Now Playing UI, ambient behavior, action pager, volume handling, like/favorite state, download placeholder state, and action callbacks are mixed together.
  - Feature work like queue UI/add-to-playlist will make this file grow quickly.
- Suggested split:
  - `TidalPlayerScreen.kt` — top-level state wiring and pager only.
  - `NowPlayingPage.kt` — main visual player page.
  - `PlayerActionPage.kt` — action sheet page wiring.
  - `FavoriteTrackButton.kt` — favorite state/load/write UI.
  - `PlayerVolumeControls.kt` — rotary volume handling and overlay.
- Verification:
  - Compile-only should catch most split mistakes.
  - Later emulator smoke: play/pause, volume, actions page, favorite toggle.

## Priority 3 — Auth repository split

### `core/auth/src/main/java/com/tidal/wear/core/auth/TidalAuthRepository.kt`

- Current size: ~611 lines.
- Symptoms:
  - Device-code flow, token refresh, client credentials, account state, persistence, and fallback constants are all together.
  - Auth state is currently conceptually blended between user-authenticated and catalog/client-credential access.
- Suggested split:
  - `TidalAuthRepository.kt` — public state/facade.
  - `TidalDeviceAuthClient.kt` — device-code polling and token exchange.
  - `TidalTokenStore.kt` — SharedPreferences persistence.
  - `TidalClientCredentialsProvider.kt` — app/client credential token logic.
  - `AuthStateModel.kt` — explicit unauthenticated / anonymous catalog / user-linked model if product wants that distinction.
- Verification:
  - Unit tests around token expiry/refresh and state transitions.
  - Emulator auth flow after KVM is enabled.

## Priority 4 — Main navigation shell split

### `app/src/main/java/com/tidal/wear/MainActivity.kt`

- Current size: ~584 lines.
- Symptoms:
  - Activity, route definitions, navigation graph, Home screen, action helpers, and playback intent helpers live together.
  - Recent cleanup removed static selection stores, but the file is still doing too many jobs.
- Suggested split:
  - `MainActivity.kt` — Activity lifecycle only.
  - `Routes.kt` — route constants/builders.
  - `TidalWearNavGraph.kt` — NavHost and screen wiring.
  - `HomeScreen.kt` — home UI.
  - `PlaybackIntentLauncher.kt` — play track/queue intent construction.
- Verification:
  - Compile and no-login navigation smoke.
  - Emulator route smoke: search → album/artist/playlist → player → back.

## Priority 5 — Media service split

### `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`

- Current size: ~542 lines.
- Symptoms:
  - Android service lifecycle, Media3 session, queue state, notification/ongoing activity publishing, quality settings, backend event handling, and `SimpleBasePlayer` implementation share one file.
  - Exported-service/controller validation decision will add more code here if done inline.
- Suggested split:
  - `TidalMediaService.kt` — Android service/session lifecycle.
  - `PlaybackQueueController.kt` — queue state and next/previous behavior.
  - `OngoingPlaybackPublisher.kt` — ongoing activity/notification publishing.
  - `TidalSessionPlayer.kt` — move private `SimpleBasePlayer` implementation out.
  - `PlaybackControllerValidator.kt` — if exported service remains true.
- Verification:
  - Existing playback unit tests plus fake backend tests.
  - Emulator: notification controls, ongoing activity, queue transition, process-kill recovery.

## General refactor rules

- Prefer move-only commits before behavior changes.
- Keep feature work and file-splitting separate.
- Preserve emulator test coverage order: compile/lint first, then auth/playback smoke.
- Add pure tests while extracting parser/state logic.
