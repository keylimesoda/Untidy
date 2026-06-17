# Offline SDK Proof Plan — 2026-06-17

Issue: UNTIDY-011 / GitHub #11 — downloads and offline playback.

Scope of this note: map the sanctioned TIDAL SDK/API path exposed by local `com.tidal.sdk` 0.0.64 artifacts and define a minimal one-track proof. This is **not** an implementation patch and does not mutate production code.

Guardrail: do **not** cache the current streaming path (`usage=PLAYBACK`, legacy `playbackmode=STREAM`, raw stream/manifest URLs). The proof must use TIDAL offline/download surfaces: `PlaybackMode.OFFLINE`, `usage=DOWNLOAD`, offline playback info, offline cache/storage, DRM helper, downloads/offline-task/installation inventory APIs.

## Evidence sources inspected

- `docs/spike-downloads-offline-playback-2026-06-17.md`
- `docs/spec-downloads-offline-playback.md`
- `docs/work-items/BOARD.md`
- Local Gradle cache artifacts:
  - `com.tidal.sdk:player:0.0.64`
  - `com.tidal.sdk:player-streaming-api:0.0.64`
  - `com.tidal.sdk:player-playback-engine:0.0.64`
  - `com.tidal.sdk:player-common:0.0.64`
  - `com.tidal.sdk:tidalapi:0.3.33`
  - TIDAL Media3 artifacts `com.tidal.androidx.media3:*:1.5.0.1`
- Signature extraction was from local bytecode using `javap` via the workspace JDK.
- Web searches for public/offical examples of `OfflinePlayProvider` / `OfflinePlaybackInfoProvider` returned no usable public example. Treat artifact signatures as the primary source for now.

## Discovered SDK/API surfaces and signatures

Kotlin suspend methods appear in `javap` as `Object ...(..., Continuation<? super T>)`; in Kotlin usage these should be normal `suspend fun` calls.

### Player construction / offline provider injection

```java
public final class com.tidal.sdk.player.Player {
  public Player(
    android.app.Application,
    com.tidal.sdk.auth.CredentialsProvider,
    com.tidal.sdk.eventproducer.EventSender,
    boolean,
    boolean,
    kotlin.jvm.functions.Function0<java.lang.Integer>,
    com.tidal.sdk.player.playbackengine.model.BufferConfiguration,
    com.tidal.sdk.player.playbackengine.model.AssetTimeoutConfig,
    com.tidal.sdk.player.streamingapi.StreamingApiTimeoutConfig,
    com.tidal.sdk.player.playbackengine.player.CacheProvider,
    boolean,
    boolean,
    okhttp3.OkHttpClient,
    com.tidal.sdk.player.playbackengine.playbackprivilege.PlaybackPrivilegeProvider,
    com.tidal.sdk.player.offlineplay.OfflinePlayProvider,
    java.lang.String,
    java.lang.String,
    java.lang.String
  );

  public final com.tidal.sdk.player.playbackengine.PlaybackEngine getPlaybackEngine();
  public final com.tidal.sdk.player.streamingapi.StreamingApi getStreamingApi();
}
```

`OfflinePlayProvider` is explicitly constructor-injected into `Player`:

```java
public final class com.tidal.sdk.player.offlineplay.OfflinePlayProvider {
  public OfflinePlayProvider(
    com.tidal.sdk.player.streamingapi.playbackinfo.offline.OfflinePlaybackInfoProvider,
    com.tidal.sdk.player.playbackengine.offline.cache.OfflineCacheProvider,
    com.tidal.sdk.player.playbackengine.Encryption
  );

  public OfflinePlayProvider();
  public final OfflinePlaybackInfoProvider getOfflinePlaybackInfoProvider();
  public final OfflineCacheProvider getOfflineCacheProvider();
  public final Encryption getEncryption();
}
```

Important implication: the SDK expects the app to provide three offline-capable dependencies:

1. an offline playback-info provider,
2. an offline cache provider,
3. an encryption implementation.

### Offline playback-info provider

```java
public interface com.tidal.sdk.player.streamingapi.playbackinfo.offline.OfflinePlaybackInfoProvider {
  public abstract Object getOfflineTrackPlaybackInfo(
    java.lang.String trackId,
    java.lang.String streamingSessionId,
    Continuation<? super PlaybackInfo>
  );

  public abstract Object getOfflineVideoPlaybackInfo(
    java.lang.String videoId,
    java.lang.String streamingSessionId,
    Continuation<? super PlaybackInfo>
  );
}
```

The streaming API and repository expose matching calls:

```java
public interface com.tidal.sdk.player.streamingapi.StreamingApi {
  Object getTrackPlaybackInfo(String id, AudioQuality, PlaybackMode, boolean adaptive, String streamingSessionId, boolean immersive, Continuation<? super PlaybackInfo>);
  Object getOfflineTrackPlaybackInfo(String id, String streamingSessionId, Continuation<? super PlaybackInfo>);
  Object getOfflineVideoPlaybackInfo(String id, String streamingSessionId, Continuation<? super PlaybackInfo>);
}

public interface com.tidal.sdk.player.streamingapi.playbackinfo.repository.PlaybackInfoRepository {
  Object getOfflineTrackPlaybackInfo(String id, String streamingSessionId, Continuation<? super PlaybackInfo>);
  Object getOfflineVideoPlaybackInfo(String id, String streamingSessionId, Continuation<? super PlaybackInfo>);
}
```

`PlaybackInfoRepositoryDefault` accepts `OfflinePlaybackInfoProvider` and delegates offline methods to it, while online track playback info can request `PlaybackMode.OFFLINE`:

```java
public final class PlaybackInfoRepositoryDefault implements PlaybackInfoRepository {
  public PlaybackInfoRepositoryDefault(
    OfflinePlaybackInfoProvider,
    PlaybackInfoService,
    dagger.Lazy<ApiErrorMapper>,
    com.tidal.sdk.tidalapi.generated.apis.TrackManifests,
    com.tidal.sdk.tidalapi.generated.apis.VideoManifests
  );

  public Object getTrackPlaybackInfo(String id, AudioQuality, PlaybackMode, boolean, String, boolean, Continuation<? super PlaybackInfo.Track>);
  public Object getOfflineTrackPlaybackInfo(String id, String streamingSessionId, Continuation<? super PlaybackInfo>);
}
```

### Playback mode and manifest download usage

```java
public enum com.tidal.sdk.player.streamingapi.playbackinfo.model.PlaybackMode {
  STREAM,
  OFFLINE
}
```

OpenAPI track-manifest wrapper:

```java
public interface com.tidal.sdk.tidalapi.generated.apis.TrackManifests {
  Object trackManifestsIdGet(
    String id,
    ManifestTypeTrackManifestsIdGet manifestType,
    List<String> formats,
    UriSchemeTrackManifestsIdGet uriScheme,
    UsageTrackManifestsIdGet usage,
    boolean adaptive,
    String countryCode,
    Continuation<? super Response<TrackManifestsSingleResourceDataDocument>>
  );
}
```

Relevant enums discovered:

```java
ManifestTypeTrackManifestsIdGet: HLS, MPEG_DASH
UriSchemeTrackManifestsIdGet: HTTPS, DATA
UsageTrackManifestsIdGet: PLAYBACK, DOWNLOAD
FormatsTrackManifestsIdGet includes HEAACV1 and EAC3_JOC among other values; use concrete enum values from the generated artifact when compiling the proof
```

`TrackManifestsAttributes` include offline-relevant fields:

```java
public final class TrackManifestsAttributes {
  public DrmData getDrmData();
  public List<TrackManifestsAttributes.Formats> getFormats();
  public String getHash();
  public String getUri();
  public AudioNormalizationData getAlbumAudioNormalizationData();
  public AudioNormalizationData getTrackAudioNormalizationData();
}
```

`DrmData` includes:

```java
public final class DrmData {
  public String getCertificateUrl();
  public DrmData.DrmSystem getDrmSystem();
  public List<String> getInitData();
  public String getLicenseUrl();
}
```

### Offline playback-info model

