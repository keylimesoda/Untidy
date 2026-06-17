# Feature Spec: Downloads / Offline Playback

**Status:** Draft spec + Phase 0 spike completed; implementation proof next. Ric confirmed TIDAL already supports/allows offline/download in 1st- and 3rd-party clients; implement only via sanctioned offline SDK/API surfaces, not by caching STREAM manifests.
**Feature:** #2, Downloads / offline playback
**Target app:** Untidy Wear OS standalone TIDAL client
**Audience:** PM, UX, Android playback/storage implementers, reviewers
**Last updated:** 2026-06-17

## 0. Phase 0 spike result — 2026-06-17

Detailed spike artifact: [`docs/spike-downloads-offline-playback-2026-06-17.md`](spike-downloads-offline-playback-2026-06-17.md).

Recommendation: **do not implement user-visible downloads by caching the current streaming manifest URLs.** The app's current direct-manifest path requests TIDAL streaming playback (`usage=PLAYBACK` / `playbackmode=STREAM`) and has no sanctioned offline entitlement/license/storage semantics.

Promising but unproven lead: local TIDAL SDK artifacts expose offline/download surfaces including `PlaybackMode.OFFLINE`, `TrackManifests$UsageTrackManifestsIdGet.DOWNLOAD`, `OfflinePlayProvider`, `OfflinePlaybackInfoProvider`, `OfflineCacheProvider`, `OfflineDrmHelper`, `PlaybackInfo.Offline.Track`, `Downloads`, and `OfflineTasks`. Treat these as a follow-up SDK proof target, not as permission to cache streaming URLs.

UI truthfulness update from the spike: the Now Playing action sheet no longer cycles fake download states. Until the sanctioned SDK/API provisioning path is implemented, it renders an honest offline proof/unavailable state.

## 1. PM problem statement

Untidy currently streams TIDAL tracks directly from the watch. That is enough for desk listening and Wi-Fi testing, but it misses one of the most important watch use cases: leaving the phone behind for a run, walk, gym session, flight, or commute where network access is weak, expensive, or unavailable.

The current product has three visible gaps:

- The README roadmap explicitly lists **Downloads / offline playback** as not done.
- Settings already shows disabled download controls: **Download quality**, **Download over LTE**, and **Storage limit**.
- The player action sheet exposes a fake local `DownloadState` that cycles through `Download`, `Downloading 47%`, and `Downloaded`, but it is not backed by real storage or playback behavior.

### User value

A TIDAL subscriber should be able to choose music while online, download it safely on the watch, then play it later without a phone and without a live network connection — with watch-appropriate controls, storage limits, battery-friendly defaults, and honest states when licensing or DRM prevents true offline playback.

### Product principles

1. **Honest capability over fake affordances.** TIDAL offline/download exists for first- and third-party clients; Untidy must only expose it after the sanctioned SDK/API provisioning path works for this app/session. Until then, the UI must be truthful and degrade gracefully.
2. **Watch-first constraints.** Downloads should default to Wi-Fi + charging, conservative quality, capped storage, and compact UX.
3. **Playback correctness before breadth.** A single downloaded track/album that plays reliably beats a broad but fragile offline library.
4. **No DRM bypass.** Untidy must not strip, redistribute, or cache protected audio in a way that violates TIDAL terms or content protection.

## 2. Scope

### In scope

- Download actions for tracks, albums, and playlists.
- Offline playback from already-downloaded content.
- Download queue/status management.
- Storage limit and eviction policy.
- Network/battery policy for downloads.
- Settings integration for download quality, LTE allowance, and storage limit.
- UX states for downloading, downloaded, failed, unavailable, storage full, and offline/library filtering.
- Architecture sketch for metadata, download manager, media storage, and playback source selection.
- Explicit TIDAL/DRM unknowns and gating decisions.

### Out of scope for this spec

- Implementing production code.
- Emulator/device validation.
- Companion phone sync.
- User-uploaded files or non-TIDAL audio libraries.
- Circumventing TIDAL DRM, entitlements, or API limitations.
- Store/legal copy beyond product requirements.

## 3. Current-state observations

