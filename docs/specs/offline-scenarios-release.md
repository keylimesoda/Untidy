# Offline scenarios release spec

Status: implementation spec — simple release-ready offline model
Date: 2026-06-20

## Product model

Offline usage uses the same user units as the rest of Untidy:

- Track: atomic playable offline unit.
- Album: collection operation/summary over its playable tracks.
- Playlist: collection operation/summary over its currently loaded playable tracks.

The first release implementation keeps the technical model simple: album and playlist actions are batch operations over track-level local records. A future download manager can replace the local-record write path with real byte/license download jobs without changing the user-facing unit model.

## Supported scenarios

### 1. Download current track

From Now Playing pull-over menu:

- If current track is eligible and not local, row shows `Download`.
- Tap marks the current track as local for the current simple implementation.
- Row changes to `Downloaded`.
- If already local, tapping `Downloaded` opens remove confirmation.

### 2. Download album

From Album screen:

- Album action row reflects collection status:
  - `Download album` when no tracks are local and collection download actions are enabled.
  - `Partial N/N` when some tracks are local.
  - `Downloaded N/N` when all playable tracks are local.
- Tapping the row for a not/partial album marks all playable album tracks local.
- Tapping a fully downloaded album row prompts no destructive action; removal lives in Downloads/Settings for now.

### 3. Download playlist

From Playlist screen:

- Playlist action row mirrors album behavior over the loaded playlist tracks.
- The simple implementation saves the currently loaded playlist track set; it does not persist or reconcile playlist snapshots yet.

### 4. Discover and play offline content

From Home → Downloads:

- Downloads lists local tracks.
- Tapping a track plays that track.
- Offline, playback is allowed for local tracks and rejected for non-local tracks.
- Album/playlist offline playback filters queues to playable local tracks when network is unavailable.

### 5. Remove one track

From Downloads:

- Tap `Remove download` under a track.
- Confirm `Remove local copy?`.
- Track disappears from Downloads.

From Now Playing:

- If current track is local, context menu shows `Downloaded`.
- Tap opens `Remove download` / `Cancel`.

### 6. Remove all tracks

From Settings:

- If local tracks exist, `Remove all downloads` appears.
- Confirmation removes all local track records/caches.

## Deliberate deferrals

- Real byte/license download manager and persistent jobs.
- Album/playlist remove-as-a-unit.
- Playlist snapshot update detection.
- LTE toggle and storage limit configuration.
- Download progress beyond immediate local-record completion.

## Verification

- Unit tests cover collection status and batch local-record behavior.
- Build gates: `lintDebug assembleDebug testDebugUnitTest assembleRelease`.
- Release copy scan should not include debug/proof/internal language.