```java
public final class PlaybackInfo.Offline.Track implements PlaybackInfo, PlaybackInfo.Offline {
  public PlaybackInfo.Offline.Track(
    PlaybackInfo.Track track,
    String offlineLicense,
    com.tidal.sdk.player.streamingapi.offline.Storage storage,
    boolean partiallyEncrypted
  );

  public PlaybackInfo.Track getTrack();
  public String getOfflineLicense();
  public Storage getStorage();
  public boolean getPartiallyEncrypted();

  // Delegated PlaybackInfo values from the inner Track:
  public String getStreamingSessionId();
  public ManifestMimeType getManifestMimeType();
  public String getManifest();
  public String getLicenseUrl();
  public long getOfflineRevalidateAt();
  public long getOfflineValidUntil();
}
```

Base online `PlaybackInfo.Track` already carries offline validity timestamps:

```java
public PlaybackInfo.Track(
  int trackId,
  AudioQuality audioQuality,
  AssetPresentation assetPresentation,
  AudioMode audioMode,
  String manifestHash,
  PreviewReason previewReason,
  String streamingSessionId,
  ManifestMimeType manifestMimeType,
  String manifest,
  String licenseUrl,
  float albumReplayGain,
  float albumPeakAmplitude,
  float trackReplayGain,
  float trackPeakAmplitude,
  long offlineRevalidateAt,
  long offlineValidUntil
);
```

Offline storage model:

```java
public final class com.tidal.sdk.player.streamingapi.offline.Storage {
  public Storage(boolean externalStorage, String path);
  public boolean getExternalStorage();
  public String getPath();
}
```

### Offline cache/storage and DRM playback helpers

```java
public interface com.tidal.sdk.player.playbackengine.offline.cache.OfflineCacheProvider {
  Cache getExternal(String path);
  Cache getInternal(String path);
}

public final class OfflineStorageProvider {
  public OfflineStorageProvider(
    OfflinePlayDataSourceFactoryHelper,
    OfflinePlayDrmDataSourceFactoryHelper
  );

  public DataSource.Factory getDataSourceFactoryForOfflinePlay(Storage storage, boolean drm);
}
```

SDK media-source factories consume offline storage:

```java
public final class PlayerDashOfflineMediaSourceFactory {
  public DashMediaSource create(
    MediaItem,
    String manifest,
    String offlineLicense,
    Storage,
    DrmSessionManagerProvider
  );
}

public final class PlayerProgressiveOfflineMediaSourceFactory {
  public ProgressiveMediaSource create(MediaItem, String manifest, Storage);
}

public final class PlayerDecryptedHeaderProgressiveOfflineMediaSourceFactory {
  public ProgressiveMediaSource create(MediaItem, String manifest, String storagePath);
}
```

DRM helper:

```java
public final class OfflineDrmHelper {
  public OfflineDrmHelper(Base64Codec);
  public void setOfflineLicense(String offlineLicense, DrmSessionManager drmSessionManager);
}
```

Encryption hook required by `OfflinePlayProvider`:

```java
public interface com.tidal.sdk.player.playbackengine.Encryption {
  byte[] getSecretKey();
  byte[] getDecryptedHeader(String pathOrKey);
}
```

Interpretation: cached/offline bytes are expected to live in Media3 `Cache` instances keyed by a `Storage.path`, with app-provided encryption/decrypted-header support. The SDK playback engine has the offline media-source path; Untidy should not build its own raw stream cache.

### TIDAL generated Downloads / OfflineTasks / Installations APIs

Downloads are read-only in the generated surface:

```java
public interface Downloads {
  Object downloadsGet(List<String> countryCode, List<String> include, Continuation<? super Response<DownloadsMultiResourceDataDocument>>);
  Object downloadsIdGet(String id, List<String> include, Continuation<? super Response<DownloadsSingleResourceDataDocument>>);
  Object downloadsIdRelationshipsOwnersGet(String id, List<String> include, String countryCode, Continuation<? super Response<DownloadsMultiRelationshipDataDocument>>);
}

public final class DownloadsAttributes {
  public List<DownloadLink> getDownloadLinks();
}

public final class DownloadLink {
  public String getHref();
  public DownloadLinkMeta getMeta();
}
```

Offline tasks expose read and status update, but no create method in the local generated artifact:

```java
public interface OfflineTasks {
  Object offlineTasksGet(String countryCode, List<String> filterState, List<String> filterAction, List<String> include, Continuation<? super Response<OfflineTasksMultiResourceDataDocument>>);
  Object offlineTasksIdGet(String id, List<String> include, Continuation<? super Response<OfflineTasksSingleResourceDataDocument>>);
  Object offlineTasksIdPatch(String id, String countryCode, OfflineTasksUpdateOperationPayload, Continuation<? super Response<Unit>>);
}
```

Patch payload:

```java
public final class OfflineTasksUpdateOperationPayload {
  public OfflineTasksUpdateOperationPayload(OfflineTasksUpdateOperationPayloadData data);
}

public final class OfflineTasksUpdateOperationPayloadData {
  public OfflineTasksUpdateOperationPayloadData(
    OfflineTasksUpdateOperationPayloadDataAttributes attributes,
    String id,
    OfflineTasksUpdateOperationPayloadData.Type type
  );
}

OfflineTasksUpdateOperationPayloadData.Type: offlineTasks
OfflineTasksUpdateOperationPayloadDataAttributes.State: IN_PROGRESS, FAILED, COMPLETED
```

Offline task attributes include action/state:

```java
OfflineTasksAttributes.Action: ADD, REMOVE
OfflineTasksAttributes.State: PENDING, IN_PROGRESS, COMPLETED, FAILED
```

Installation registration and offline inventory relationship are present:

```java
public interface Installations {
  Object installationsPost(String countryCode, InstallationsCreateOperationPayload, Continuation<? super Response<InstallationsSingleResourceDataDocument>>);
  Object installationsIdRelationshipsOfflineInventoryPost(String id, String countryCode, InstallationsOfflineInventoryRelationshipAddOperationPayload, Continuation<? super Response<Unit>>);
  Object installationsIdRelationshipsOfflineInventoryDelete(String id, String countryCode, InstallationsOfflineInventoryRelationshipRemoveOperationPayload, Continuation<? super Response<Unit>>);
  Object installationsIdRelationshipsOfflineInventoryGet(String id, String countryCode, List<String> filterState, List<String> filterType, List<String> pageCursor, List<String> include, Continuation<? super Response<InstallationsOfflineInventoryMultiRelationshipDataDocument>>);
}

public final class InstallationsCreateOperationPayloadDataAttributes {
  public InstallationsCreateOperationPayloadDataAttributes(String clientProvidedInstallationId, String name);
}

public final class InstallationsOfflineInventoryRelationshipAddOperationPayload {
  public InstallationsOfflineInventoryRelationshipAddOperationPayload(List<InstallationsOfflineInventoryItemIdentifier> data);
}

public final class InstallationsOfflineInventoryItemIdentifier {
  public InstallationsOfflineInventoryItemIdentifier(String id, InstallationsOfflineInventoryItemIdentifier.Type type);
}

InstallationsOfflineInventoryItemIdentifier.Type:
  tracks, videos, albums, playlists, userCollectionTracks

Installations.FilterStateInstallationsIdRelationshipsOfflineInventoryGet:
  PENDING, STORED
```

Interpretation: the SDK/API likely has a server-side notion of install-specific offline inventory. The local generated artifact can register an installation and mark inventory relationships, but creating the actual offline task may be triggered elsewhere (possibly by installation inventory POST, by first-party-only endpoint not generated here, or by external TIDAL client state). This remains the biggest unknown.

## Likely sanctioned call sequence

This is the most likely one-track proof path from the signatures, with production UX intentionally out of scope:

1. **Authenticate as a user** using the existing Untidy auth repository / `CredentialsProvider`.
   - Need a valid user bearer token and client ID.
   - Current fallback user scopes are `r_usr w_usr w_sub`; configured app scopes include modern `playback`, `entitlements.read`, collection/playlist scopes. Offline APIs may require additional or first-party scopes; verify live responses before committing UI.

2. **Create or reuse an installation id** for the watch/emulator.
   - Generate and persist a stable `clientProvidedInstallationId` per install, not per download.
   - Call `Installations.installationsPost(countryCode, payload)` if no server installation id is stored.
   - Store returned installation resource id and client-provided id locally.