### Product/documentation

- `README.md` roadmap says downloads/offline playback is not done.
- Status table marks **Downloads / offline playback** as ❌.

### UI

- `SettingsScreen.kt` has a disabled **Downloads** section:
  - Download quality
  - Download over LTE
  - Storage limit
- `ActionsSheet.kt` defines `DownloadState` and renders:
  - `Download`
  - `Downloading N%`
  - `Downloaded` with a checkmark
- `TidalPlayerScreen.kt` stores a local fake `downloadState` and cycles it on click. This should be replaced by state from a real repository when implementation begins.

### Playback

- `TidalMediaService` handles track/queue playback and delegates media loading to `PlaybackBackend`.
- Current production playback uses `DirectManifestPlaybackBackend`, which fetches TIDAL playback manifests and creates Media3 sources (`DashMediaSource` or `ProgressiveMediaSource`) from network manifest/URL data.
- Track transitions now prefetch manifests, but downloaded playback would need a separate local-source path rather than relying on a network manifest at play time.

## 4. Goals and success metrics

### MVP goals

1. User can download one track from Now Playing/action sheet while online.
2. Download survives app/service process death and watch reboot.
3. User can play that downloaded track with radios disabled or network unavailable, subject to TIDAL licensing constraints.
4. User sees accurate progress/failure/offline availability state.
5. Downloads respect a default conservative policy: Wi-Fi only, no LTE, storage capped, battery-aware.

### V1 goals

1. User can download albums and playlists from detail pages.
2. User can browse a dedicated **Downloads** library or filter to downloaded content.
3. Downloaded queues play continuously offline.
4. Storage cleanup is predictable and explainable.
5. Settings controls are enabled and persisted.

### Success metrics

- ≥95% of successful downloaded-track play attempts start without network requests for audio bytes.
- Download state is restored correctly after process death/reboot in manual tests.
- Storage never exceeds the configured cap except for a bounded temporary in-progress file budget.
- User-facing errors distinguish: no entitlement, DRM/offline unsupported, network failure, storage full, expired download, and unknown failure.

## 5. Primary user flows

### Flow A: Download current track from Now Playing

1. User opens Now Playing.
2. User opens the action sheet.
3. User taps **Download**.
4. App validates:
   - User is signed in.
   - Track is streamable and has a full playback entitlement.
   - Offline/download capability is supported for the resolved manifest/license type.
   - Network policy allows download now.
   - Storage cap has room or can evict old content.
5. UI changes to **Queued** or **Downloading N%**.
6. On success, action sheet shows **Downloaded** with checkmark.
7. If the user plays the track later while offline, playback uses the local copy/license.

### Flow B: Download album or playlist

1. User opens album/playlist detail.
2. User chooses **Download album/playlist** from a header action or overflow action.
3. App resolves playable tracks, applying explicit/clean preference as already modeled elsewhere.
4. App enqueues each track as a download item under a collection-level download group.
5. Header state summarizes progress: `12/18 downloaded`, `Paused: Wi-Fi required`, `3 failed`, etc.
6. User can cancel remaining downloads or remove completed content.

### Flow C: Offline playback

1. User loses network or intentionally enables airplane mode.
2. App surfaces downloaded content without implying the full catalog is available.
3. User starts a downloaded track/album/playlist.
4. Playback service selects a local media source when available and valid.
5. If a queue includes missing/expired tracks, the player skips with an explanation or stops on first unavailable item based on policy.

### Flow D: Storage pressure and eviction

1. User starts a new download while near the storage cap.
2. App estimates required bytes from manifest/metadata when possible.
3. App evicts eligible content according to policy before starting, or asks the user to free space if protected/pinned content prevents eviction.
4. UI reports **Storage full** with actions: **Manage downloads**, **Raise limit**, **Cancel**.

### Flow E: Remove download

1. User taps a downloaded item action.
2. Action label is **Remove download**.
3. App asks for confirmation for collection removals; single track can use a quick confirm/undo pattern.
4. App deletes media files, license artifacts if applicable, and metadata rows.
5. If currently playing from local storage, removal waits until playback stops or removes after current play.

