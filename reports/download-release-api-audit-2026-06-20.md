# Download Release API/Storage Audit — 2026-06-20

## Scope and conclusion

Audited the current Untidy debug/offline download proof path in `/home/riclewis/.openclaw/workspace/projects/Untidy`, focusing on:

- `app/src/debug/java/com/tidal/wear/debug/OfflineProofActivity.kt`
- `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`
- `app/src/debug/java/com/tidal/wear/debug/OfflinePlaybackValidationActivity.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloads.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`
- release UI gates in `TidalPlayerScreen.kt`, `TrackContextScreen.kt`, downloads/settings/collection screens
- TIDAL API/auth plumbing in `core/tidal-api` and `core/auth`

**Recommendation:** Do **not** promote current media-byte download/cache-fill or offline playback to release yet. The safest release-ready slice is a **local offline-management shell** plus **accurate unavailable/coming-soon copy**, with no user-visible “Download” action that implies playable downloads. If Ric wants current-track downloads in release, the smallest technically safe promotion is **metadata/download-state plumbing only behind a remote/internal feature flag**, not media bytes or playback.

The debug proof has strong evidence that a sanctioned `trackManifests` `usage=DOWNLOAD` DASH manifest can be fetched and Media3 can fill an app-private cache. Later proof artifacts also show one network-disabled replay success after cache-key debugging. However, the implementation is still a debug harness, not a product download manager: no durable job lifecycle, no license/validity model, no server-side offline inventory/task orchestration, no entitlement/revalidation policy, no quota/storage policy, no robust resumability, and release UI currently depends on debug artifact polling.

## Architecture map: current download proof path

### 1. Release UI surfaces

**Now Playing action sheet**

- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
  - Defines `DownloadState`: `Unavailable`, `NotDownloaded`, `Downloading(progress)`, `Downloaded`.
  - Label mapping:
    - `Unavailable` -> `Offline unavailable`
    - `NotDownloaded` -> `Download`
    - `Downloading` -> `Downloading N%`
    - `Downloaded` -> `Downloaded`

- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
  - `initialDownloadState(track)` returns `DownloadState.Unavailable` in release because of `!BuildConfig.DEBUG`.
  - `startDebugDownload()` rejects release with toast: `Offline downloads are not available in this build`.
  - In debug only, it launches `com.tidal.wear.debug.OfflineProofActivity` and then polls `filesDir/offline-proof/latest.json` via `latestOfflineDownloadProofFor(track.id)`.
  - It marks the track downloaded with `markOfflineTrackDownloaded(track)` only if the latest proof event named `downloadManifestNetworkDisabledReplay` reports:
    - `playbackClaimed=true`
    - `reachedReady=true`
    - `offlineUpstreamAttempted=false`

**Track context menu**

- `app/src/main/java/com/tidal/wear/ui/recent/TrackContextScreen.kt`
  - Shows `Download` / `Downloaded` row.
  - Secondary copy for non-downloaded tracks: `Offline unavailable in release`.
  - `startDebugDownload(track)` is `BuildConfig.DEBUG` gated and launches `.debug.OfflineProofActivity` only in debug.

**Downloads shelf and removal UX**

- `app/src/main/java/com/tidal/wear/ui/downloads/DownloadsScreen.kt`
  - Reads local download metadata from `readOfflineDownloadedTracks()`.
  - Plays downloaded rows through `onPlayTrack(row.toTrack())`.
  - Removes local copy through `removeOfflineTrackDownload(row.id)`.

- `app/src/main/java/com/tidal/wear/ui/settings/SettingsScreen.kt`
  - Displays local download storage bytes and supports “remove local copies”.
  - Legal copy says: `Tokens are local and erasable`; `Unofficial client · TIDAL marks belong to TIDAL`; `Stream policy: AAC/MP4A non-lossless to preserve battery.`

**Collection screens**

