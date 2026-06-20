# Offline Unit Product Model — 2026-06-20

Repo: `/home/riclewis/.openclaw/workspace/projects/Untidy`  
Role: Product model / QA engineering  
Status: Report only; no code changes made.  
Commit inspected: `ce8a485`

## Executive summary

Untidy should model offline usage in the same units users already understand:

1. **TrackOfflineUnit** — one TIDAL track saved locally on this watch.
2. **AlbumOfflineUnit** — the playable track set for one album, represented as a collection summary over track units.
3. **PlaylistOfflineUnit** — a saved snapshot of a playlist's playable track membership/order, represented as a collection summary over track units plus playlist staleness rules.

The current implementation has useful pieces: track-level downloaded/failed markers in `OfflineDownloads.kt`, a Downloads shelf, local removal flows, settings cleanup, offline playback rejection copy, and album/playlist collection summaries. It does **not** yet have a release-grade public download system: no unified repository/state machine, no production download manager, no persisted manifest/license validity model, no true network-independent playback path from a release record, and no album/playlist group lifecycle.

**Release-ready rule:** do not show a public `Download`, `Download album`, or `Download playlist` promise unless that unit can become playable offline in release and pass device validation. Until then, the product model should be visible as honest management/status copy: `Downloaded`, `Partial`, `Download unavailable`, `Not in this release`, and local-only removal.

## Sources reviewed

