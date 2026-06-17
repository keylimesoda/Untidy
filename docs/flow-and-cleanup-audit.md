# Untidy Flow & Cleanup Audit

_Last updated: 2026-06-16_

This is a read-only synthesis of product-flow and architecture audits. It is meant to guide cleanup before/alongside emulator validation.

## Primary Flow Map

### App shell / navigation

- `app/src/main/java/com/tidal/wear/MainActivity.kt`
  - `MainActivity.onCreate()` requests notification permission and renders `TidalWearApp()`.
  - `TidalWearApp()` creates:
    - `TidalAuthRepositoryProvider.get(...)`
    - `SettingsRepository`
    - `TidalApiClient(authRepository)`
    - `NowPlayingStateHolder(..., pollPosition = false)` for home playback summary.
  - Nav routes: `Onboarding`, `Home`, `Discover`, `Search`, `Album`, `Playlist`, `Artist`, `ArtistAlbums`, `Library`, `Settings`.
  - `LaunchedEffect(authState)` routes signed-in users to Home and anonymous users to Onboarding.

### First launch / onboarding / auth

- `ui/onboarding/OnboardingScreen.kt`
  - Starts TIDAL device auth via `authRepository.startDeviceAuth()`.
  - Displays verification URI/code and polls via `authRepository.awaitAuthCompletion(session)`.
- `core/auth/TidalAuthRepository.kt`
  - Stores token state in `SharedPreferences("tidal_auth")`.
  - Exposes `authState`, `isAuthenticated`, `accountInfo`.
  - Uses two client contexts: catalog client credentials via configured `BuildConfig.*`, and user device auth via fallback constants.

### Home / discover / search / library

- `HomeScreen()` in `MainActivity.kt`
  - Primary action changes by auth/playback state: sign in, search, resume, now playing.
  - Links to Discover, Library, Settings; Downloads is disabled.
- `ui/discover/DiscoverScreen.kt`
  - Loads `apiClient.discoverSections()`.
  - Opens albums/playlists/artists or plays track/queue.
- `ui/search/SearchScreen.kt`
  - Uses a nearly invisible Android `EditText` host to trigger keyboard.
  - Calls `apiClient.search(query)`.
- `ui/library/LibraryScreen.kt`
  - Calls `apiClient.favorites()`.
  - Category drill-in for playlists/albums/tracks/artists.

### Album / playlist / artist playback

- `MainActivity.openAlbum()` navigates directly to `album/{albumId}`; route IDs are the source of truth.
- `ui/album/AlbumScreen.kt`
  - Loads `apiClient.album(albumId)` and `apiClient.albumTracks(albumId)`.
  - `Play Album` calls `onPlayQueue(tracks, 0)`.
- `ui/playlist/PlaylistScreen.kt`
  - Loads `apiClient.playlist(playlistId)` and `apiClient.playlistTracks(playlistId)`.
  - `Play all` and track rows call `onPlayQueue(tracks, index)`.
- `ui/artist/ArtistScreen.kt`
  - Loads artist, top tracks, and album groups.
  - Top tracks play as a queue; album sections navigate to `AlbumScreen`.
- `MainActivity.startQueuePlayback()`
  - Stores the queue in `PlaybackQueueStore.put(tracks)`.
  - Starts `TidalMediaService` with `ACTION_PLAY_QUEUE`.
  - Opens `PlayerActivity`.
- `core/playback/TidalMediaService.kt`
  - `playQueue()` pulls by queue id and calls `playTrack()`.
  - `playTrack()` configures quality, loads session metadata, loads direct manifest backend, and starts playback.

### Now playing / settings / errors

- `PlayerActivity.kt` hosts `TidalPlayerScreen()` and ambient mode.
- `playback/NowPlayingStateHolder.kt`
  - Connects `MediaController` to `TidalMediaService`.
  - Maps current media item + metadata to `NowPlayingUiState`.
  - Sends transport actions via foreground service intents.
- `ui/settings/SettingsScreen.kt`
  - Account state, sign-out, quality, duplicate-release preference, debug info.
- Error states are mostly generic per-screen text. Playback errors are mostly logged rather than surfaced to the player UI.

## Prioritized Cleanup Backlog

### P0 / High-impact before broad emulator testing

1. **Make queue playback process-death tolerant**
   - Files:
     - `core/playback/src/main/java/com/tidal/wear/core/playback/PlaybackQueueStore.kt`
     - `app/src/main/java/com/tidal/wear/MainActivity.kt` (`Context.startQueuePlayback`)
     - `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt` (`playQueue`)
   - Why: Queue handoff is process-memory only. Wear OS can kill/recreate services aggressively; a queued album/playlist may disappear between intent creation and service handling.
   - Minimal fix: Serialize the queue payload into the service intent or persist a queue snapshot in app storage/DataStore. Treat id-only static map as an optimization, not the source of truth.
   - Verification: JVM tests for serialization/missing queue behavior; emulator later with process kill/background start.