- `AlbumScreen.kt` and `PlaylistScreen.kt`
  - Use `collectionDownloadSummary(...)` to display partial/full downloaded state.
  - Copy currently says things like `Local-valid subset plays offline`, `Downloaded tracks play offline`, and `Tracks save one at a time for now` even though release cannot create true playable downloads yet.
  - This is a release-copy red flag: copy implies a proven release offline product.

### 2. Local state/storage model currently in `main`

File: `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloads.kt`

- Uses `SharedPreferences` named `offline-downloads`.
- Stores:
  - `downloaded:<trackId>` boolean
  - `failed:<trackId>` boolean
  - `title:<trackId>`
  - `artist:<trackId>`
  - `downloadedAt:<trackId>`
- Exposes:
  - `markOfflineTrackDownloaded(track)`
  - `markOfflineTrackDownloadFailed(trackId)`
  - `isOfflineTrackDownloaded(trackId)`
  - `readOfflineDownloadedTracks()`
  - `removeOfflineTrackDownload(trackId)`
  - `removeAllOfflineTrackDownloads()`
  - `offlineDownloadsStorageBytes()`
  - `offlineTrackCacheDir(trackId)`
- Cache path is currently hardcoded to the debug proof directory:
  - `filesDir/offline-proof-cachefill/cache-$trackId`

This is useful for prototyping, but it is not release-grade. It conflates “downloaded metadata flag” with “valid playable media bytes and entitlement,” has no schema/versioning, and points main/release code at a directory named `offline-proof-cachefill`.

### 3. Playback integration currently in `main`

File: `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`

Current behavior:

1. `loadTrack` / `loadQueue` calls `resolveMediaSource(track)`.
2. `resolveMediaSource` always fetches a manifest first via `manifestRequest(trackId).await()`.
3. `fetchPlaybackManifest(trackId)`:
   - obtains token/client id from `TidalAuthRepository`;
   - if `isMarkedDownloaded(trackId)`, tries `fetchTrackManifest(... usage="DOWNLOAD")`;
   - otherwise falls back to desktop `playbackinfopostpaywall` with `playbackmode=STREAM`;
   - finally falls back to OpenAPI `trackManifests` with `usage="PLAYBACK"`.
4. If `isOfflineTrackDownloaded(trackId)` and cache directory exists, `downloadedTrackCache(trackId)` returns a `SimpleCache` over `offlineTrackCacheDir(trackId)`.
5. For DASH manifests and a non-null offline cache, `DashMediaSource.Factory` is configured with `CacheDataSource.Factory().setCache(offlineCache).setCacheKeyFactory(canonicalDownloadCacheKeyFactory(trackId)).setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext))`.

Important: this is **not strictly offline playback**. Even for marked-downloaded tracks, the backend still fetches a fresh `usage=DOWNLOAD` manifest from the network before playing. The cache data source also has a live upstream factory. If cache lookup misses, it can use the network. That is OK as an online acceleration/fallback prototype, but not a validated offline mode.

### 4. Debug proof runner

File: `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`

Entry points:

- `OfflineProofActivity` starts `OfflineProofService`.
- `OfflineProofService.onStartCommand` has a runtime `BuildConfig.DEBUG` guard.
- Debug manifest exports these debug activities/services for adb:
  - `.debug.OfflineProofService` exported=true
  - `.debug.OfflineProofActivity` exported=true
  - `.debug.OfflinePlaybackValidationActivity` exported=true

Proof sequence in `runProof(trackId, countryCode)`:

1. Auth probe:
   - `TidalAuthRepositoryProvider.get(applicationContext)`
   - reads access token, client id, account/scopes
   - writes redacted metadata only.

2. Sanctioned manifest fetch:
   - `GET https://openapi.tidal.com/v2/trackManifests/{trackId}`
   - query:
     - `manifestType=MPEG_DASH`
     - `formats=HEAACV1`
     - `uriScheme=DATA`
     - `usage=DOWNLOAD`
     - `adaptive=false`
     - `countryCode=...`
   - headers:
     - `accept: application/vnd.api+json`
     - `X-Tidal-Token: <clientId>`
     - `Authorization: Bearer <token>`

