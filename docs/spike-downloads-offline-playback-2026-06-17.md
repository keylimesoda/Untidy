# Downloads / Offline Playback Capability Spike

**Issue:** #11 / `UNTIDY-011`  
**Date:** 2026-06-17  
**Status:** Spike complete; implementation proof next. Ric confirmed TIDAL already supports/allows offline/download in 1st- and 3rd-party clients; Untidy should prove and use the sanctioned offline SDK/API path rather than caching STREAM manifests.

## Executive recommendation

Do **not** implement user-visible downloads by caching the current streaming manifest URLs.

Untidy should keep downloads/offline playback disabled until it can use a TIDAL-sanctioned offline/download path that includes entitlement, storage, offline license/expiry, and playback reporting semantics. The current app path is a streaming-only direct-manifest bridge and is not enough to safely store playable offline audio.

Immediate product action taken in this spike: the Now Playing action sheet no longer cycles through fake download states. It now renders a disabled **Offline unavailable** row, and Settings explains that offline playback needs sanctioned TIDAL support.

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
- It risks violating TIDAL terms by turning streaming delivery into user-visible downloads.

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

**Blocked unless TIDAL explicitly permits and documents this.**

Even with app-private storage and encryption, a custom downloader must still respect TIDAL's offline license and terms. Do not invent local DRM or store raw audio from streaming endpoints.

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
  - Updated Downloads section copy to explain support is blocked on sanctioned TIDAL support rather than simply "coming".

## Recommended next action

Open a narrow follow-up spike: **TIDAL sanctioned offline SDK proof**.

Scope:

1. Inspect official TIDAL SDK docs/source/test app for `OfflinePlayProvider`, `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, `Downloads`, and `OfflineTasks` usage.
2. Build a debug-only proof that requests `usage=DOWNLOAD` / `PlaybackMode.OFFLINE` through official SDK APIs, never by raw stream URL caching.
3. Only after that succeeds, design Phase 1 state repository and Phase 2 single-track MVP.

Recommended issue status: keep #11 open as **blocked/needs upstream TIDAL offline proof**, with fake UI neutralized.