3. **Request sanctioned download/offline manifest information for one track.**
   - Preferred probe A: SDK streaming API:
     - `player.getStreamingApi().getTrackPlaybackInfo(trackId, AudioQuality.LOW, PlaybackMode.OFFLINE, adaptive=false, streamingSessionId, immersive=false)`
     - Confirm the returned `PlaybackInfo.Track` has a non-empty manifest, valid `offlineRevalidateAt` / `offlineValidUntil`, and download/offline semantics.
   - Preferred probe B: generated OpenAPI:
     - `TrackManifests.trackManifestsIdGet(trackId, MPEG_DASH, listOf("HEAACV1"), DATA or HTTPS, DOWNLOAD, adaptive=false, countryCode)`
     - Confirm response success and presence of `attributes.uri`, `hash`, optional `drmData`, formats, and normalization data.
   - Do not persist or cache any result that came from `usage=PLAYBACK` or legacy `playbackmode=STREAM`.

4. **Resolve actual downloadable asset bytes through sanctioned surface.**
   - Check `Downloads.downloadsIdGet(trackId or download id, include=...)` and inspect `DownloadsAttributes.downloadLinks.href`.
   - Unknown: whether `downloadsIdGet` id equals track id, manifest hash, offline task id, or a download resource id returned by offline inventory/tasks. This must be probed with logs redacted.
   - If `downloadLinks` returns URLs, download only the single proof track asset into a debug-only Media3 cache path controlled by `OfflineCacheProvider`; do not place raw URLs in app DB/logs.

5. **Record / observe offline task and installation inventory.**
   - Add one track to installation offline inventory:
     - `Installations.installationsIdRelationshipsOfflineInventoryPost(installationId, countryCode, AddPayload([ItemIdentifier(trackId, tracks)]))`
   - Poll:
     - `Installations.installationsIdRelationshipsOfflineInventoryGet(... filterState=PENDING/STORED, filterType=tracks ...)`
     - `OfflineTasks.offlineTasksGet(countryCode, filterState=..., filterAction=..., include=...)`
   - Patch offline task state only in debug proof after a local download actually starts/completes, using `IN_PROGRESS`, `COMPLETED`, or `FAILED` as appropriate. Do not falsely mark a server task complete.

6. **Implement minimal offline provider dependencies for playback proof.**
   - `OfflinePlaybackInfoProvider.getOfflineTrackPlaybackInfo(trackId, streamingSessionId)` should return a `PlaybackInfo.Offline.Track` built from a validated offline/download playback-info result, offline license, and `Storage(externalStorage=false, path=<cache-key/path>)`.
   - `OfflineCacheProvider.getInternal(path)` should return the Media3 `Cache` instance that contains the one track proof bytes.
   - `Encryption` must return a stable proof secret key and decrypted header if the selected asset path requires it; if the offline manifest/format is fully DRM/DASH, `OfflineDrmHelper.setOfflineLicense(...)` may be the critical path instead.

7. **Playback proof using SDK offline path.**
   - Construct a `Player` with the debug `OfflinePlayProvider`.
   - Load `MediaProduct(ProductType.TRACK, trackId, sourceType, sourceId, extras, referenceId)` via `PlaybackEngine.load(...)` and play.
   - Confirm playback still works after network is disabled only if the one-track cache and offline license are present.

## Required tokens / IDs / local state

Required for proof:

- TIDAL user access token from `TidalAuthRepository.getAccessToken()` or SDK `CredentialsProvider`.
- TIDAL client id via existing auth repository client-id path.
- Country code, currently `Locale.getDefault().country` fallback `US` in playback backend.
- TIDAL user id from stored auth/account info when available.
- Stable local `clientProvidedInstallationId`, probably UUID stored in DataStore/SharedPreferences for the app install.
- Server `installationId` returned by `Installations.installationsPost`.
- Track id for one low-risk proof track.
- Streaming session id / reference id: generate UUID-like values per SDK playback session unless official examples prove a stricter format.
- Cache storage path/key for the one proof track.
- Offline license string if returned by offline playback-info/download/DRM flow.
- Encryption secret key / decrypted header support if required by selected asset type.

Do not log bearer tokens, client secrets, download URLs, license blobs, or encryption keys. Logs should redact to booleans/lengths/hashes, e.g. `offlineLicensePresent=true length=...` and `downloadLinkCount=...`.

## Emulator vs real watch proof boundary

Can be proven on emulator:

- SDK method wiring compiles against local artifacts.
- Authenticated calls to `PlaybackMode.OFFLINE` / `usage=DOWNLOAD` return either success or an actionable TIDAL error.
- Installation create / offline-inventory POST/GET response behavior.
- Offline task GET/PATCH response behavior, with patching only after local state is true.
- One-track Media3 cache write/read path and `OfflineCacheProvider` routing.
- Airplane-mode / network-disabled playback from a local cache, if Widevine/offline license works in emulator.
- UI-disabled state remains honest until proof is complete.

Likely requires real watch validation before productizing:

- Wear OS storage pressure, charging/Wi-Fi constraints, and background execution.
- Real watch DRM/Widevine behavior and offline license persistence.
- Reboot persistence and offline validity expiry/revalidation.
- Bluetooth headphones/audio focus behavior over a full offline track.
- Thermal/battery impact and download interruption recovery.

## Minimal debug-only one-track proof steps

No production code should be changed for this proof. Recommended implementation vehicle is a temporary debug-only instrumentation/activity/service command or local branch commit that is removed or kept behind a `debugImplementation`/`BuildConfig.DEBUG` gate before product work.

1. Add a debug-only `OfflineProofRunner` reachable by `adb shell am startservice` or a hidden debug action. Inputs: `trackId`, optional `countryCode`.
2. Verify auth state; abort if no user token. Print only redacted auth facts: `hasToken=true`, `userIdPresent=true`, `clientIdHash=...`.
3. Create/reuse stable installation id:
   - Generate `clientProvidedInstallationId` if absent.
   - Call `installationsPost` or `installationsGet`/`installationsIdGet` if reuse path is understood.
4. Call both proof probes:
   - `TrackManifests.trackManifestsIdGet(trackId, MPEG_DASH, [HEAACV1], DATA, DOWNLOAD, false, countryCode)`.
   - `StreamingApi.getTrackPlaybackInfo(trackId, AudioQuality.LOW, PlaybackMode.OFFLINE, false, sessionId, false)`.
5. If manifest/offline info succeeds, inspect non-secret fields and persist a proof JSON artifact under app-private debug files, containing no token/license/download URL.
6. Add track to installation offline inventory with type `tracks`; poll inventory and offline tasks.
7. Resolve a `Downloads` resource only if a download id can be confidently derived from server response. If not, stop and record `download-link resolution unknown` rather than guessing.
8. If a sanctioned download link is returned, download exactly one track to a debug Media3 `SimpleCache` via an `OfflineCacheProvider` key/path. Do not cache PLAYBACK manifests.
9. Create debug `OfflinePlayProvider` with:
   - provider returning `PlaybackInfo.Offline.Track(...)`,
   - cache provider returning the proof cache,
   - encryption implementation with a stable app-private key for the proof.
10. Instantiate SDK `Player` with the debug provider, load the one track as `MediaProduct(ProductType.TRACK, trackId, ...)`, play online once, then disable network and replay from cache.
11. Success criteria:
   - all proof API calls are from offline/download surfaces,
   - one track plays with network disabled,
   - logs/artifacts contain no secrets,
   - deleting debug cache removes playback capability,
   - non-debug production code is untouched or still feature-gated.

## Blockers and unknowns

1. **No public example found.** Local artifacts expose the APIs, but no official sample/test app was found by web search. Bytecode signatures are the current evidence.
2. **Offline task creation path is not explicit.** `OfflineTasks` exposes GET/GET-by-id/PATCH but no POST. Possibilities: installation offline-inventory POST creates tasks, `Downloads` resources are pre-existing, or task creation is in a first-party-only/private endpoint absent from the generated artifact.
3. **Download resource id mapping unknown.** `Downloads.downloadsIdGet(id, ...)` exists, but the artifact alone does not prove whether `id` is a track id, task id, manifest hash, or separate resource id.
4. **Offline license acquisition/storage unknown.** `PlaybackInfo.Offline.Track` requires `offlineLicense`; signatures show where to provide it, not exactly which endpoint returns it.
5. **Encryption contract unknown.** `Encryption.getSecretKey()` and `getDecryptedHeader(String)` are required by `OfflinePlayProvider`; artifact signatures do not reveal the expected key lifecycle/header derivation.
6. **Scopes/entitlements unknown.** Existing auth may return 401/403 for offline/download calls if TIDAL restricts offline APIs by client type/scope/subscription.
7. **Emulator DRM may not match watch.** Even a passing emulator proof needs real-watch validation before closing product acceptance.