3. `offlinePlaybackStoreAdapterProbe(...)`:
   - persists a debug record under `filesDir/offline-proof-store`.
   - constructs `PlaybackInfo.Track` and `PlaybackInfo.Offline.Track` from the `usage=DOWNLOAD` manifest.
   - wires SDK `OfflinePlayProvider`, `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, and `Encryption` with a synthetic key.
   - proves the provider can serve stored offline info.
   - explicitly records `downloadBytesCached=false`, `playbackClaimed=false`, and `offlineLicensePresent=false`.

4. `downloadManifestShapeProbe(...)`:
   - decodes the `DATA` DASH manifest.
   - inspects manifest shape, content protection count, and segment-addressability without logging URLs.

5. `downloadManifestCacheFillProbe(...)`:
   - creates `SimpleCache` under `filesDir/offline-proof-cachefill/cache-$trackId`.
   - builds a `DashDownloader` using the `usage=DOWNLOAD` DASH manifest.
   - uses `CacheDataSource` with a canonical proof cache key factory.
   - fetches media bytes with Authorization and `X-Tidal-Token` headers.
   - records cache bytes/keys/progress, but keeps `playbackClaimed=false`.

6. `downloadManifestNetworkDisabledReplayProbe(...)`:
   - fills the cache as above.
   - constructs a replay `DashMediaSource` where chunk upstream is deliberately disabled.
   - records whether ExoPlayer reaches READY/playing without an upstream attempt.
   - proof artifacts show progression from failure (`offlineUpstreamAttempted=true`, `ExoPlaybackException: Source error`) to a later success in `reports/offline-proof-2026-06-17-1358-cache-miss-key-probe/latest.json` (`playbackClaimed=true`, `reachedReady=true`, `offlineUpstreamAttempted=false`).

7. Server-side offline discovery probes:
   - `probeInstallationInventory(...)`
   - `probeOfflineDiscoverySurfaces(...)`
   - `downloads/{id}`
   - `offlineTasks` with several query shapes
   - `userOfflineMixes/...`
   - Prior artifacts show these were mostly 400/404/500 or shape-discovery failures, not a proven release path.

8. SDK provider probes:
   - `offlineProviderWiringProbe(trackId)` proves basic SDK provider object wiring.
   - `playerOfflineProviderInjectionProbe(trackId, authRepository)` proves a `com.tidal.sdk.player.Player` can be constructed with an injected `OfflinePlayProvider`, and `streamingApi.getOfflineTrackPlaybackInfo(...)` returns the injected offline info.
   - `sdkOfflinePlaybackProbe(...)` calls SDK `getTrackPlaybackInfo(... PlaybackMode.OFFLINE ...)`; artifact reports a normal `PlaybackInfo$Track`, not a complete official offline entitlement/download lifecycle.

9. Artifacts:
   - Writes `filesDir/offline-proof/offline-proof-<timestamp>.json` and `filesDir/offline-proof/latest.json`.
   - Debug UI polls `latest.json` to decide whether to mark a track downloaded.

### 5. Auth/API posture

- `core/auth/src/main/java/com/tidal/wear/core/auth/TidalAuthRepository.kt`
  - Uses TIDAL device auth and stores tokens locally.
  - Contains fallback first-party TIDAL credentials, documented as reverse-engineered and “not registered to us.” This is already a release/legal risk independent of downloads; download promotion increases the sensitivity.

- `core/tidal-api/src/main/java/com/tidal/wear/core/api/TidalApiClient.kt`
  - `authenticatedClient(...)` injects `Authorization: Bearer ...` and `X-Tidal-Token` headers.
  - Existing service does not include production download/offline APIs; debug proof hand-builds these URLs with OkHttp.

## Evidence from proof artifacts

Observed/redacted local artifacts:

- `reports/offline-proof-2026-06-17-1320-cache-fill/latest.json`
  - `trackManifestDownload`: HTTP 200.
  - `downloadManifestCacheFill`: cache fill succeeded; `cacheBytesAdded=2428102`; `playbackClaimed=false`.
  - `offlinePlaybackStoreAdapter`: manifest persisted/provider served; `offlineLicensePresent=false`; `playbackClaimed=false`.

- `reports/offline-proof-2026-06-17-1345-network-disabled-replay-manifest-ok/latest.json`
  - cache fill succeeded; `cacheBytesAdded=2428102`; `cacheKeysAfterFill=371`; `downloadProgressPercent=100.0`.
  - network-disabled replay failed: `reachedReady=false`, `offlineUpstreamAttempted=true`, `ExoPlaybackException: Source error`.

- `reports/offline-proof-2026-06-17-1356-canonical-cache-replay/latest.json`
  - still failed replay with `offlineUpstreamAttempted=true` despite more cache keys.

- `reports/offline-proof-2026-06-17-1358-cache-miss-key-probe/latest.json`
  - `downloadManifestNetworkDisabledReplay`: `playbackClaimed=true`, `reachedReady=true`, `offlineUpstreamAttempted=false`, cache fill succeeded.

Interpretation: media-byte cache fill and one network-disabled replay have been proven in debug for a specific path/track. That is promising, but it is not yet a release download subsystem.

## Specific blockers to true release download/playback

### Critical blockers

1. **No release download manager/service exists.**
   - Current byte download is a debug `Service` proof runner, launched through a debug-only activity and polled via a JSON artifact.
   - Release needs a bounded job model, cancellation, retry/resume, notifications/foreground behavior on Wear, storage accounting, failure states, and lifecycle-safe UI state.

2. **No durable release schema for media download records.**
   - `SharedPreferences` booleans are not enough.
   - Needed fields include track id, title/artist/artwork, manifest hash/content hash, cache dir, byte count, downloadedAt, source surface, format/quality, countryCode, token client id hash, validity/revalidation timestamps, last validation, failure reason, schema version.

3. **No official offline license/validity policy is integrated.**
   - Store-adapter proof had `offlineLicensePresent=false` and `offlineRevalidateAt=-1`, `offlineValidUntil=-1`.
   - Even if current `usage=DOWNLOAD` manifests are unprotected for HEAACV1, release should not pretend indefinite offline entitlement without an official revalidation/expiry model.

4. **Server-side offline inventory/task/download orchestration remains unresolved.**
   - Probes for installations, offline inventory, `downloads`, `offlineTasks`, and `userOfflineMixes` did not establish a reliable production path.
   - If TIDAL expects clients to register offline inventory/tasks, bypassing it could be unsupported even if manifest/cache works.

5. **Main playback code still requires network to fetch a manifest before offline playback.**
   - `DirectManifestPlaybackBackend.fetchPlaybackManifest()` calls TIDAL before building the offline source.
   - True offline playback must be able to load a persisted manifest/record without network, then use only cache bytes.

6. **Main playback cache source allows network fallback for downloaded tracks.**
   - `DirectManifestPlaybackBackend` uses `CacheDataSource` with `DefaultDataSource.Factory(appContext)` as upstream.
   - For validated offline mode, cache misses must fail locally or explicitly transition to online streaming with honest UI, not silently stream while labeled offline.

7. **Cache path/keying is still proof-named and under-specified.**
   - `offlineTrackCacheDir(trackId)` points to `offline-proof-cachefill/cache-$trackId`.
   - A release path should be renamed and versioned, e.g. `filesDir/offline-downloads/v1/tracks/<trackId>/media-cache`, and protect against path injection/sanitization issues.

8. **Release UI copy overclaims in some collection/download surfaces.**
   - Examples: `Downloaded tracks play offline`, `Local-valid subset plays offline`, `Download from Now Playing`.
   - Since release cannot create playable downloads, copy should not imply availability.

9. **Auth/client-id posture is sensitive.**
   - The repository uses fallback reverse-engineered TIDAL credentials pending a proper registered Limited Input Device client. Shipping download/offline functionality on top of that is a higher legal/platform risk than streaming/library browsing.

### Important blockers

10. **No multi-track/album/playlist batch semantics.**
    - Album/playlist screens have summary UI, but current proof is one-track at a time.
    - Need queueing, partial failures, retry, and storage limits before collection downloads.

11. **No robust revalidation/offline-expiry behavior.**
    - Need periodic online checks and a state such as `Expired`, `Needs connection`, or `Unavailable`.

12. **No DRM/encryption strategy.**
    - Debug `Encryption` uses synthetic keys and empty decrypted header.
    - If TIDAL returns protected manifests or license data for other tracks/qualities/regions, release cannot handle it.

13. **No release-safe telemetry/logging policy for download internals.**
    - Debug proof carefully redacts, but production downloader must maintain the same discipline: no URLs, tokens, manifests, license blobs, cache keys, or user IDs in logs/artifacts.

14. **No formal storage quota/eviction policy.**
    - `NoOpCacheEvictor` is fine for a proof; release needs explicit quota and user-visible storage management.

15. **No tests around actual downloader state machine.**
    - Existing test coverage is limited to `CollectionDownloadSummaryTest`.

## What is safe to promote vs. keep debug-only

### Safe in main/release with cleanup

- Local metadata/state helpers, **if renamed and made honest**:
  - `DownloadedTrackSummary`
  - `CollectionDownloadSummary`
  - read/remove local records
  - settings storage display/remove all
- Downloads shelf as an empty-state/management page, **if copy says no release downloads yet**.
- `NetworkStatus` and offline fallback routing that avoids attempting online streaming when offline.
- Tests for local state and collection summary.

### Safe extraction only if it does not enable UI behavior

A small code extraction could be safe if done as inert library plumbing:

- `TidalDownloadManifestApi` in `core/playback` or `core/tidal-api`:
  - Builds the sanctioned `trackManifests` `usage=DOWNLOAD` request.
  - Parses only metadata/redacted manifest shape.
  - Not called from release UI yet.

- `DownloadCachePaths` / `DownloadRecord` model:
  - Sanitized release cache path and versioned record schema.
  - No byte download action exposed.

- `DownloadCacheKeyFactory`:
  - Shared cache-key logic extracted from debug and backend.
  - Unit tested with URI/key cases.

I did not implement this extraction because the current code is still proof-coupled and the task asked to implement only if very safe. A partial extraction would be easy to misread as release enablement unless paired with feature gating and copy cleanup.

### Must remain debug-only for now

- `OfflineProofActivity`, `OfflineProofService`, `OfflinePlaybackValidationActivity`.
- Any adb/exported proof entry points.
- `DashDownloader` cache-fill using user token and `usage=DOWNLOAD` until wrapped in a production download manager with policy.
- Polling `offline-proof/latest.json` from UI.
- Synthetic SDK `OfflinePlayProvider` / `Encryption` wiring.
- Marking a track downloaded solely based on proof artifact success.
- Server-side installation/offlineTasks/downloads probes.

## Recommended smallest release slice

### Recommended release slice: “Downloads management shell + honest unavailable copy”

This is the safest slice for the current release:

1. Keep `BuildConfig.DEBUG` gating for current-track downloads.
2. Keep no release media-byte download action.
3. Make release copy consistent:
   - Now Playing: `Offline unavailable` / toast `Offline downloads are not available in this build` is OK.
   - Track context: `Offline unavailable in release` is OK, but label should probably be `Offline unavailable` instead of `Download` in release.
   - Downloads empty state should say `Offline downloads are not available in this build` rather than `Download from Now Playing` in release.
   - Album/playlist copy should not say `Downloaded tracks play offline` unless at least one valid local record exists and the backend can prove offline playback. Prefer `Local copies managed on watch` or hide collection download language in release.
4. Keep remove-local-copy controls, because they only mutate Untidy-local app storage and are safe.
5. Do not expose album/playlist download action beyond “coming after track MVP”.

This slice is honest, release-safe, and prevents users from relying on an unsupported offline feature.

### Next internal slice: “Release-grade current-track metadata/download state only”

If Ric wants visible progress toward current-track downloads without shipping playback:

- Add a `DownloadRepository` in `core/playback/src/main/java/com/tidal/wear/core/playback/offline/` with states:
  - `Unavailable`
  - `Eligible`
  - `Queued`
  - `FetchingManifest`
  - `CachedForValidation`
  - `ValidatedPlayable`
  - `Failed(reason)`
  - `Expired`
- In release, default feature flag remains off. In internal builds, UI can show `Download` only when flag is on.
- Persist only metadata and proof state initially; do not claim playback until network-disabled replay is integrated outside the debug harness.

### Later internal slice: “Media bytes cache fill, no offline playback claim”

Only after state/repository exists:

- Move a sanitized version of `downloadManifestCacheFillProbe` into `DownloadWorker` / `DownloadManager`.
- Use release cache path, quota, cancellation, and notification.
- Mark state as `CachedForValidation`, not `Downloaded`, until replay validation passes.
- Never use raw `STREAM`/desktop `playbackmode=STREAM` manifests for download cache.

### Final release slice: “Offline playback”

Only after these are true:

- Offline playback can start with network disabled from a persisted release record.
- Cache source has no network upstream in offline mode.
- Manifest/key/cache lookup is stable across app restarts.
- Entitlement/revalidation policy is implemented.
- Multi-device/account change invalidation is handled.
- Physical Wear OS validation passes.

## Concrete implementation plan

### Phase 0 — release-copy safety cleanup

Files:

- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
- `app/src/main/java/com/tidal/wear/ui/recent/TrackContextScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/downloads/DownloadsScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/album/AlbumScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/playlist/PlaylistScreen.kt`

Changes:

- Add a small function such as `offlineDownloadsEnabled(): Boolean = BuildConfig.DEBUG` or a real build config/feature flag.
- In release:
  - show `Offline unavailable`, not `Download`, for current-track context row;
  - downloads empty state: `Offline downloads unavailable` / `This build can remove local copies only` or similar;
  - collection detail labels should not imply playable offline subsets unless the feature flag is enabled and there are validated downloads.

Tests:

- Compose text tests if available, or screenshot/UI smoke.
- Unit test any label helpers if extracted from private functions.

### Phase 1 — state model/repository extraction, no bytes

Files to add:

- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloadState.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloadRecord.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloadRepository.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloadPaths.kt`