## 6. UX states and copy requirements

### Item-level states

| State | Meaning | Primary UI |
| --- | --- | --- |
| Not downloaded | No valid local asset | `Download` |
| Checking | Verifying entitlement/storage/policy | `Checking…` |
| Queued | Waiting for scheduler/policy | `Queued` |
| Paused: Wi-Fi required | LTE disabled or metered network blocked | `Waiting for Wi-Fi` |
| Paused: charging recommended | Optional V1 policy when battery low | `Waiting to charge` |
| Downloading | Active byte transfer | `Downloading 42%` |
| Processing | Finalizing/validating file/license | `Finishing…` |
| Downloaded | Valid playable local asset | `Downloaded` + checkmark |
| Failed | Recoverable or terminal failure | `Download failed` + reason in detail |
| Unavailable | TIDAL/API/DRM does not permit offline | `Offline unavailable` |
| Expired | License/entitlement expired | `Renew download` or `Expired` |
| Removing | Delete in progress | `Removing…` |

### Collection-level states

- Not downloaded: no child tracks downloaded.
- Partial: some child tracks downloaded or queued.
- Downloading: active group progress by track count and bytes when available.
- Downloaded: all playable tracks are downloaded.
- Has failures: at least one track failed or is unavailable.
- Stale/expired: local content exists but requires revalidation/renewal.

### Offline-mode UX

When the app knows the network is unavailable:

- Library/search/discover should not pretend to fetch online results.
- Downloaded content should remain reachable from a top-level **Downloads** entry or a library filter.
- Non-downloaded item play should produce: `This track is not downloaded.`
- Expired/unlicensed local item should produce: `Download expired. Connect to renew.`

### Watch-specific UX guidance

- Keep actions one-tap and text short.
- Prefer progress by track count for collections: `7/12` is more glanceable than byte counts.
- Avoid long confirmation flows for single tracks; use undo/toast/snackbar if feasible on Wear.
- Use disabled states honestly. If offline is unsupported by TIDAL for this app, leave download settings disabled and update copy to explain why.

## 7. Technical architecture sketch

### Proposed modules/classes

This is conceptual only; names are suggested implementation seams.

```text
:app
  ui/downloads/
    DownloadsScreen.kt               # downloaded library + manage storage
    DownloadStatusChip.kt            # reusable item/collection state
  ui/settings/
    SettingsRepository               # add download prefs to existing DataStore

:core:model
  DownloadState.kt                   # item/group state models
  DownloadPolicy.kt                  # quality/network/storage settings
  DownloadedMedia.kt                 # persisted metadata contracts

:core:playback
  OfflinePlaybackResolver            # chooses local vs stream source
  LocalMediaPlaybackBackend path     # Media3 local MediaItem/MediaSource handling
  TidalMediaService                  # route play requests through resolver

:core:downloads (new module or package under :core:playback initially)
  DownloadRepository                 # Room/DataStore facade for persisted state
  DownloadManager                    # enqueue/cancel/retry/remove
  TidalDownloadClient                # manifest/license/file fetch integration
  StorageManager                     # cap accounting, temp files, eviction
  DownloadWorker/Scheduler           # WorkManager if available/suitable on Wear
```

### Data model sketch

Minimum persisted entities:

- `DownloadedTrack`
  - `trackId`
  - title/artist/album/artwork snapshot
  - duration
  - quality/preset used
  - content type/mime/codec
  - local file path or MediaStore URI
  - manifest/license metadata reference, if applicable
  - bytes downloaded
  - state
  - error code/message
  - created/updated/lastPlayed timestamps
  - expiry/renewal timestamp, if provided by TIDAL/license layer
- `DownloadGroup`
  - group id/type: album, playlist, manual queue
  - source id/title/artwork
  - child track ids and order
  - aggregate state/progress
- `DownloadPolicy`
  - quality preset
  - allow cellular/LTE
  - storage limit bytes
  - allow downloads on battery / require charging threshold (optional phase)
  - auto-renew on Wi-Fi (optional phase)

### Playback selection algorithm

For every `playTrack(trackId, knownTrack)` request:

1. Ask `OfflinePlaybackResolver` whether a valid local asset exists.
2. If valid local asset exists, create a local `MediaItem`/`MediaSource`.
3. If no local asset and network is available, use current streaming path.
4. If no local asset and network is unavailable, surface a clear playback error.
5. If local asset exists but license/entitlement is expired, attempt renewal only when online and allowed; otherwise surface `Download expired`.

Important: streaming and offline should share queue, metadata, session, Ongoing Activity, and Now Playing state. Only the source resolution should differ.

### Download pipeline

1. Resolve track metadata and current entitlement.
2. Request playback/download manifest using TIDAL-supported offline/download usage (`usage=DOWNLOAD` / `PlaybackMode.OFFLINE`).
3. Validate response supports local playback and any required DRM/license persistence.
4. Stream bytes to a temp file under app-private storage.
5. Verify file length/hash/container if available.
6. Persist license/artifact metadata, atomically move temp file to final path.
7. Mark state `Downloaded` and emit UI update.
8. On failure, clean temp files and persist a retryable/terminal error.

### Storage location

Recommended default: app-private storage, not public media storage.

- Use `Context.getNoBackupFilesDir()` only if losing downloads on backup/restore is desired; otherwise app files/cache under internal storage is more straightforward.
- Use atomic temp files: `trackId.part` -> `trackId.final` only after validation.
- Store metadata in Room or a small SQLite layer if collection/querying grows; DataStore alone is likely insufficient for per-track download state.
- Use TIDAL-provided offline license/provisioning semantics; do not invent homegrown DRM.

## 8. TIDAL / DRM unknowns and gates

This is the central risk. Implementation must not start until the team answers these questions with primary-source docs, live API probing against developer credentials, or SDK behavior.

### Unknowns

1. **Is offline playback permitted for third-party TIDAL developer apps?**
   - TIDAL may restrict offline downloads to first-party clients or approved SDK flows.
2. **Is there a distinct manifest usage for downloads/offline?**
   - Current code requests `usage=PLAYBACK`, `playbackmode=STREAM`, and direct manifest/DASH data for streaming.
   - Offline may require a different usage, license challenge, or SDK-only path.
3. **Are returned streams DRM-protected, expiring, watermarked, or signed?**
   - Direct URL caching may be prohibited or technically invalid after URL expiry.
4. **Can Media3 persist and renew the required license on Wear OS?**
   - If Widevine offline licenses are involved, the app must use the supported DRM session manager/offline license APIs, not raw file caching.
5. **Do TIDAL SDK terms allow caching audio bytes?**
   - Terms may distinguish transient buffering from user-visible downloads.
6. **Are quality tiers available offline?**
   - BatterySaver/Balanced/High might map differently for downloadable assets.
7. **Can downloaded assets be played after account sign-out?**
   - Expected answer should be no; sign-out should remove or invalidate downloads.
8. **How often must entitlements be renewed?**
   - Need expiry semantics before designing renewal UX.

### Gating recommendation

Create a short technical spike before product implementation:

- Inspect official TIDAL SDK/API docs, artifacts, and first-party/sample call sites for offline provisioning.
- Probe only sanctioned download/offline usage paths; never cache `PLAYBACK`/`STREAM` manifests.
- Determine whether ExoPlayer/Media3 offline DRM is required and viable on Wear OS.
- Produce one of three go/no-go outcomes:
  1. **Supported:** implement full offline downloads.
  2. **SDK-gated/limited:** implement only the supported SDK path and adjust UX.
  3. **Not permitted:** remove fake download affordances or replace them with `Offline playback unavailable in this build` copy.

## 9. Storage and network policy

### Defaults

- Download quality: **Battery Saver** by default, matching watch constraints.
- Download over LTE: **Off** by default.
- Storage limit: default to a conservative cap, e.g. **1 GB** or **10% of free internal storage**, whichever is smaller. Exact default should be real-device tested.
- Downloads while battery low: queue but pause under a threshold, e.g. <20%, unless charging. This can be V1 if MVP keeps policy simpler.

### Network behavior