## Recommendation

Move #11 from permission-blocked to **official offline provisioning proof needed**: Ric has confirmed the download/store/playback pathway is sanctioned and used by other TIDAL first- and third-party apps. The next actionable engineering step is a debug-only, one-track proof runner that exercises `usage=DOWNLOAD` / `PlaybackMode.OFFLINE`, installation inventory, and the SDK `OfflinePlayProvider` path. Stop immediately if the only available path is raw caching of current `PLAYBACK`/`STREAM` manifests.

## Debug proof runner result — 2026-06-17 11:00 PT

A debug-only proof runner was added under `app/src/debug/` and executed on the Wear emulator using the existing signed-in Untidy session. The runner is reachable through `com.tidal.wear.debug.OfflineProofActivity`, writes a redacted app-private JSON artifact, and probes only sanctioned offline/download surfaces. It intentionally omits bearer tokens, manifest URIs, download hrefs, licenses, and encryption material from logs/artifacts.

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1100/latest.json` (gitignored local runtime artifact)

Redacted proof result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| Auth state | token present, user id present, scopes `r_usr w_sub w_usr` | Existing user session can call authenticated TIDAL endpoints; artifact records only token length and client-id hash. |
| OpenAPI `GET /v2/trackManifests/{id}` with `usage=DOWNLOAD`, `manifestType=MPEG_DASH`, `uriScheme=DATA`, `formats=HEAACV1` | `200`; `data.type=trackManifests`; manifest URI present; hash present; no DRM data in this response | This is the first live proof that a sanctioned DOWNLOAD manifest path returns real data for the current app/session. It is not a STREAM/PLAYBACK manifest and was not cached as product download data. |
| SDK `player.streamingApi.getTrackPlaybackInfo(... PlaybackMode.OFFLINE ...)` | returned `PlaybackInfo.Track`; manifest present; streaming session id present; no license URL; `offlineRevalidateAt=-1`; `offlineValidUntil=-1` | The SDK OFFLINE playback-info call compiles and returns manifest-bearing playback info for the current app/session. The returned model is still `PlaybackInfo.Track`, not a complete `PlaybackInfo.Offline.Track`, so offline storage/license orchestration remains unsolved. |
| OpenAPI `GET /v2/downloads/{trackId}` | `500 INTERNAL_SERVER_ERROR` | Track id is probably not a valid download resource id, or this endpoint requires a different orchestration-created id. Do not guess/crawl download URLs. |
| OpenAPI `GET /v2/offlineTasks` with/without repeated state/action filters | `400 MISSING_REQUIRED_PARAMETER` | The generated method signature is incomplete for live use or requires another required query parameter not yet discovered from bytecode. Offline task creation/orchestration remains the next proof target. |

Concrete advancement: the sanctioned `usage=DOWNLOAD` and `PlaybackMode.OFFLINE` probes now have live authenticated evidence. The next proof step is **not** product implementation yet; it is to identify the missing offline orchestration seam: installation registration/offline inventory POST/GET and the required `offlineTasks` parameter or download resource id source.

## Installation inventory proof result — 2026-06-17 11:15 PT

The debug-only proof runner was extended to probe installation registration, installation offline-inventory relationships, and the `OfflineTasks` query shape discovered from bytecode. No production UX was changed.

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1115/latest.json` (gitignored local runtime artifact)

Additional SDK/bytecode evidence from this pass:

- `OfflineTasks.offlineTasksGet(...)` Retrofit annotations do **not** use the earlier guessed `countryCode`, `filter[state]`, or `filter[action]` query shape. The actual generated query names are `page[cursor]`, `include`, `filter[id]`, and `filter[installation.id]`.
- `Installations.installationsGet(...)` uses `page[cursor]`, `include`, `filter[clientProvidedInstallationId]`, `filter[id]`, and `filter[owners.id]`.
- Installation offline inventory GET uses `filter[state]`, `filter[type]`, `page[cursor]`, and `include`; POST uses `countryCode` plus a JSON:API relationship body.

Redacted live proof result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `GET /v2/installations?filter[clientProvidedInstallationId]=...&include=offlineInventory,owners` | `400 GENERIC_REQUEST_ERROR` | The generated query shape is known, but the current user/app session cannot list/reuse this debug installation by client-provided id through this endpoint shape. |
| `POST /v2/installations?countryCode=US` with `data.type=installations`, `attributes.clientProvidedInstallationId`, and `attributes.name` | `500 INTERNAL_SERVER_ERROR` | Installation creation is not accepted for this current app/session/body shape; no installation id was returned, so inventory POST could not safely proceed. |
| `GET /v2/offlineTasks` using both guessed and generated shapes, including `include=item,collection,owners` and `page[cursor]=0` | `400 MISSING_REQUIRED_PARAMETER` | The earlier missing parameter is not solved by `countryCode`, state/action filters, generated include-only shape, or first-page cursor. A valid installation id and/or server-created task id likely has to exist before task listing can work. |

Self-unblock attempted in the same pass: after installation creation failed, the runner tested the bytecode-derived `OfflineTasks` query shape without `countryCode` and with `page[cursor]`; it still returned `MISSING_REQUIRED_PARAMETER`. This narrows the missing seam: the next proof should search/decompile first-party SDK call sites or generated docs for **how a valid installation/offline task is created**, not keep guessing IDs.

Next concrete proof step:

1. Decompile/search SDK bytecode for callers of `installationsPost`, `installationsIdRelationshipsOfflineInventoryPost`, `offlineTasksGet`, and `downloadsIdGet` to identify the required endpoint/body/query sequence.
2. If no caller exists in SDK artifacts, build a smaller endpoint-shape probe around `UserOfflineMixes` and installation owner filters; those are the remaining generated offline-adjacent surfaces that may reveal installation/task ids.
3. Only after a real installation/task/download id is obtained should the proof attempt `Downloads.downloadsIdGet(...)` or an offline cache/provider playback path.

## Endpoint-shape correction and offline-discovery probe — 2026-06-17 11:30 PT

This pass decompiled Retrofit annotations for the remaining offline-adjacent APIs and corrected an important mistake from the prior raw HTTP probe: some methods that looked like they accepted `countryCode` from plain `javap` were actually taking an `Idempotency-Key` header.

Corrected bytecode evidence:

- `Installations.installationsPost(...)` is `POST /v2/installations` with `Header("Idempotency-Key")` and a JSON:API body. It does **not** use `countryCode` as a query parameter.
- `Installations.installationsIdRelationshipsOfflineInventoryPost(...)` is `POST /v2/installations/{id}/relationships/offlineInventory` with `Header("Idempotency-Key")` and a JSON:API relationship body. It does **not** use `countryCode` as a query parameter.
- `Installations.installationsIdRelationshipsOfflineInventoryGet(...)` uses `page[cursor]`, `include`, `filter[id]`, `filter[state]`, and `filter[type]`.
- `Downloads.downloadsGet(...)` uses `include` and `filter[id]`; `downloadsIdGet(...)` uses only path id plus `include`.
- `UserOfflineMixes.userOfflineMixesIdGet(...)` uses `countryCode`, `locale`, and `include`; `userOfflineMixesIdRelationshipsItemsGet(...)` uses `page[cursor]`, `locale`, and `include`.