Changes:

- Replace boolean-only `downloaded:<id>` with versioned records. SharedPreferences can be acceptable for the first small slice, but DataStore or JSON records under a versioned directory would be cleaner.
- Add a migration from current proof prefs if needed, but prefer treating proof prefs as non-release/debug data.
- Rename path from `offline-proof-cachefill` to release-safe path only after migration/clear behavior is designed.
- Keep `markOfflineTrackDownloaded` internal or rename to `markOfflineTrackValidatedPlayable` to avoid false state.

Tests:

- `OfflineDownloadRepositoryTest`:
  - writes/reads record;
  - rejects blank/unsafe ids;
  - remove deletes metadata and cache dir;
  - failed state does not count as downloaded;
  - account/client-id mismatch invalidates or hides records.

### Phase 2 — sanctioned download manifest client, no UI enablement

Files to add/change:

- `core/tidal-api/src/main/java/com/tidal/wear/core/api/TidalDownloadManifestClient.kt` or `core/playback/.../DownloadManifestClient.kt`
- `core/playback/src/test/.../DownloadManifestRequestTest.kt`

Functions/classes:

- `suspend fun fetchDownloadManifest(trackId: String, countryCode: String, audioPreset: AudioPreset): DownloadManifestResult`
- `data class DownloadManifestResult(trackId, manifestDataUri, manifestHash, formats, hasContentProtection, licenseUrlPresent, sourceSurface="trackManifests usage=DOWNLOAD")`
- Request must use:
  - `manifestType=MPEG_DASH`
  - `uriScheme=DATA`
  - `usage=DOWNLOAD`
  - `adaptive=false`
  - `formats=HEAACV1` initially