- `reports/download-release-api-audit-2026-06-20.md`
- `reports/download-release-scenarios-2026-06-20.md`
- `reports/download-release-ux-audit-2026-06-20.md`
- `docs/ux/offline-download-lifecycle-ux-2026-06-17.md`
- `docs/spec-downloads-offline-playback.md`
- `core/playback/src/main/java/com/tidal/wear/core/playback/offline/OfflineDownloads.kt`
- `core/playback/src/test/java/com/tidal/wear/core/playback/offline/CollectionDownloadSummaryTest.kt`
- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/downloads/DownloadsScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/album/AlbumScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/playlist/PlaylistScreen.kt`
- `app/src/main/java/com/tidal/wear/MainActivity.kt`

## Current implementation truth

### What exists now

- Track-local state in `OfflineDownloads.kt`:
  - `downloaded:<trackId>` boolean
  - `failed:<trackId>` boolean
  - `title:<trackId>` / `artist:<trackId>` / `downloadedAt:<trackId>`
  - local removal and remove-all helpers
  - storage byte summary based on per-track cache directories
- `DownloadedTrackSummary` is track-only and can convert back to `TidalTrack` for Downloads playback.
- `CollectionDownloadSummary` can summarize playable/distinct/downloaded/failed child track counts.
- Now Playing action sheet supports coarse states: `Unavailable`, `NotDownloaded`, `Downloading(progress)`, `Downloaded`.
- Release builds do not start downloads. Debug builds can launch the offline proof activity and mark a track downloaded after proof polling.
- Downloads screen lists locally marked tracks, plays them, and removes local copies with copy like `Remove download`, `Keeps it in TIDAL`, `Remove local copy?`.
- Settings can open Downloads, show count/storage, and remove all local downloads.
- Album/playlist screens display collection summaries and now use safer unavailable copy when nothing is downloaded.

### What does not exist yet

- No production `DownloadRepository` or durable release schema for offline units.
- No public download manager/job lifecycle: queued, waiting, downloading, finishing, retry, cancel, process-death recovery.
- No release-safe persisted media manifest/license/validity model.
- No true release offline resolver that can start from persisted local records without network.
- No album or playlist group download queue.
- No playlist snapshot/update/staleness model.
- No public storage quota/preflight/eviction policy beyond manual cleanup.

## Product principles

1. **User units first:** all visible offline status should be track, album, or playlist — not cache, manifest, task, proof, or endpoint.
2. **Truthful copy over breadth:** if release cannot create a playable local unit, say `Download unavailable`, not `Download`.
3. **Track is the atomic playable unit:** album and playlist status are summaries of child track units until collection downloads are fully implemented.
4. **Downloaded means playable locally:** do not set `Downloaded` until required local media/license/metadata are valid enough to pass offline playback validation.
5. **Partial is first-class:** albums and playlists often contain unavailable, failed, duplicate, or expired tracks.
6. **Removal is local-only:** copy must say `Remove download`, `Remove local copy`, `Local files only`, or `Keeps TIDAL library`; never imply remote deletion.
7. **No public debug language:** release UX must not show `proof`, `debug`, `MVP`, raw URLs, manifests, tokens, licenses, or internal task names.

## Unit identity model

### TrackOfflineUnit

A `TrackOfflineUnit` represents one playable TIDAL track on this watch.

Minimum identity fields for a release-grade model:

- `unitType = track`
- `trackId`
- `title`
- `artist`
- optional `albumId`, `albumTitle`, `artworkUrl` or local artwork reference
- `state`
- `downloadedAt` when complete
- `updatedAt`
- optional `failureReason`
- optional `validUntil` / `revalidateAt` once license validity is known
- optional `storageBytes`
- optional `sourceSurface` such as now playing, album, playlist, downloads

Current implementation covers only `trackId`, `title`, `artist`, `downloadedAt`, downloaded/failed booleans, and cache path by track id.

### AlbumOfflineUnit

An `AlbumOfflineUnit` represents the album's current resolved playable track set.

Minimum identity fields:

- `unitType = album`
- `albumId`
- `title`
- `artist`
- `trackIds` after de-dupe and playable filtering
- `playableCount`
- `downloadedCount`
- `failedCount`
- derived `state`
- optional `unavailableCount`
- optional `downloadGroupId` later

Current implementation can derive `playableCount`, `downloadedCount`, and `failedCount` using `collectionDownloadSummary(tracks)`. It does not persist an album download group.

### PlaylistOfflineUnit

A `PlaylistOfflineUnit` represents a local snapshot of a playlist's playable track membership/order.

Minimum identity fields:

- `unitType = playlist`
- `playlistId`
- `title`
- `creator`
- `snapshotTrackIds` in order
- `snapshotCreatedAt`
- `remoteVersion`/`etag`/`lastModified` if available later
- `playableCount`
- `downloadedCount`
- `failedCount`
- derived `state`
- optional `stale = true` / `updateAvailable = true`
- optional `downloadGroupId` later

Current implementation can summarize the currently loaded playlist track list, but it does not persist playlist snapshots or detect remote updates.

## Shared state vocabulary

These are product states, not necessarily one Kotlin enum yet. Implementations may collapse disabled cases internally, but user copy should remain consistent.

| State | Meaning | Primary copy | Action |
| --- | --- | --- | --- |
| `Unavailable` | Unit cannot be offline in this release/account/region/track type | `Download unavailable` or `Offline unavailable` | Disabled/no-op; optional short toast |
| `NotDownloaded` | Unit is eligible and release can start the download | `Download` / `Download album` / `Download playlist` | Start download |
| `Queued` | Intent accepted, work not yet transferring | `Queued` or `Queued 0/N` | Open status/cancel if available |
| `WaitingForWiFi` | Policy blocks transfer until Wi-Fi | `Waiting for Wi‑Fi` | Open Downloads/Settings; cancel if available |
| `Downloading` | Media/license transfer is active | `Downloading 42%` or `Downloading 7/12` | Usually disabled/status; cancel if available |
| `Finishing` | Validating/finalizing local record | `Finishing…` | Disabled |
| `Downloaded` | Local unit is complete and playable under current validity | `Downloaded` or `Downloaded N/N` | Play or remove local copy |
| `Partial` | Some child tracks local, not all playable children local | `Partial N/N` | Play local-valid subset or open detail; collection remove later |
| `Failed` | Retryable or terminal failure occurred | `Download failed` / `3 failed` | Retry if retryable; remove/clear |
| `Expired` | Local asset/license needs connection/renewal | `Download expired` | Connect to renew/redownload; remove |
| `StorageFull` | Storage policy prevents completion | `Storage full` | Manage downloads |
| `Removing` | Local delete in progress | `Removing…` | Disabled |
| `UpdateAvailable` | Playlist remote membership differs from local snapshot | `Update available` | Update snapshot/download new tracks later |

## TrackOfflineUnit states, copy, and actions

| State | Entry condition | Primary copy | Secondary/detail copy | User action |
| --- | --- | --- | --- | --- |
| `Unavailable` | Release cannot start downloads, invalid track id, unsupported entitlement, or backend disabled | `Download unavailable` / `Offline unavailable` | `Not in this release` or `Track unavailable` | Disabled/no-op |
| `NotDownloaded` | Release supports track downloads and this track is eligible | `Download` | `Save on watch` | Start track download |
| `Queued` | Track accepted for background work | `Queued` | `Waiting to start` | Open Downloads/cancel if available |
| `WaitingForWiFi` | Network policy blocks transfer | `Waiting for Wi‑Fi` | `Downloads use Wi‑Fi` | Open Downloads/Settings; cancel if available |
| `Downloading` | Bytes/license are transferring | `Downloading 42%` | optional | Status/cancel only |
| `Finishing` | Local validity check/final write | `Finishing…` | optional | Disabled |
| `Downloaded` | Track has valid local record and playable local source | `Downloaded` | `On watch` | Tap opens `Remove download` + `Cancel`; Downloads row plays track |
| `Failed` | Retryable network/server/storage/license issue | `Download failed` | `Tap to retry` or short reason | Retry or remove failed record |
| `Expired` | Local record exists but must be renewed | `Download expired` | `Connect to renew` | Renew/redownload when online, or remove |
| `StorageFull` | Cannot reserve enough local storage | `Storage full` | `Manage downloads` | Open Downloads/Settings |
| `Removing` | Local state/cache/license deletion in progress | `Removing…` | optional | Disabled |

Current release-safe mapping if public download creation remains off:

- Already marked local track: `Downloaded` → allow local remove.
- Non-downloaded valid track: `Unavailable` with `Download unavailable` / `Not in this release`.
- Invalid/fixture/blank track: `Unavailable` with `Offline unavailable` / `Track unavailable`.
- Do not show `Download`, `Queued`, or `Downloading` in release unless a production path exists.

## AlbumOfflineUnit states, copy, and actions

Album state is derived from distinct playable child tracks.

| State | Entry condition | Primary copy | Secondary/detail copy | User action |
| --- | --- | --- | --- | --- |
| `Unavailable` | No playable tracks or album downloads not supported and no local children | `Download unavailable` | `Not in this release` or `Album has no playable tracks` | Disabled/no-op or safe toast |
| `NotDownloaded` | Future: collection downloads supported and no child local | `Download album` | `Save album on watch` | Start album group download |
| `Queued` | Future album group queued | `Queued 0/N` | `Waiting to start` | Open Downloads/cancel |
| `WaitingForWiFi` | Future group waiting | `Waiting for Wi‑Fi` | `N tracks queued` | Open Downloads/cancel |
| `Downloading` | Future group active | `Downloading N/N` | Optional bytes/status | Open status/cancel |
| `Downloaded` | `downloadedCount == playableCount`, `failedCount == 0`, playableCount > 0 | `Downloaded N/N` | `All local copies on watch` | Play album; future remove album local copies |
| `Partial` | `downloadedCount > 0` but not full | `Partial N/N` | `Local copies on watch` | Play local-valid subset only if policy is clear; otherwise open details |
| `Failed` | `failedCount > 0`, especially with no downloaded children | `X failed` | `Tap to retry later` | Future retry/clear; current safe toast |
| `Expired` | One or more local children expired | `Partial N/N` or `Expired` | `Connect to renew` | Future renew child tracks |
| `StorageFull` | Future group blocked by storage | `Storage full` | `Manage downloads` | Open settings/downloads |

Release-ready behavior today:

- Keep album download action honest: `Download unavailable` / `Not in this release` when `downloadedCount <= 0`.
- If some tracks are locally marked, show `Partial N/N` or `Downloaded N/N` based on current summary.
- Do not promise that tapping the album chip will start a group download.
- If album playback is attempted offline, product must decide either:
  - **subset policy:** play only downloaded children and explain skipped tracks; or
  - **strict policy:** fail if the requested/current child is not local.
  The current service-level queue filtering already leans toward local-valid subset behavior, but this needs explicit tests.

## PlaylistOfflineUnit states, copy, and actions

Playlist state is derived from a snapshot of child track units. Playlist download requires stronger rules than albums because playlist membership can change.

| State | Entry condition | Primary copy | Secondary/detail copy | User action |
| --- | --- | --- | --- | --- |
| `Unavailable` | Playlist downloads unsupported and no local children | `Download unavailable` | `Not in this release` | Disabled/no-op or safe toast |
| `NotDownloaded` | Future: playlist downloads supported and no child local | `Download playlist` | `Save playlist on watch` | Snapshot playlist and start group download |
| `Queued` | Future playlist group queued | `Queued 0/N` | `Waiting to start` | Open Downloads/cancel |
| `WaitingForWiFi` | Future group waiting | `Waiting for Wi‑Fi` | `N tracks queued` | Open Downloads/cancel |
| `Downloading` | Future group active | `Downloading N/N` | Optional bytes/status | Open status/cancel |
| `Downloaded` | Snapshot playable children all local and no failures | `Downloaded N/N` | `Playlist snapshot on watch` | Play snapshot; future remove local playlist copies |
| `Partial` | Some snapshot/current children local | `Partial N/N` | `Local copies on watch` | Play local-valid subset only if policy is clear; otherwise open details |
| `Failed` | One or more child failures | `X failed` | `Tap to retry later` | Future retry/clear |
| `Expired` | One or more local children expired | `Partial N/N` or `Expired` | `Connect to renew` | Future renew child tracks |
| `UpdateAvailable` | Online remote playlist differs from local snapshot | `Update available` | `Playlist changed online` | Future update action |
| `StorageFull` | Future group blocked by storage | `Storage full` | `Manage downloads` | Open settings/downloads |

Release-ready behavior today:

- Do not show a public functional `Download playlist` unless snapshot/download/update semantics exist.
- Show `Download unavailable` / `Not in this release` when nothing is local.
- If local children exist, summarize them as `Partial N/N` or `Downloaded N/N`, but do not claim remote playlist sync.
- Never silently delete local tracks because they were removed from a remote playlist. Future cleanup must be explicit.

## MVP / release-ready behavior

### Safe MVP if public downloads are not ready

This is the recommended current public posture based on the API/storage audit:

1. **Downloads shelf exists as a local management shell.**
   - Home → Downloads remains available.
   - Empty state says `No downloads on this watch` / `Downloads are unavailable in this release`.
2. **Track units can be displayed/removed if already locally marked.**
   - `Downloaded` tracks appear in Downloads.
   - Now Playing can show `Downloaded` for an already-local track and expose `Remove download`.
3. **Non-local tracks do not show a false download promise.**
   - Now Playing / Track Context: `Download unavailable` or `Offline unavailable`.
4. **Album/playlist summaries are read-only product truth.**
   - No local children: `Download unavailable` / `Not in this release`.
   - Some local children: `Partial N/N`.
   - All playable local children: `Downloaded N/N`.
5. **Removal works and is local-only.**
   - Per-track and remove-all clear Untidy local state/cache only.
6. **Offline playback failure is clear.**
   - Non-downloaded playback while offline uses `Not downloaded · connect to stream this track`.

### First public download release if/when enabled

Minimum scope for an honest public download feature:

1. **Single-track only.** Start from Now Playing; no album/playlist group download yet.
2. **Production repository/state model.** No debug proof polling or SharedPreferences-only booleans as source of truth.
3. **Durable track record.** Persist local manifest/media/license metadata needed to play offline after process death/reboot.
4. **Real state machine.** At least `Queued`, `WaitingForWiFi`, `Downloading`, `Finishing`, `Downloaded`, `Failed`, `StorageFull`, `Expired/renew` if applicable.
5. **Downloads shelf.** Shows active, failed, and downloaded track units.
6. **Removal.** Per-track and remove-all delete media/cache/license/metadata and refresh all surfaces.
7. **Offline playback.** A downloaded track plays with network/radios disabled from local valid assets; a non-downloaded track fails visibly.
8. **Policy defaults.** Wi-Fi only, watch-friendly quality, manual cleanup or hard cap, no LTE consumption by default.
9. **Release hygiene.** No debug/proof activities, strings, raw endpoints, tokens, manifest/license details, or synthetic provider assumptions in public UX.

## Explicit deferrals

Defer from the first public download release:

- Album-level download start.
- Playlist-level download start.
- Playlist snapshot update detection and `Update available` UX.
- Per-collection remove/download cleanup flows.
- Automatic eviction beyond manual cleanup and conservative preflight/headroom.
- Configurable download quality UI.
- LTE download enablement.
- Offline-only mode toggle.
- License renewal UX if the first release can instead fail clearly and require reconnect/redownload.
- Library-wide downloaded filter/badges beyond Home → Downloads.

## Acceptance criteria

### General release truth

- Release build never exposes a clickable `Download`, `Download album`, or `Download playlist` action unless the action can create a playable local unit.
- Release user-visible strings do not include `proof`, `debug`, `MVP`, raw URL, manifest, token, license blob, cache key, or internal endpoint names.
- `Downloaded` is only shown for units whose required child track records are locally valid according to the product model.
- `Download unavailable` / `Not in this release` is used for unsupported units instead of enabled dead affordances.

### TrackOfflineUnit

- Already-local track renders `Downloaded` in Now Playing, Track Context, and Downloads after app restart.
- Tapping `Downloaded` exposes `Remove download` and `Cancel`; it does not silently redownload.
- Per-track removal clears downloaded marker, failed marker, title/artist/downloadedAt metadata, cache/media bytes, and future license artifacts.
- Removing a track refreshes Downloads, Settings count/storage, and Now Playing state.
- Offline playback of a valid downloaded track succeeds with network disabled before public claim.
- Offline playback of a non-downloaded track fails with visible `Not downloaded` / `connect to stream` copy and no stuck foreground service.

### AlbumOfflineUnit

- Album playable count is based on distinct nonblank child track ids.
- `Downloaded N/N` appears only when all playable distinct children are downloaded and no child failures exist.
- `Partial N/N` appears when at least one child is downloaded but the album is not fully downloaded.
- Failed child tracks are counted and surfaced as `X failed` or partial failure detail.
- No public album group download starts until queue/progress/retry/cancel/storage/partial semantics exist.
- If album playback is allowed offline, mixed local/nonlocal behavior matches a documented subset-or-strict policy.

### PlaylistOfflineUnit

- Playlist playable count is based on distinct/snapshot child track ids according to the chosen model.
- `Downloaded N/N` means the local snapshot/current playable set is fully local and no failures exist.
- `Partial N/N` means some children are local, not full playlist offline availability.
- No public playlist group download starts until snapshot semantics exist.
- Remote playlist changes never silently delete local downloads.
- Future `Update available` requires a detectable remote-vs-local snapshot difference and explicit user action.

### Removal and settings

- All removal copy is local-only: `Remove download`, `Remove local copy`, `Local files only`, `Keeps TIDAL library`.
- Remove-all clears all local download records and cache/license artifacts or reports failure without pretending success.
- Account sign-out behavior is explicit: either remove/invalidate local downloads or copy clearly says what stays local and why.
- Storage count/bytes reflect local file-system state, not just row count, if bytes are shown.

## Required tests

### JVM/unit tests

1. **Track state/copy reducer**
   - Maps eligibility/build/account/local-record states to exact labels/actions.
   - Release-disabled + nonlocal track → `Download unavailable`, disabled.
   - Release-disabled + local track → `Downloaded`, remove action available.
   - Invalid/fixture/blank id → `Offline unavailable`, disabled.

2. **Collection summary expansion**
   - Existing distinct/downloaded/failed tests stay green.
   - Add empty list, blank ids, duplicate ids, full download, partial download, failed-only, downloaded+failed conflict, and zero-playable cases.

3. **AlbumOfflineUnit reducer**
   - Child summaries map to `Unavailable`, `Partial`, `Downloaded`, `Failed` with exact copy.
   - `Downloaded` is false when `failedCount > 0` even if counts otherwise match.

4. **PlaylistOfflineUnit reducer**
   - Snapshot/current child summaries map to `Unavailable`, `Partial`, `Downloaded`, `Failed`.
   - Future stale snapshot maps to `Update available` only when remote difference evidence exists.

5. **Removal helpers**
   - Per-track removal clears downloaded/failed/title/artist/downloadedAt and deletes cache path.
   - Blank id removal returns false and changes nothing.
   - Remove-all calls per-track removal and reports partial failure.

6. **Storage accounting**
   - Missing cache dirs count as 0.
   - File sizes sum recursively.
   - Removed tracks no longer contribute storage bytes.

7. **Offline resolver policy**
   - Online + non-downloaded → stream path allowed.
   - Offline + downloaded valid → local path allowed.
   - Offline + non-downloaded → blocked with user-visible reason.
   - Marker present + cache/license missing → not treated as valid downloaded.

### Compose/UI tests where feasible

1. `ActionsSheet` label/enabled/icon behavior for `Unavailable`, `NotDownloaded`, `Downloading`, `Downloaded` and future states if added.
2. `Downloaded` in Actions opens inline `Remove download` / `Cancel` and preserves state on cancel.
3. `DownloadsScreen` empty state says `No downloads on this watch` / `Downloads are unavailable in this release` when public download creation is off.
4. `DownloadsScreen` populated row shows title/artist fallback and remove confirmation copy.
5. `SettingsScreen` shows `Manage downloads`, disabled public-copy settings, and remove-all only when downloads exist.
6. Album/playlist screens show `Download unavailable` / `Not in this release` when no local children and no collection download support.
7. Album/playlist screens show `Partial N/N` and `Downloaded N/N` for seeded local child summaries.

### Integration/fake-manager tests

1. Fake track manager transitions `Queued → Downloading → Finishing → Downloaded`; all surfaces update.
2. Fake network loss maps to `Waiting for Wi‑Fi` without marking downloaded.
3. Fake storage full maps to `Storage full` and opens/links to management.
4. Fake failure maps to retryable `Download failed`; retry does not create duplicate rows.
5. Process recreation reloads active/downloaded/failed state from repository.
6. Downloads list can start playback for a fake valid local track.
7. Offline non-downloaded playback is rejected without launching a stuck service.

### Device validation before any public offline claim

- Install release candidate on physical Wear OS hardware.
- Download one track over Wi-Fi.
- Kill/restart app; reboot watch.
- Disable Wi-Fi/LTE/Bluetooth or otherwise ensure network-disabled playback.
- Play from Downloads; confirm audio starts and continues from local source.
- Attempt non-downloaded playback offline; confirm visible rejection copy.
- Remove downloaded track; confirm local bytes/metadata/license removed and offline playback fails afterward.
- Start download, drop Wi-Fi, restore Wi-Fi; confirm waiting/resume or clean failure.
- Verify no release UI exposes proof/debug/internal strings.

## Recommended next implementation sequence

1. Extract a small `OfflineUnitState` / copy reducer for track, album, and playlist states.
2. Add unit tests for reducer behavior and current `CollectionDownloadSummary` edge cases.
3. Keep current release behavior honest: no public creation promise unless the release path exists.
4. Build a production `TrackOfflineUnit` repository before moving any debug proof byte-download logic into release paths.
5. Only after track lifecycle passes device validation, add `AlbumOfflineUnit` and `PlaylistOfflineUnit` group queues.

## Bottom line

The release-ready product model is: **tracks are the atomic offline unit; albums and playlists are user-facing collection summaries over track units until group downloads are real.** Public copy should be conservative and truthful now, while the state model should be rich enough to support future track, album, and playlist downloads without redesigning the UX vocabulary.