2. **Revisit `PlaybackQueueStore` 100-track cap**
   - File: `PlaybackQueueStore.kt`, `MAX_QUEUE_TRACKS = 100`.
   - Why: Long playlists/box sets can be silently truncated. Live TIDAL validation found real album collections over 100 tracks.
   - Minimal fix: Raise/remove cap, or make truncation explicit in UI. If memory is the concern, persist ids instead of full objects.
   - Verification: JVM tests for long queues; device perf validation for very large queues.

3. **Fix async load/play race in direct playback backend**
   - Files:
     - `TidalMediaService.kt` (`playTrack`)
     - `DirectManifestPlaybackBackend.kt` (`loadTrack`, `play`)
   - Why: `loadTrack()` launches async work and returns immediately; service then calls `play()` before media source is prepared. Prefetch reduces boundary latency but does not fully remove this race.
   - Minimal fix: Track desired play state inside backend and call `player.play()` after `prepare()` when load completes, or make load suspendable behind a service coroutine.
   - Verification: Fake backend/unit tests for call ordering; emulator/audio test.

4. **Lock down or validate exported media service**
   - File: `app/src/main/AndroidManifest.xml`, `TidalMediaService`.
   - Why: Service is exported without an app permission; lint flags this and external apps may send playback intents. Media controller/browser requirements should be explicit.
   - Minimal fix: If external browser access is not needed, set `android:exported="false"`. If it is needed, add controller/package validation in `onGetSession` and/or permission.
   - Verification: `lintDebug`; later media controls smoke test.

### Quick wins / no-emulator-friendly

5. **Surface playback errors in player UI**
   - Files:
     - `TidalMediaService.kt` backend error event handling
     - `NowPlayingStateHolder.kt` / `NowPlayingUiState`
     - `TidalPlayerScreen.kt`
   - Why: User currently sees stalled/paused/nothing for auth/network/manifest failures.
   - Minimal fix: propagate simple error text/state via media metadata/extras or controller state; render it unobtrusively.
   - Verification: unit/fake event test if seam exists; emulator later.

6. **Remove redundant SDK guard**
   - File: `MainActivity.kt`, notification permission request.
   - Why: minSdk is already 33; `SDK_INT >= 33` is obsolete and lint warns.
   - Minimal fix: request permission based on permission state only.
   - Verification: `lintDebug`.

7. **Fix Compose parameter order lint**
   - File: `ui/components/SecondaryChip.kt`.
   - Why: Compose lint expects `modifier` as first optional parameter.
   - Minimal fix: reorder function parameters and call sites if needed.
   - Verification: `lintDebug`, compile.

8. **Add Wear recents taskAffinity**
   - File: `app/src/main/AndroidManifest.xml`, `.MainActivity`, `.PlayerActivity`.
   - Why: lint flags Wear recents behavior.
   - Minimal fix: add explicit task affinity per Wear guidance.
   - Verification: `lintDebug`; emulator later.

### Medium refactors

9. **Replace static navigation selection stores**
   - File: `MainActivity.kt`, `AlbumSelectionStore`, `PlaylistSelectionStore`, `ArtistSelectionStore`.
   - Why: static maps disappear on process death and can grow without bounds. Routes already include ids; screens can load by id.
   - Minimal fix: treat id as source of truth; cache objects only as optional initial UI hints.
   - Verification: process-death route restore later; compile/unit checks now.

10. **Clarify auth state model**
    - Files:
      - `TidalAuthRepository.kt` (`ensureClientCredentialsToken`, `updateAuthState`, `hasUsableSession`)
      - `MainActivity.kt` auth gate
    - Why: client-credentials/anonymous catalog state and user-linked state are blended. Product behavior should explicitly choose whether catalog browsing is allowed pre-user-auth.
    - Minimal fix: model states as unauthenticated / anonymous-catalog / user-linked and route intentionally.
    - Verification: unit tests around repository state transitions; device/auth flow later.

11. **Make search input less fragile**
    - File: `ui/search/SearchScreen.kt`.
    - Why: hidden 1dp `EditText` keyboard trigger is likely brittle across Wear OS variants.
    - Minimal fix: visible Wear-friendly input affordance or explicit voice/keyboard launch UX.
    - Verification: emulator/device required.

## Cleanup progress — 2026-06-16

Completed without emulator/device validation:

- Album track pagination now follows TIDAL `links.next` until empty and was validated against live TIDAL album/collection examples.
- Queue handoff now sends both the in-memory queue id and a serialized capped queue payload, so `TidalMediaService` can recover if the static map is missing.
- The 100-track queue cap remains intentionally in place for watch ergonomics.
- Static album/playlist/artist navigation selection stores were removed; route ids are now the source of truth and detail screens load by id. Now Playing also carries album/artist route IDs for player action navigation.
- `DirectManifestPlaybackBackend.loadTrack()` is now suspendable and waits for manifest fetch/media-source preparation before service code calls `play()`.
- Playback/backend errors are surfaced into Media3 player state and rendered in the player UI.
- Lint quick wins completed: obsolete SDK check removed, `SecondaryChip` modifier parameter order fixed, Wear recents `taskAffinity` added, and small Compose autoboxing info items cleaned up.