- Must never log raw manifest URI/body/license/tokens.

Tests:

- MockWebServer test for request path/query/headers.
- Parser test for data URI manifest, missing attrs, content-protection count.
- Redaction test: error/log summaries do not contain bearer token, manifest data URI, or segment URLs.

### Phase 3 — internal byte cache fill, still no release claim

Files to add/change:

- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/TrackDownloadManager.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/DownloadCacheKeyFactory.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/DownloadCacheStore.kt`
- app-level foreground service/worker if needed: `app/src/main/java/com/tidal/wear/download/TrackDownloadService.kt`

Changes:

- Move sanitized `DashDownloader` logic out of debug proof.
- Use a quota-aware cache evictor or explicit max bytes per account.
- Store state as `CachedForValidation` after fill.
- Add cancellation and progress Flow.
- Keep feature flag internal/off by default.

Tests:

- Fake downloader/cache tests for state transitions.
- Instrumented test with MockWebServer manifest + fake segments.
- Failure tests for 401/403/404/5xx, no storage, cancellation.

### Phase 4 — offline playback path

Files to change:

- `core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt`
- new `OfflinePlaybackResolver.kt`
- possibly integrate SDK `OfflinePlayProvider` only after official record/license model exists.

Required backend behavior:

- If offline/network unavailable and track has a validated record:
  - load persisted manifest from local record;
  - create media source using local cache only;
  - no TIDAL HTTP call before prepare;
  - no upstream fallback for chunk misses.
- If online and downloaded:
  - choose explicit policy: prefer local cache with online revalidation, or stream online. UI should distinguish.
- On cache miss/expiry:
  - fail with user-facing `Connect to refresh download`, not silent streaming labeled offline.

Tests:

- Unit test resolver chooses local record without calling API.
- Instrumented network-disabled playback test after app restart.
- Test cache miss does not open upstream in offline mode.
- Test account sign-out/account switch hides/deletes offline records.

## Red flags / security / legal / release-copy concerns

1. **Do not ship raw STREAM caching.** Current proof correctly uses `trackManifests usage=DOWNLOAD`; keep this invariant explicit in code/tests.
2. **Do not ship debug adb/exported proof components.** They are debug-source only; keep them out of release manifest.
3. **Do not mark tracks `Downloaded` from cache-fill alone.** Only validated offline replay plus entitlement/revalidation should produce a user-facing downloaded state.
4. **Do not rely on proof JSON artifacts in release UI.** Replace with repository state.
5. **Do not log or persist raw tokens/manifests/license URLs in reports.** Existing proof redaction is good; production must preserve it.
6. **Do not silently fall back to network while claiming offline.** Current main backend can do this due to upstream factory and network manifest fetch.
7. **Reverse-engineered auth/client credentials are a platform risk.** Shipping downloads/offline increases the chance of account/platform problems. Prefer a properly registered TIDAL client before broad release.
8. **Release copy must stay honest.** Avoid `Downloaded tracks play offline` / `Download from Now Playing` unless the release build actually supports that path.
9. **Use app-private storage only.** Current proof uses `filesDir`, which is good. Keep removal local and never mutate TIDAL library/playlists/offline inventory unless the official API shape is proven.
10. **Storage quota is mandatory before real downloads.** Wear devices have constrained storage; `NoOpCacheEvictor` is not release-safe.

## Suggested immediate decision

For the current release: ship with downloads/offline management visible only as a safe management shell, not as enabled downloads. The first mergeable engineering task should be **copy cleanup + repository/state extraction**, not media bytes. Once the repository exists, move the sanctioned `usage=DOWNLOAD` manifest/client and cache-key logic under tests, then promote byte cache fill behind an internal feature flag. Offline playback should be last, after a network-disabled, app-restart validation path works from release records without TIDAL network access.