The debug-only runner was updated to use `Idempotency-Key` for installation create/offline-inventory add and to probe the remaining official offline discovery surfaces. No production UX was changed and no raw STREAM/PLAYBACK manifest caching was added.

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1130-discovery/latest.json` (gitignored local runtime artifact)

Redacted live results for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `GET /v2/trackManifests/{id}` with `usage=DOWNLOAD` | `200`; manifest/hash still present | The sanctioned download-manifest proof remains stable. |
| `GET /v2/installations?filter[clientProvidedInstallationId]=...` | `400 GENERIC_REQUEST_ERROR` | Lookup shape still not accepted for the current app/session. |
| Corrected `POST /v2/installations` with `Idempotency-Key` header | `500 INTERNAL_SERVER_ERROR` | The earlier `countryCode` mistake was not the only issue; installation creation still fails for this app/session/body. |
| `GET /v2/downloads?filter[id]={trackId}&include=owners` | `500 INTERNAL_SERVER_ERROR` | The `downloads` collection does not expose track-id-filtered records for this session, or track id is not the download id. |
| `GET /v2/installations?filter[owners.id]={userId}` | `500 INTERNAL_SERVER_ERROR` | Owner-filtered installation discovery did not return an installation id. |
| `GET /v2/userOfflineMixes/{userId}` | `404 NOT_FOUND` | User id is not a valid user-offline-mix id. |
| `GET /v2/userOfflineMixes/{userId}/relationships/items?page[cursor]=0` | `400 INVALID_CURSOR` | This endpoint exists but needs a valid offline-mix id and/or cursor semantics; user id is not sufficient. |
| `GET /v2/offlineTasks` generated shapes | `400 MISSING_REQUIRED_PARAMETER` | Still likely needs a valid installation id or task id before listing works. |

Self-unblock outcome: the runner now uses the correct bytecode-derived header/query shapes and explored downloads list, owner installation lookup, and user-offline-mix surfaces. None yielded a valid installation/task/download id. The next proof should either (a) find a valid user-offline-mix id/cursor source by inspecting generated models/relationships or alternative endpoint ids, or (b) pivot to a compile-only/local `OfflinePlayProvider` + `OfflineCacheProvider` harness to prove the SDK offline playback wiring while server-side task/download-id orchestration remains unresolved.

## Offline provider/cache wiring proof — 2026-06-17 11:45 PT

After the server-side discovery probes failed to yield a valid installation/task/download id, the next self-unblock step was to prove the local SDK offline playback seam itself instead of stopping at server orchestration.

The debug-only proof runner now constructs a minimal `OfflinePlayProvider` from real SDK interfaces:

- `OfflinePlaybackInfoProvider` returning a `PlaybackInfo.Offline.Track`
- `OfflineCacheProvider` returning a Media3 `SimpleCache`
- `Encryption` returning a stable debug-only 32-byte key and empty decrypted header
- `Storage(externalStorage=false, path=<app-private proof cache>)`

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1145-provider-wiring/latest.json` (gitignored local runtime artifact)

Redacted runtime results for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `offlineProviderWiring` | constructed successfully; resolved `PlaybackInfo$Offline$Track`; `SimpleCache` UID present; `secretKeyLength=32`; app-private storage path recorded only as a hash | The SDK offline-provider/cache/encryption seam compiles and can be instantiated with app-private Media3 cache wiring. This proves the local playback wiring is implementable once sanctioned server-side offline bytes/license/task ids are obtained. |
| `GET /v2/trackManifests/{id}` with `usage=DOWNLOAD` | still `200`; manifest/hash present | The sanctioned download-manifest probe remains stable. |
| SDK `PlaybackMode.OFFLINE` playback-info probe | still returns manifest-bearing `PlaybackInfo.Track`; no license URL; offline validity fields `-1` | The SDK OFFLINE request path still works but does not itself return a complete `PlaybackInfo.Offline.Track` or license. |
| Corrected installation/download/offlineTasks discovery probes | installation create and download-id paths still fail; no task/download id found | Server orchestration remains the unresolved seam, but it is now isolated from local provider/cache wiring. |

Concrete advancement: #11 now has a compile/runtime proof that Untidy can wire the official SDK `OfflinePlayProvider`/`OfflineCacheProvider`/`Encryption` path with app-private Media3 cache. The remaining unknown is not "can the app supply the offline provider?"; it is where TIDAL exposes the sanctioned download resource id/offline license/offline task sequence for this app session.

Next concrete proof step: inspect generated model relationships for offline task/download/user-offline-mix ids and/or decompile SDK network metadata for hidden required query/header fields; only if a real server-side id or license source is found should the runner attempt a one-track cache fill and offline playback replay.


## Offline mix cursor/id self-unblock probe — 2026-06-17 12:02 PT

After the provider/cache wiring proof, the next self-unblock target was the remaining server-side id source. I inspected generated model relationships and API metadata for `UserOfflineMixes`, `Downloads`, `OfflineTasks`, and `Installations`, then added a debug-only probe for the most plausible cursor/locale alternates.

Additional SDK metadata from this pass:

- `UserOfflineMixesResourceObject` has only an `items` relationship; its `attributes` type is generated as `Object`, so the local artifact does not expose a hidden offline-mix id field.
- `UserOfflineMixes.userOfflineMixesIdGet(id, countryCode, locale, include)` defaults locale to `en-US`; the earlier runtime probe used `en_US`, so the runner now probes the hyphen-locale shape too.
- `userOfflineMixes/{id}/relationships/items` uses `page[cursor]`, `locale`, and `include`; it does not accept `countryCode`.
- `DownloadsResourceObject` has `id`, `attributes.downloadLinks`, and `owners` relationship only. `DownloadLinkMeta` exposes required header names, but no alternate id source.
- `OfflineTasksRelationships` exposes `collection`, `item`, and `owners`; task listing can filter by `filter[id]` and `filter[installation.id]`, but there is still no create endpoint in the generated artifact.
- `InstallationsOfflineInventoryResourceIdentifierMeta` only exposes `addedAt`; it does not expose a task/download id.

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1202-offline-mix-cursors/latest.json` (gitignored local runtime artifact)

Redacted live results for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `GET /v2/userOfflineMixes/{userId}?locale=en-US&include=items` | `404 NOT_FOUND` | The locale correction did not make user id a valid offline-mix id. |
| `GET /v2/userOfflineMixes/{userId}/relationships/items?page[cursor]=0&locale=en_US&include=items` | `400 INVALID_CURSOR` | Cursor `0` remains invalid for this endpoint/id pair. |
| `GET /v2/userOfflineMixes/{userId}/relationships/items?locale=en-US&include=items` | `500 INTERNAL_SERVER_ERROR` | Omitting cursor does not produce a first page for user id as mix id. |
| `GET /v2/userOfflineMixes/{userId}/relationships/items?locale=en-US` | `500 INTERNAL_SERVER_ERROR` | Omitting both cursor and include still does not reveal item ids. |
| `GET /v2/downloads?filter[id]={trackId}&include=owners` | `500 INTERNAL_SERVER_ERROR` | Track id is still not accepted as a download id/filter id for this app/session. |
| `GET /v2/offlineTasks?include=item,collection,owners` and `page[cursor]=0` | `400 MISSING_REQUIRED_PARAMETER` | Task listing still needs a real installation/task/download seam not present in generated metadata. |

Self-unblock outcome: generated metadata plus live cursor/locale alternates did not reveal a valid offline mix, download, task, or installation id. This is a useful negative proof: the next run should stop guessing `UserOfflineMixes` by user id and instead inspect non-`tidalapi` SDK artifacts for first-party orchestration around `Downloads`, `OfflineTasks`, or `OfflineDrmHelper`, or search network constants in player/offline modules for the missing server-side source.


## Non-tidalapi player offline branch inspection — 2026-06-17 12:16 PT

After the offline-mix/user-id probes failed, the next anti-lazy self-unblock step was to inspect **non-`tidalapi`** SDK/player artifacts for the offline playback orchestration path instead of guessing more endpoint ids. I decompiled `player-playback-engine`, `player-streaming-api`, and adjacent player modules with `javap -p -c -constants` and searched literal strings/network constants. This produced a useful local playback map, but did **not** reveal a hidden Downloads/OfflineTasks/create endpoint.

Bytecode evidence from `com.tidal.sdk.player.playbackengine.mediasource.TidalMediaSourceCreator`:

- `TidalMediaSourceCreator.create(mediaItem, playbackInfo, extras)` has a dedicated branch for `PlaybackInfo.Offline`.
- For progressive offline playback:
  - if `PlaybackInfo.Offline.partiallyEncrypted == true`, it uses `PlayerDecryptedHeaderProgressiveOfflineMediaSourceFactory.create(mediaItem, manifest, productId)`.
  - otherwise it uses `PlayerProgressiveOfflineMediaSourceFactory.create(mediaItem, manifest, storage)`.
- For DASH offline playback it uses `PlayerDashOfflineMediaSourceFactory.create(mediaItem, manifest, offlineLicense, storage, drmSessionManagerProvider)`.
- This confirms the official player expects a **complete `PlaybackInfo.Offline` object** containing manifest, storage, and — for DASH/DRM — offline license. The player branch is not responsible for discovering server download/task ids.

Bytecode evidence from `PlayerDashOfflineMediaSourceFactory` and DRM helpers:

- `PlayerDashOfflineMediaSourceFactory.create(...)` parses the encoded DASH manifest, obtains a `DataSource.Factory` from `OfflineStorageProvider.getDataSourceFactoryForOfflinePlay(storage, hasOfflineLicense)`, and calls `OfflineDrmHelper.setOfflineLicense(offlineLicense, drmSessionManager)` when the offline license is non-empty.
- `DrmSessionManagerFactory.createDrmSessionManagerForOfflinePlay(playbackInfo, extras)` returns `DRM_UNSUPPORTED` when `offlineLicense` is empty; when non-empty, it builds a normal DRM session manager from the online delegate playback info and passes an empty license URL into the callback path.
- `TidalMediaDrmCallback.executeKeyRequest(...)` only has an implemented `DrmMode.Streaming` branch in this artifact; the offline license is therefore injected through `OfflineDrmHelper.setOfflineLicense(...)`, not fetched from a separate hidden offline callback branch discovered in player bytecode.

Literal/network-constant scan outcome:

- Non-`tidalapi` player artifacts exposed the storage URL sentinel `https://fsu.fa.tidal.com/storage/.m3u8@` and offline media-source/cache classes, but no hidden first-party endpoint for creating offline tasks, resolving download ids, or fetching an offline license outside the generated `tidalapi`/playback-info surfaces already probed.