- Wi-Fi: allowed by default.
- LTE/cellular: blocked unless user explicitly enables **Download over LTE**.
- Metered Wi-Fi: treat as blocked or warn if Android reports metered.
- Roaming: blocked unless explicit future setting.
- Offline: queue requests and show `Waiting for Wi-Fi`.

### Storage behavior

- Always reserve headroom for app operation; do not fill internal storage.
- Use a hard cap plus temp-file budget.
- Evict only content that is not pinned/actively playing.
- Suggested eviction order:
  1. Failed/incomplete temp downloads.
  2. Oldest unplayed downloads.
  3. Least-recently played downloads.
  4. Ask user before deleting explicitly downloaded albums/playlists if the mental model treats them as pinned.
- Sign-out should remove local audio/license artifacts and download metadata, or mark all content unusable and prompt removal. Safer recommendation: remove all downloads on sign-out.

## 10. Settings integration

The existing disabled settings should become real only after the TIDAL/DRM gate is resolved.

### Download quality

Options should mirror or intentionally differ from streaming quality:

- Battery Saver: smallest files, safest default.
- Balanced: higher quality with moderate storage.
- High: storage-heavy; warn on small devices if needed.

Question: Should download quality reuse `AudioPreset`, or define `DownloadQuality` separately? Recommendation: use a separate model that can map to `AudioPreset` initially but leaves room for offline-specific constraints.

### Download over LTE

- Default off.
- Toggle copy: `Download over LTE` / `Uses watch data plan`.
- When off and user initiates download on LTE: queue and show `Waiting for Wi-Fi`.

### Storage limit

- Preset chips: `500 MB`, `1 GB`, `2 GB`, `Custom/Max available` only if custom UI is worth it.
- Show current usage: `Downloads: 642 MB of 1 GB`.
- Provide **Manage downloads** entry to remove content.

### Debug settings

For debug builds only:

- Show download DB counts.
- Show active/in-progress worker count.
- Show last download error.
- Show resolved playback source: `local` vs `stream`.

## 11. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Sanctioned provisioning source remains undiscovered | Feature cannot ship as imagined | Keep #11 as proof work; remove or neutralize user-visible offline affordance until resolved |
| Cached streaming URLs expire or bypass the offline contract | Broken playback/supportability issue | Do not implement raw `PLAYBACK`/`STREAM` URL caching |
| Widevine offline license unsupported or fragile on Wear OS | Offline playback fails in real use | Prototype one licensed track on real device before building UX breadth |
| Storage pressure on watches | App instability / user frustration | Conservative defaults, hard cap, atomic temp files, cleanup |
| Battery drain from background downloads | Bad watch experience | Wi-Fi/charging/battery policy; small queue concurrency |
| Process death during downloads | Corrupt/inconsistent state | Persistent DB, resumable/atomic downloads, temp cleanup on startup |
| Queue contains mixed local/online tracks | Confusing offline playback | Resolver per track; clear skipped/blocked messaging |
| Sign-out/account switch leaves protected content | Privacy/licensing issue | Delete local audio/license artifacts on sign-out |
| UI promises more than backend can deliver | Trust loss | Gate UI enablement on actual support; honest states |

## 12. Open questions

### Product

1. Should downloads be available from MVP Now Playing only, or should album/playlist download ship in the first user-visible release?
2. Should downloaded content be shown as a separate top-level tab, a Library filter, or both?
3. Are downloads treated as user-pinned content, or may the app auto-evict them under the storage cap?
4. Should single-track remove require confirmation, or is undo enough?
5. What exact default storage cap is appropriate for target Galaxy Watch hardware?

### UX

1. Where should collection-level progress live on album/playlist screens?
2. How should partial album/playlist downloads be represented in compact round layouts?
3. Should Now Playing show a persistent offline/local badge?
4. What copy should be used if offline is not supported by TIDAL for this app?

### Android/technical

1. Is WorkManager reliable and lightweight enough for Wear OS downloads, or should a foreground service own active downloads?
2. Should download state live in a new `:core:downloads` module or inside `:core:playback` until it stabilizes?
3. Should metadata be stored with Room, SQLDelight, or a simpler SQLite wrapper?
4. Can Media3 offline DRM APIs persist licenses on the target watch devices?
5. How does local playback integrate with `MediaLibraryService` exported behavior and external controllers?

