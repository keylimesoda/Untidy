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

Move #11 from permission-blocked to **sanctioned SDK proof needed**. The next actionable engineering step is a debug-only, one-track proof runner that exercises `usage=DOWNLOAD` / `PlaybackMode.OFFLINE`, installation inventory, and the SDK `OfflinePlayProvider` path. Stop immediately if the only available path is raw caching of current `PLAYBACK`/`STREAM` manifests.