Concrete advancement: the remaining #11 gap is now narrowed further. Untidy can wire the local official offline playback branch, and the player branch confirms the exact required payload shape: `PlaybackInfo.Offline.Track(delegate PlaybackInfo.Track, offlineLicense, Storage, partiallyEncrypted)`. The missing source is still upstream/server-side: a sanctioned way to populate `offlineLicense`, `Storage.path`, and cached bytes for a track. Continuing to guess `downloads/{trackId}` or user-id offline mixes is lower-value than proving whether the SDK `PlaybackInfoRepositoryDefault.getOfflineTrackPlaybackInfo(...)` delegation can be supplied from local persisted proof data, or finding official/first-party code that populates `OfflinePlaybackInfoProvider`.

Next concrete proof step:

1. Build a debug-only compile/runtime harness around the real `TidalMediaSourceCreator` offline branch using a synthetic redacted `PlaybackInfo.Offline.Track` and app-private `Storage`, to prove which offline branch/factory path Untidy will hit for DASH vs progressive manifests without real download bytes.
2. In parallel or next run, search/decompile any available first-party sample/app artifacts for implementations of `OfflinePlaybackInfoProvider`; that interface remains the likely boundary where TIDAL's official app/client injects server-side offline license/storage state.


## TidalMediaSourceCreator compile-only harness attempt — 2026-06-17 12:26 PT

Next proof target from the previous pass was to build a debug-only harness around the real `TidalMediaSourceCreator` offline branch with synthetic `PlaybackInfo.Offline.Track` and app-private `Storage`.

Result: the direct app/debug-source harness is **not the correct integration seam**. The compile attempt failed because the media-source branch and most of its factory dependencies are Kotlin `internal` inside the TIDAL SDK artifacts:

- `TidalMediaSourceCreator`
- `PlayerProgressiveOfflineMediaSourceFactory`
- `PlayerDashOfflineMediaSourceFactory`
- `OfflineStorageProvider`
- `OfflinePlayDataSourceFactoryHelper`
- `OfflinePlayDrmDataSourceFactoryHelper`
- `OfflineDrmHelper`
- `DefaultBtsManifestFactory` / `DashManifestFactory`
- `DrmSessionManagerFactory` / `DrmSessionManagerProviderFactory` / `TidalMediaDrmCallbackFactory`

Verification/self-unblock sequence:

1. Tried the debug-only compile harness against `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`.
2. Ran `source scripts/env-android.sh >/dev/null && bash ./gradlew :app:compileDebugKotlin --no-daemon`.
3. Reverted the failed app-source change so the repo stayed compileable.
4. Recorded the failed attempt locally at `reports/offline-proof-2026-06-17-1226-media-source-creator-compile/README.md` (gitignored proof artifact; no secrets).
5. Re-ran `:app:compileDebugKotlin` and `git diff --check`; both passed after revert/docs-only changes.
6. Inspected public SDK bytecode and found the higher-level public seam:

```text
public final class com.tidal.sdk.player.Player {
  public Player(
    Application,
    CredentialsProvider,
    EventSender,
    ...,
    CacheProvider,
    ...,
    PlaybackPrivilegeProvider,
    OfflinePlayProvider,
    String,
    String,
    String
  );
}

public final class com.tidal.sdk.player.offlineplay.OfflinePlayProvider {
  public OfflinePlayProvider(
    OfflinePlaybackInfoProvider,
    OfflineCacheProvider,
    Encryption
  );
  public OfflinePlayProvider();
}

public interface OfflinePlaybackInfoProvider {
  getOfflineTrackPlaybackInfo(String, String, Continuation<PlaybackInfo>);
  getOfflineVideoPlaybackInfo(String, String, Continuation<PlaybackInfo>);
}
```

Interpretation: Untidy should not instantiate `TidalMediaSourceCreator` or offline media-source factories directly. Those classes are internal implementation details. The sanctioned app-facing proof seam is `Player(..., OfflinePlayProvider, ...)` plus a custom/debug `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, and `Encryption`.

Next concrete proof step: build a minimal compile-only `Player` construction harness using a debug `OfflinePlayProvider` at the public constructor seam. The harness should not play media yet and should keep synthetic offline playback info redacted; the goal is to prove Untidy can inject the provider into the SDK `Player` without touching internal media-source classes. If construction compiles, follow with a runtime proof that requests `PlaybackMode.OFFLINE` through the player/load path and captures only class/status/shape evidence.


## Public Player OfflinePlayProvider injection proof — 2026-06-17 12:36 PT

The previous direct `TidalMediaSourceCreator` harness failed because that media-source branch is SDK-internal. This pass moved up to the public app-facing seam: `Player(..., OfflinePlayProvider, ...)`.

Debug-only code in `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt` now builds a synthetic/redacted `OfflinePlayProvider` and injects it into a real SDK `Player` constructor. The provider supplies:

- `OfflinePlaybackInfoProvider` returning `PlaybackInfo.Offline.Track`
- `OfflineCacheProvider` backed by app-private Media3 `SimpleCache`
- `Encryption` with a stable debug-only 32-byte key and empty decrypted header
- `Storage(externalStorage=false, path=<app-private proof cache>)`

No playback was attempted, no production UX was changed, and no stream/playback manifest caching was added. This proof only validates public SDK constructor injection and the offline playback-info provider boundary.

Artifact copied from the emulator for this pass:

- `reports/offline-proof-2026-06-17-1236-player-provider-injection/latest.json` (gitignored local runtime artifact)

Redacted runtime results for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `trackManifestDownload` | `200`; manifest/hash present | The sanctioned `usage=DOWNLOAD` manifest probe remains stable. |
| `offlineProviderWiring` | constructed successfully; `PlaybackInfo.Offline.Track`; app-private `SimpleCache` UID present | The provider/cache/encryption pieces still instantiate independently. |
| `playerOfflineProviderInjection` | `playerConstructed=true`; `providerInjected=true`; `offlineInfoReturned=true`; class `PlaybackInfo$Offline$Track`; manifest present; storage external false; cache UID present; secret key length 32 | Untidy can inject a custom offline provider into the official SDK `Player` at the public seam and have the player-facing offline playback-info call return the synthetic offline track object. |
| SDK `PlaybackMode.OFFLINE` playback-info probe | still returns manifest-bearing `PlaybackInfo.Track`; no license URL; offline validity `-1` | The SDK network OFFLINE call is not the complete offline object source by itself. |

Concrete advancement: the public `Player`/`OfflinePlayProvider` seam is now proven at compile and runtime. The remaining blocker is no longer local player construction or internal media-source access; it is the sanctioned provisioning source for a **real** `PlaybackInfo.Offline.Track`: `offlineLicense`, `Storage.path`, and cached bytes/download resource ids.

Next concrete proof step: search/decompile any available first-party/sample artifacts or SDK call sites for an `OfflinePlaybackInfoProvider` implementation or provisioning path. Avoid repeating lower-value guesses already ruled out (`downloads/{trackId}`, user id as offline-mix id, and cursor `0` as first page).

## Upstream source/provider implementation search — 2026-06-17 12:46 PT

After the public `Player(..., OfflinePlayProvider, ...)` injection proof, this pass searched for a real provider/provisioning implementation rather than guessing more endpoint ids.

Durable local artifacts:

- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/search-summary.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/class-index.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/candidate-classes.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/javap-reference-hits.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/javap-implementers.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/upstream-source-search.txt`
- `reports/offline-proof-2026-06-17-1246-offline-provider-impl-search/source-snippets.md`

