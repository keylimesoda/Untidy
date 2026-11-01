# Untidy architecture (surveyed 2026-11-01, v0.6.0-beta.6)

Standalone Wear OS TIDAL client. Package `com.tidal.wear`, launcher
`.MainActivity`, player `.PlayerActivity`, media service
`com.tidal.wear.core.playback.TidalMediaService`. compileSdk 35 / minSdk 33 /
target 35; AGP 8.7.3, Kotlin 2.2.21, Gradle 8.10.2, Java target 17.
Wear Compose foundation/material/material3/navigation all 1.5.0 (hybrid:
M3 `AppScaffold`/`ScreenScaffold`, M2 controls throughout), wear-input 1.2.0,
Horologist 0.6.22, Media3 fork `com.tidal.androidx.media3:*:1.5.0.1`.

## Modules

- `:app` — UI + navigation + auth orchestration. `MainActivity` hosts a
  `SwipeDismissableNavHost` with 13 routes: onboarding, home, discover, search,
  downloads, recent, recent/track/{id}, album/{id}, playlist/{id},
  artist/{id}, artist/{id}/albums, library, settings. Home content is built
  in `MainActivity.kt` (search chip, Offline-ready shelf, Recent shelf + See
  all, Explore: Library/Discover/On this watch/Settings). `PlayerActivity`
  runs in its own task and hosts `TidalPlayerScreen`: Now Playing (ambient
  aware, rotary = volume), pull-up `ActionsSheet` (`TransformingLazyColumn`),
  `QueueSheet`, `AddToPlaylistSheet`, `AudioOutputPicker` (Wear M2 Dialog).
  `PlayerNavigationState` (plain ViewModel) guards player-return routes.
- `:core:model` — domain models.
- `:core:auth` — `TidalAuthRepository`: TIDAL OAuth device-code flow
  (start → poll at interval → token), persisted in SharedPreferences
  `tidal_auth` (access_token, refresh_token, expires_at, user_id,
  token_client_id, granted_scopes). States: Initializing / Anonymous /
  UserSignedIn. Catalog credentials from `.dev-secrets/tidal-app.properties`
  into BuildConfig; `allowBackup=false`.
- `:core:tidal-api` — TIDAL REST client (pagination, favorites, playlists,
  search), okio/ktor-era networking; tests for request shape/pagination.
- `:core:playback` — `TidalMediaService` (MediaLibraryService +
  MediaLibrarySession) wraps `DirectManifestPlaybackBackend` (ExoPlayer,
  DASH/progressive) with `LogicalQueueForwardingPlayer`; app-authored service
  intents with command token. `PlaybackQueueStore` (in-memory map, 100-track
  cap) with serialized queue fallback in the start intent. Settings via shared
  Preferences DataStore `tidal_settings` (audio preset, release preference,
  download-over-LTE). Offline: SharedPreferences `offline-downloads` metadata +
  `filesDir/offline-downloads/cache-{trackId}` DASH caches,
  `OfflineDownloadScheduler` + WorkManager queue worker.

## Storage map

- `tidal_auth` (SharedPreferences): tokens, account metadata.
- `tidal_settings` (DataStore preferences): user settings.
- `recent` history (SharedPreferences, watch-local, explicit plays only).
- `offline-downloads` (SharedPreferences) + file cache.
- No Room, no cloud sync of recents/settings.

## Auth seeding (open gap, UX-009)

No test hook exists. Only repeatable path today: complete device-code sign-in
on the emulator once and keep its data (script boots `-no-snapshot-load`, no
`-wipe-data`; `adb install -r` preserves data). Watch is signed-in but not
debuggable → tokens not extractable. No production-only automation shortcuts.

## Entry points

App launcher, Media3 media browser/notification surfaces. No tiles or
complications. `wear-ongoing` dependency present but unused (UXC-010).

## Tests

35 JVM unit tests (app 11, playback 15, tidal-api 9; none for auth/model).
No androidTest, no screenshot tests, no CI workflow. Build/validate commands:
`:app:assembleDebug`, `testDebugUnitTest`, `lintDebug`.

## Prior audits (read before re-reporting)

June 2026 walkthroughs (`wear-os-ux-walkthrough-*`, safe-area, typography —
mostly landed), Sept 2026 `ux-dead-ends` (resolved in beta 6),
`hardware-power-2026-09-07` (short power samples; no battery-life claims).