### TIDAL sanctioned provisioning

1. Which endpoint/SDK method is sanctioned for downloads for this app/session?
3. What license expiry/renewal rules apply?
4. Must local files be encrypted or DRM-managed?
5. Are there reporting/analytics obligations for downloaded playback?

## 13. Phased implementation plan

### Phase 0 — Capability spike / go-no-go

**Goal:** Determine the sanctioned technical provisioning path for this already-supported class of offline/download capability.

Deliverables:

- TIDAL offline/download support finding with source links or live SDK evidence.
- DRM/license feasibility note for Media3/Wear OS.
- Recommendation: supported, limited, or not permitted.
- If not permitted, product decision for removing/rewording fake UI.

Exit criteria:

- Team knows whether Untidy can store playable offline audio and under what constraints.

### Phase 1 — Spec hardening and UI truthfulness

**Goal:** Align UI with actual capability before building storage.

If offline is supported:

- Replace fake local `DownloadState` with repository-backed read-only state in player UI.
- Add disabled-but-accurate states while backend is under construction.

If offline is not yet supported:

- Change action copy to `Offline unavailable` or hide the action.
- Keep settings disabled with explanatory copy.

Exit criteria:

- No fake download state remains in user-facing UX.

### Phase 2 — Single-track MVP

**Goal:** One downloaded track plays offline reliably.

Work items:

- Add persisted download metadata store.
- Add download policy model and settings persistence.
- Add single-track download manager.
- Add local playback resolver and Media3 local source path.
- Add player/action-sheet state integration.
- Add cleanup on sign-out.

Verification without emulator where possible:

- JVM tests for state machine, storage cap accounting, policy decisions, and resolver selection.
- Static compile/lint/unit test gate.

Device verification later:

- Download track online, reboot/process kill, enable airplane mode, play track.

### Phase 3 — Collections and Downloads library

**Goal:** Albums/playlists and management UX.

Work items:

- Add album/playlist download group model.
- Add detail-screen download actions and aggregate progress.
- Add Downloads screen/manage storage screen.
- Add remove/retry/cancel flows.
- Add mixed local/stream queue handling.

Verification:

- JVM tests for group progress and partial failure states.
- Real-watch tests for long albums/playlists and storage pressure.

### Phase 4 — Robustness and renewal

**Goal:** Make downloads dependable over weeks of use.

Work items:

- License/entitlement renewal if required.
- Automatic retry policy.
- Startup temp-file cleanup.
- Storage-pressure notifications or manage prompts.
- Better debug diagnostics.

Verification:

- Expiry/renewal tests with fake clock.
- Network transition tests.
- Battery/storage stress tests on real hardware.

### Phase 5 — Polish and release readiness

**Goal:** Ship-ready offline feature.

Work items:

- Final legal/settings/privacy copy.
- Accessibility and round-screen QA.
- Performance and battery profiling.
- README/status update only after feature is real.

Exit criteria:

- Offline playback works on target real watches under network-off conditions.
- No known sanctioned-provisioning/DRM blockers remain.

## 14. Reviewer recommendations

Top recommendations before implementation:

1. **Run the TIDAL/DRM provisioning proof first.** This feature is dominated by license, storage, and DRM orchestration; raw streaming-manifest caching is not a safe assumption.
2. **Remove or neutralize the fake download cycle early.** Even before full implementation, the UI should not imply downloads work.
3. **Start with a single-track MVP.** It exercises every critical seam — entitlement, file/license storage, policy, local Media3 playback, and restored state — without collection complexity.
4. **Use a real persisted download store.** Process death/reboot tolerance is required for a watch feature; in-memory state is not acceptable.
5. **Keep defaults conservative.** Wi-Fi only, Battery Saver quality, hard storage cap, and app-private storage are the right starting point for Wear OS.
6. **Design local-vs-stream as source resolution, not a separate player.** Preserve the current MediaLibraryService/session/Now Playing model and swap only the media source when valid local content exists.