Recommendation / follow-up:

- Keep `TidalMediaService` exported for now because it is a MediaLibraryService/MediaBrowserService endpoint likely needed by system media controllers. Do not blindly set `exported=false` until emulator/device testing confirms notification, Bluetooth/headset, recents, and media controls still work. If exported must remain true, add controller/package validation in `onGetSession(controllerInfo)` and keep the lint suppression/decision documented.
- KVM is now enabled and usable. The current emulator unblock is host SELinux policy: a temporary `selinuxuser_execheap` diagnostic allowed `UntidyWearOS51` to boot and the app to install/launch to onboarding/device-auth polling. Do not leave that boolean broadly enabled as a persistent daily-workstation fix. See `docs/emulator-selinux-notes.md`.
- Next device-test priority: no-login install/launch capture, authenticated TIDAL sign-in, single-track playback/error display, album/playlist play-all, process-kill queue recovery, View Album/View Artist, favorite/like toggles, and exported service/media controls behavior.

Verification at this checkpoint:

```bash
source scripts/env-android.sh
bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
git diff --check
```

Result: `BUILD SUCCESSFUL`; diff check clean. Data extraction/backup config is now privacy-safe and lint-clean. Remaining app lint is dependency-update warnings plus `ExportedService`, intentionally pending device validation/recommendation.


## Feature/spec progress — 2026-06-16

Completed in this pass:

- Implemented View Album / View Artist from Now Playing: playback metadata now carries album/artist IDs, PlayerActivity routes to existing detail screens, and the action sheet buttons use the real IDs with fallback toasts. Spec: `docs/spec-view-album-artist-from-now-playing.md`.
- Implemented API-backed favorite/like toggle in the player: reads favorite track state, writes add/remove via TIDAL user collection relationship endpoints, and rolls back optimistic UI on failure. Live authenticated API validation is still needed.
- Added privacy-safe Android `dataExtractionRules` so auth/playback/cache data is excluded from cloud backup and device transfer.
- Wrote feature specs for downloads/offline playback, current queue UI, and add-to-playlist workflow.

Verification:

```bash
source scripts/env-android.sh
bash ./gradlew :app:lintDebug :app:assembleDebug :core:tidal-api:testDebugUnitTest :core:playback:testDebugUnitTest --no-daemon
git diff --check
```

Result: `BUILD SUCCESSFUL`; diff check clean.

## Ordered QA / Feature TODO — emulator unblocked 2026-06-16

### Immediate emulator tests

1. Capture host state: KVM pass, Android Emulator 37.1.4, SELinux mode, and `selinuxuser_execheap` value.
2. Boot `UntidyWearOS51` using the temporary SELinux diagnostic only if needed; record `sys.boot_completed=1` and adb `device`.
3. Clean install and launch `app-debug.apk`; verify onboarding/device-code polling and capture screenshot/logcat.
4. Probe malformed exported media-service intents; confirm no crash or stuck foreground service.
5. Turn `selinuxuser_execheap` back off and record final state.

### Authenticated emulator tests

1. Complete TIDAL device-code auth and verify token persistence after relaunch/force-stop.
2. Validate search/catalog navigation and album pagination.
3. Validate single-track playback plus visible playback error display.
4. Validate album Play all and playlist Play all, including next/previous and transition smoothness.
5. Validate process-kill queue handoff for album and playlist starts.
6. Validate Now Playing View Album/View Artist and favorite/like read/write/rollback behavior.
7. Validate background playback, media notification/session controls, and exported service defensive behavior.

### Feature implementation order

1. **Current Queue UI** (`docs/spec-current-queue-ui.md`) — highest value now that queue reliability is the main emulator regression surface.
2. **Add to Playlist** (`docs/spec-add-to-playlist.md`) — build after live favorite/like auth/write validation, reusing auth and optimistic-update patterns.
3. **Downloads/offline playback** (`docs/spec-downloads-offline-playback.md`) — spike/design after online playback and queue UX are stable; requires storage/entitlement/background policy decisions and real-watch validation.
4. **Regression only for already-implemented features** — keep View Album/View Artist and favorite/like toggles in the emulator smoke matrix until live authenticated validation passes.

### SELinux/emulator policy follow-up

1. Keep `selinuxuser_execheap` off by default. Use on/off only for bounded emulator sessions.
2. Capture exact AVC/journal/coredump evidence with the boolean off.
3. Investigate a narrower local SELinux module scoped to Android Emulator/QEMU.
4. Prefer an isolated test host/VM over broad persistent workstation policy relaxation.
5. Retest after Android Emulator or Fedora SELinux policy updates.

