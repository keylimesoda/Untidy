# Downloads / Offline Playback Capability Spike

**Issue:** #11 / `UNTIDY-011`  
**Date:** 2026-06-17  
**Status:** Spike complete; implementation proof next. Ric confirmed TIDAL already supports/allows offline/download in 1st- and 3rd-party clients; Untidy should prove and use the sanctioned offline SDK/API path rather than caching STREAM manifests.

## Executive recommendation

Do **not** implement user-visible downloads by caching the current streaming manifest URLs.

Untidy should keep user-visible downloads/offline playback disabled until it implements the TIDAL-sanctioned offline/download path that includes entitlement, storage, offline license/expiry, and playback reporting semantics. Ric confirmed TIDAL already supports/allows offline/download in first- and third-party clients; the current app gap is implementation/provisioning proof, not a categorical permission blocker. The current app path is a streaming-only direct-manifest bridge and is not enough to safely store playable offline audio.

Immediate product action taken in this spike: the Now Playing action sheet stopped cycling through fake download states and rendered an honest offline proof/unavailable state while #11 proved the official provisioning path. Later follow-ups replaced that proof-only posture with the accepted single-track MVP route.

## Evidence from current app

### Current playback path is streaming-only

`core/playback/src/main/java/com/tidal/wear/core/playback/DirectManifestPlaybackBackend.kt` resolves track playback in this order:

1. Legacy desktop playback info:
   - `https://api.tidalhifi.com/v1/tracks/{id}/playbackinfopostpaywall`
   - query includes `playbackmode=STREAM`
   - parses BTS direct URLs only when `encryptionType` is `NONE`
2. OpenAPI track manifests:
   - `https://openapi.tidal.com/v2/trackManifests/{id}`
   - query includes `manifestType=MPEG_DASH`, `uriScheme=DATA`, `usage=PLAYBACK`, `adaptive=false`

Playback then creates a Media3 `DashMediaSource` or `ProgressiveMediaSource` from the returned network/data manifest and starts ExoPlayer. There is no persisted media store, download DB, offline license store, cache-backed media source, or local-vs-stream resolver.

### TIDAL SDK artifacts expose offline types, but not an integrated download implementation in Untidy

Local Gradle artifacts show TIDAL SDK 0.0.64 contains offline-facing classes:

- `com.tidal.sdk.player.offlineplay.OfflinePlayProvider`
- `com.tidal.sdk.player.streamingapi.playbackinfo.offline.OfflinePlaybackInfoProvider`
- `com.tidal.sdk.player.playbackengine.offline.cache.OfflineCacheProvider`
- `com.tidal.sdk.player.playbackengine.Encryption`
- `com.tidal.sdk.player.playbackengine.offline.OfflineDrmHelper`
- `PlaybackMode.OFFLINE`
- `TrackManifests$UsageTrackManifestsIdGet.DOWNLOAD`
- generated API groups including `Downloads`, `OfflineTasks`, and installation offline inventory models

This is a useful lead: the official SDK/OpenAPI surface appears to distinguish playback vs download/offline. But Untidy currently bypasses those offline SDK flows and uses direct streaming manifests. Ric confirmed TIDAL already has/allows offline/download in 1st- and 3rd-party clients. Treat these classes as the implementation proof target; they still do not justify raw STREAM URL caching.

## Android / Media3 feasibility

Media3 can implement offline media when the app has a legal, stable media source:

- `DownloadService` + `DownloadManager` are the normal background download path.
- Downloaded playback should use the same `Cache` through `CacheDataSource.Factory` and inject that into the player's media source factory.
- DRM-protected content must use Media3/ExoPlayer DRM support, and offline Widevine keys are loaded via `MediaItem.DrmConfiguration.Builder.setKeySetId` after acquiring/storing an offline key set.

This means Media3 is not the blocker. The blocker is acquiring TIDAL content and any required license in a sanctioned, replayable offline form.

## Practical approaches considered

### 1. Media3 DownloadService / DownloadManager over current streaming manifests

**Technically possible only for clear, stable URLs. Not recommended.**

Problems:

- Current requests explicitly use `usage=PLAYBACK` / `playbackmode=STREAM`, not download/offline usage.
- Streaming URLs and DASH data may be signed, expiring, watermarked, or governed as transient playback buffers.
- This path would not capture TIDAL offline entitlement, expiry, renewal, or reporting semantics.
- It bypasses the official offline/download contract by turning streaming delivery into user-visible downloads.

### 2. Direct URL caching of BTS URLs

**Rejected.**

The legacy BTS branch currently rejects encrypted BTS responses and streams first URL from `urls`. Caching those bytes as a download would be especially risky because the endpoint was requested with `playbackmode=STREAM` and not with any offline/download contract.

### 3. Official TIDAL SDK/OpenAPI offline path

**Best candidate, but needs focused follow-up with credentials/docs/device proof.**

Promising SDK/API signals:

- `PlaybackMode.OFFLINE`
- `usage=DOWNLOAD` enum for track manifests
- `OfflinePlaybackInfoProvider`
- `OfflineCacheProvider`
- `OfflineDrmHelper`
- `PlaybackInfo.Offline.Track` with `offlineLicense`, `storage`, `offlineRevalidateAt`, and `offlineValidUntil`
- generated `Downloads`, `OfflineTasks`, and installation offline inventory APIs

Required next proof:

1. Identify the blessed call sequence for creating an offline task/download and recording installation inventory.
2. Determine whether the SDK can download/cache media itself or only plays already-provisioned offline storage supplied by an app.
3. Validate one track using the sanctioned SDK/offline flow on a real authenticated device.
4. Confirm license expiry/renewal and sign-out cleanup rules.

### 4. Full custom downloader with app-private files

**Blocked unless this uses the same official offline/download provisioning pathway TIDAL clients already use.**

Even with app-private storage and encryption, a custom downloader must still use TIDAL's offline license/provisioning path. Do not invent local DRM or store raw audio from streaming endpoints.

## Code changes made

- `ActionsSheet.kt`
  - Added `DownloadState.Unavailable`.
  - Disabled the download row when offline is unavailable.
  - Changed user-facing copy to **Offline unavailable** instead of fake `Download` / `Downloading 47%` / `Downloaded` progression.
- `TidalPlayerScreen.kt`
  - Removed the local fake `downloadState` cycle.
  - Hard-wired current state to `DownloadState.Unavailable` until a real repository/backend exists.
  - Retained a defensive toast callback, though the row is disabled.
- `SettingsScreen.kt`
  - Updated Downloads section copy to avoid fake availability while #11 proved the official offline provisioning path.

## Follow-up outcome

This spike is complete and #11 is closed. The proof line moved into follow-up implementation issues instead of keeping #11 open:

1. #25 / `UNTIDY-024` accepted the canonical cache-key replay proof using `usage=DOWNLOAD`, app-private Media3 cache, and network-disabled replay.
2. #26 / `UNTIDY-025` accepted the single-track MVP app-authored route: persisted download marker, `usage=DOWNLOAD` manifest selection for marked tracks, and app-private offline cache playback path.
3. #27-#32 split the remaining product lifecycle into Wear OS UX design, Downloads shelf, local remove-download UX, offline fallback behavior, Settings download controls, and collection download UX.

The corrected ongoing rule is: keep the guardrail against `PLAYBACK` / `STREAM` manifest caching, but do not reopen #11 or frame TIDAL offline/download as categorically permission-blocked. Future work should build on the accepted `usage=DOWNLOAD` proof and the user-facing lifecycle issues.