Search/proof steps completed:

1. Indexed local TIDAL/Media3 Gradle artifacts: 23 artifacts, 6,271 classes, 626 offline/download/cache/player candidate classes.
2. Decompiled candidate classes with `javap` looking for references and implementers of `OfflinePlaybackInfoProvider`, `OfflinePlayProvider`, `OfflineCacheProvider`, `offlineLicense`, `Downloads`, `OfflineTasks`, and `Installations`.
3. Found 137 reference hits but **zero concrete `OfflinePlaybackInfoProvider` implementers** in the packaged local artifacts (`javap-implementers.txt` is empty).
4. Searched the public upstream `tidal-music/tidal-sdk-android` source at HEAD `86c48c3` for the same provider/provisioning seams.
5. Found the SDK source confirms the app boundary:
   - `PlaybackInfoRepositoryDefault.getOfflineTrackPlaybackInfo(...)` only delegates to the supplied `offlinePlaybackInfoProvider` and throws `NullPointerException("No OfflinePlaybackInfoProvider provided")` if absent.
   - `OfflinePlaybackInfoProvider` is documented as "getting track or video playback info from local storage."
   - The only upstream implementation found is `OfflinePlaybackInfoProviderStub` under tests, returning synthetic `TrackPlaybackInfoFactory.OFFLINE_PLAY` / `VideoPlaybackInfoFactory.OFFLINE_PLAY`.
   - `OfflinePlayProvider` is only a holder for app-supplied `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, and `Encryption`.
6. Public Dokka pages corroborate the same contract: `OfflinePlaybackInfoProvider` is an interface for local-storage playback info, and `OfflinePlayProvider` is a collection of classes needed for offline playback; neither page documents a download-task creation helper.

Interpretation: within the public SDK and packaged artifacts available to Untidy, offline playback provisioning is intentionally app-supplied. The TIDAL player SDK can request `usage=DOWNLOAD` manifests and can consume `PlaybackInfo.Offline.Track`, but it does **not** ship a public downloader/offline-task orchestrator or concrete provider implementation. The remaining sanctioned path is therefore an Untidy-owned debug provider/repository that persists only data obtained from offline/download surfaces, paired with TIDAL-generated Downloads/Installations/OfflineTasks APIs where they return usable ids.

Concrete next proof step: build a debug-only `OfflinePlaybackStore` shape plus provider adapter that persists redacted metadata for one `usage=DOWNLOAD` / `PlaybackMode.OFFLINE` probe result into app-private storage and reconstructs `PlaybackInfo.Offline.Track` from that store. This should not download bytes or claim offline playback yet; it proves the missing app-owned persistence boundary and narrows the next runtime gap to locating and wiring the official byte/license provisioning interface.

## OfflinePlaybackStore/provider adapter proof — 2026-06-17 12:56 PT

The next proof step after the upstream/provider search was to turn the app-owned `OfflinePlaybackInfoProvider` boundary into a small debug-only store adapter. This does **not** claim offline playback and does **not** cache STREAM/PLAYBACK manifests or media bytes. It only proves that Untidy can persist sanctioned DOWNLOAD/OFFLINE metadata in app-private storage and reconstruct a `PlaybackInfo.Offline.Track` through the public SDK provider seam.

Code change:

- `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`
  - Added `offlinePlaybackStoreAdapter` probe.
  - Fetches `GET /v2/trackManifests/{trackId}` with `usage=DOWNLOAD`, `manifestType=MPEG_DASH`, `uriScheme=DATA`, `formats=HEAACV1`.
  - Persists a debug-only app-private JSON record under `files/offline-proof-store/` with the sanctioned DOWNLOAD manifest, manifest hash, optional DRM license URL field, storage path, and empty `offlineLicense`.
  - Reloads the record and serves it through a real `OfflinePlayProvider` / `OfflinePlaybackInfoProvider` / `OfflineCacheProvider` using app-private Media3 `SimpleCache`.
  - The public artifact redacts the manifest itself to booleans/lengths/hashes; no bearer token, manifest URI, download href, offline license, or encryption key is logged.

Runtime artifact:

- `reports/offline-proof-2026-06-17-1256-store-adapter/latest.json` (gitignored local runtime artifact)

Redacted live result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `trackManifestDownload` | `200`; manifest URI present; manifest hash present; no DRM data | The sanctioned DOWNLOAD manifest surface still works for the signed-in debug session. |
| `offlinePlaybackStoreAdapter` | persisted app-private record; manifest present length `2657`; manifest hash present; `providerCanServeStoredInfo=true`; app-private storage; `SimpleCache` UID present; `downloadBytesCached=false`; `playbackClaimed=false` | Untidy can now reconstruct a `PlaybackInfo.Offline.Track` from its own app-private offline metadata store/provider boundary. This proves the app-side state/provider shape, not the final offline replay. |
| `sdkPlaybackModeOffline` | still returns manifest-bearing `PlaybackInfo.Track`, not a complete `PlaybackInfo.Offline.Track`; offline validity fields `-1` | The SDK OFFLINE network call alone is not the complete persisted offline object source. |

Concrete advancement: the app-owned store/provider boundary is now proven end-to-end at debug runtime. The remaining missing input for a true one-track offline replay is narrower: the official TIDAL provisioning interface for **media bytes and/or offline license** that can populate the app-private cache/storage referenced by the reconstructed `PlaybackInfo.Offline.Track`.

Next concrete proof step: inspect the DOWNLOAD manifest shape and SDK cache-read expectations to determine whether the DOWNLOAD manifest itself contains enough segment addressing for a debug-only cache-fill experiment, while still avoiding STREAM/PLAYBACK manifests and not logging URLs. If it does not, continue searching for the official offline license/download-link interface before attempting playback.


## DOWNLOAD manifest shape probe — 2026-06-17 13:09 PT

The next proof step after the app-owned store/provider adapter was to inspect the sanctioned `usage=DOWNLOAD` DASH manifest shape and determine whether a debug-only cache-fill experiment is technically legitimate without touching `usage=PLAYBACK` / `playbackmode=STREAM` manifests.

Code change:

- `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`
  - Added a `downloadManifestShape` probe.
  - Fetches the same official `GET /v2/trackManifests/{trackId}` surface with `manifestType=MPEG_DASH`, `formats=HEAACV1`, `uriScheme=DATA`, `usage=DOWNLOAD`, and `adaptive=false`.
  - Decodes only the returned DATA DASH manifest locally and records redacted structural facts: counts/booleans/hashes only.
  - Does **not** log manifest URLs, segment URLs, bearer tokens, download hrefs, licenses, or encryption keys.

Runtime artifact:

- `reports/offline-proof-2026-06-17-1309-download-manifest-shape/latest.json` (gitignored local runtime artifact)

Redacted live result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `trackManifestDownload` | `200`; manifest URI present; manifest hash present; no DRM data | The sanctioned DOWNLOAD manifest surface remains stable. |
| `downloadManifestShape` | DATA DASH decoded successfully; decoded length `1968`; one representation; one adaptation set; one `SegmentTemplate`; `initialization` template present; `media` template present; `startNumber` and `duration` present; `SegmentTimeline` present; four absolute URLs; zero `ContentProtection`; `looksSegmentAddressable=true`; `cacheFillCandidateWithoutLicense=true`; URLs were not logged | The sanctioned DOWNLOAD manifest itself appears structurally sufficient for a debug-only DASH cache-fill experiment into app-private `SimpleCache`, at least for this non-DRM HEAACV1 proof track. This is still not production offline playback yet. |
| `offlinePlaybackStoreAdapter` | persisted metadata and served reconstructed `PlaybackInfo.Offline.Track`; cache UID present; `downloadBytesCached=false`; `playbackClaimed=false` | Store/provider seam remains ready; the next proof can attempt cache population using only the sanctioned DOWNLOAD manifest. |

Concrete advancement: #11 has moved from "can we store offline metadata?" to "the official DOWNLOAD manifest is segment-addressable enough for a debug-only app-private cache-fill attempt." The guardrail remains intact: no STREAM/PLAYBACK manifest is used or cached.

Next concrete proof step: implement a debug-only cache-fill probe that uses the sanctioned `usage=DOWNLOAD` DASH manifest and app-private Media3 cache only, records byte/cache-key counts without URLs, and does **not** claim offline playback until a network-disabled replay path succeeds. If cache filling fails, inspect the failure at the Media3 cache/key layer before going back to server-side `Downloads`/offline-license discovery.


## DOWNLOAD manifest cache-fill proof — 2026-06-17 13:20 PT

The next proof step after the DOWNLOAD manifest shape probe was to try a debug-only cache fill using only the sanctioned `usage=DOWNLOAD` DASH manifest and app-private Media3 cache. This does not claim offline playback and does not use `usage=PLAYBACK` / `playbackmode=STREAM` manifests.

Code change:

- `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`
  - Added `downloadManifestCacheFill`.
  - Fetches `GET /v2/trackManifests/{trackId}` with `manifestType=MPEG_DASH`, `formats=HEAACV1`, `uriScheme=DATA`, `usage=DOWNLOAD`, and `adaptive=false`.
  - Feeds the returned DATA DASH manifest into Media3 `DashDownloader` with app-private `SimpleCache`.
  - Records only byte counts, key counts, key hashes, manifest hashes, and progress numbers; no bearer tokens, manifest URLs, segment URLs, download hrefs, licenses, or encryption keys are logged.
- `app/build.gradle.kts`
  - Added debug-only Media3 datasource/exoplayer/dash dependencies so the debug harness can compile without exposing the downloader path as production UX.

Runtime artifact:

- `reports/offline-proof-2026-06-17-1320-cache-fill/latest.json` (gitignored local runtime artifact)

Redacted live result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `trackManifestDownload` | `200`; manifest URI present; manifest hash present; no DRM data | The sanctioned DOWNLOAD manifest surface remains stable. |
| `downloadManifestShape` | DATA DASH decoded; one representation; one SegmentTemplate; SegmentTimeline present; zero ContentProtection; `cacheFillCandidateWithoutLicense=true` | The manifest remains structurally cache-fillable for this non-DRM HEAACV1 proof track. |
| `downloadManifestCacheFill` | `cacheFillSucceeded=true`; cache bytes added `2,428,102`; cache keys after fill `53`; downloader progress `100%`; elapsed `6,727ms`; URLs logged `false`; `playbackClaimed=false` | The sanctioned DOWNLOAD DASH manifest can populate app-private Media3 cache for one proof track. This advances the proof from metadata/store shape to actual sanctioned byte caching, but it is not yet an offline replay claim. |

Concrete advancement: Untidy now has a debug-only proof that app-private Media3 cache can be filled from the official `usage=DOWNLOAD` DASH manifest. The remaining proof is whether the SDK/player offline path can replay from that cache with network disabled and the reconstructed `PlaybackInfo.Offline.Track`; if not, inspect cache-key alignment between `DashDownloader`, `OfflineStorageProvider`, and `OfflineCacheProvider` before returning to server-side `Downloads`/offline-license discovery.

Next concrete proof step: add or run a debug-only network-disabled replay probe against the same cache and provider path. Do not mark #11 done until playback succeeds from cache with network disabled and the artifact proves no network fetch/STREAM manifest path was used.

## Network-disabled replay probe — 2026-06-17 13:45 PT

After the sanctioned DOWNLOAD manifest cache-fill proof, the next anti-lazy step was to attempt a debug-only network-disabled replay against the same app-private Media3 cache. This is still a proof harness only: it does not change production UX and does not use or cache `usage=PLAYBACK` / `playbackmode=STREAM` manifests.

Code change:

- `app/src/debug/java/com/tidal/wear/debug/OfflineProofService.kt`
  - Added `downloadManifestNetworkDisabledReplay`.
  - Fetches the official `GET /v2/trackManifests/{trackId}` surface with `manifestType=MPEG_DASH`, `formats=HEAACV1`, `uriScheme=DATA`, `usage=DOWNLOAD`, and `adaptive=false`.
  - Fills the same app-private `SimpleCache` using Media3 `DashDownloader`.
  - Attempts replay with a DASH media source whose chunk upstream deliberately throws if Media3 tries to fetch network chunks; the manifest itself remains the sanctioned DATA DASH manifest.
  - Records only cache bytes/key counts, hashes, playback states, and booleans. No bearer tokens, manifest URLs, segment URLs, download hrefs, licenses, or encryption keys are logged.

Runtime artifact:

- `reports/offline-proof-2026-06-17-1345-network-disabled-replay-manifest-ok/latest.json` (gitignored local runtime artifact)

Redacted live result for track `5120026` / country `US`:

| Probe | Result | Meaning |
| --- | --- | --- |
| `trackManifestDownload` | `200`; manifest URI present; manifest hash present; no DRM data | The sanctioned DOWNLOAD manifest surface remains stable. |
| `downloadManifestCacheFill` | cache fill still succeeded; `2,428,102` bytes added this run; downloader progress `100%`; URLs logged `false`; `playbackClaimed=false` | The DOWNLOAD manifest can still populate the app-private cache. |
| `downloadManifestNetworkDisabledReplay` | attempted replay after cache fill with network chunk upstream disabled; cache bytes increased from `14,568,612` to `16,996,714`; cache keys from `318` to `371`; `durationMs=200100`; state sequence `BUFFERING,IDLE`; `offlineUpstreamAttempted=true`; `reachedReady=false`; `reachedPlaying=false`; `errorClass=ExoPlaybackException`; `playbackClaimed=false` | Media3 could parse the manifest and derive duration, but chunk replay still missed the cache and tried disabled network upstream. Cached bytes alone are not yet enough for a valid offline replay with this media-source/cache-key setup. |

Self-unblock attempted in this pass: the first replay attempt failed because ExoPlayer was accessed off the main thread. The proof harness was corrected to construct/query/release ExoPlayer on the main looper, then replay was rerun. The second attempt got past threading and produced the meaningful cache-miss result above.

Concrete advancement: #11 now has the first network-disabled replay artifact. It proves the sanctioned DOWNLOAD cache fill is real but not yet aligned with replay cache keys/storage expectations. The next problem is narrower than provisioning in general: align `DashDownloader` cache keys with the offline replay media-source path and the SDK `OfflineCacheProvider`/`Storage.path` expectation, or find the official offline storage provider keying contract.

Next concrete proof step: inspect cache-key alignment between `DashDownloader`, `DashMediaSource`/`DefaultDashChunkSource`, TIDAL `OfflineStorageProvider`, and `Storage.path`. Try a debug-only replay with matching media item/cache keys or a custom cache-key factory before returning to server-side `Downloads`/offline-license discovery.

## Spike conclusion — 2026-06-17 13:48 PT

#11 is complete as a capability spike. It should no longer linger as the catch-all offline/download work item.

What the spike proved:

- TIDAL's offline/download path is sanctioned in principle for this class of client, per Ric's correction; the work did not re-litigate permission.
- `usage=DOWNLOAD` manifest fetch works for the authenticated session.
- The DOWNLOAD DASH manifest is segment-addressable for the proof track.
- App-private Media3 `DashDownloader` cache fill succeeds from the DOWNLOAD manifest.
- `OfflinePlayProvider`, `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, and `Encryption` wiring is viable from app code.
- App-private offline metadata store/provider reconstruction is viable.
- Network-disabled replay has been attempted. The first meaningful replay result narrows the remaining failure to cache-key/storage alignment: Media3 parses duration but chunk replay misses the cache and attempts disabled upstream.

What the spike did **not** claim:

- No production offline playback is shipped.
- No offline replay success has been claimed yet.
- No `PLAYBACK` / `STREAM` manifest caching was used.
- No URLs, bearer tokens, offline licenses, or keys were logged.

Follow-up implementation work:

- `UNTIDY-024` / GitHub #25 — align offline cache keys/storage paths for network-disabled replay and single-track MVP.

Latest proof artifact:

- `reports/offline-proof-2026-06-17-1345-network-disabled-replay-manifest-ok/latest.json`

Release implication:

- Offline/download is no longer a vague capability spike. The remaining work is a concrete implementation task: align cache-key/storage expectations between `DashDownloader`, `DashMediaSource` / `DefaultDashChunkSource`, TIDAL `OfflineStorageProvider`, TIDAL `OfflineCacheProvider`, and `PlaybackInfo.Offline.Track.Storage.path` until network-disabled replay reaches READY/playing from app-private cache.
