# Feature Spec: View Album / View Artist from Now Playing

## Goal
Let a listener jump from the currently playing track to its album or artist page from the Now Playing actions sheet on Wear OS.

## User experience
- Entry point: Now Playing → swipe to actions sheet → `View album` / `View artist`.
- Happy path:
  - `View album` opens the existing album detail route for the current track's album.
  - `View artist` opens the existing artist detail route for the current track's primary artist.
- Empty/unavailable metadata:
  - If no navigable album id is present, show a short `Album unavailable` toast.
  - If no navigable artist id is present, show a short `Artist unavailable` toast.
- Non-goals: downloads, queue UI, favorites, add-to-playlist, and playback/exported-service policy changes.

## Product requirements
1. Actions must be available from the existing `ActionsSheet`; labels remain `View album` and `View artist`.
2. Navigation must reuse the existing `album/{albumId}` and `artist/{artistId}` screens.
3. The feature must not require an emulator or new service permissions.
4. The player must not guess routes from display text; route ids are required.

## Technical design
- Extend `TidalTrack` with optional route metadata: `albumId` and `artistId`, defaulting to blank for backward compatibility and queue serialization.
- Populate ids from API track relationship metadata where available.
- Preserve ids through playback handoff extras and `MediaMetadata.extras` so `NowPlayingStateHolder` can expose them in `NowPlayingUiState.track`.
- Add optional callbacks to `TidalPlayerScreen` for opening album/artist routes.
- `PlayerActivity` starts `MainActivity` with a route extra; `MainActivity` consumes it and navigates using the existing nav graph.

## Feasibility review
Feasible with small safe changes:
- Existing album and artist routes/screens already load by id in `MainActivity.kt`.
- The Now Playing UI already receives `TidalTrack` via `NowPlayingStateHolder`.
- Media3 `MediaMetadata.Builder` supports `setExtras(Bundle)`, which can carry the route ids without altering visible metadata.

Risk/limits:
- Some playback sources may not provide relationship ids. In those cases the feature intentionally shows an unavailable toast rather than searching by text.
- Artist relationship naming may vary by TIDAL response shape; implementation reads common `artist`/`artists`/`artistsRoles` relationships and leaves blank if absent.

## Implementation summary
Implemented in this change:
- Added `albumId` and `artistId` fields to `TidalTrack`.
- Parsed ids from v2 and legacy track responses when present.
- Passed ids through playback intents and Media3 metadata extras.
- Rehydrated ids in `NowPlayingStateHolder`.
- Wired `View album` / `View artist` actions to open existing routes via `MainActivity`.

## Verification
Target command:

```bash
source scripts/env-android.sh && bash ./gradlew :app:compileDebugKotlin :core:playback:compileDebugKotlin --no-daemon
```
