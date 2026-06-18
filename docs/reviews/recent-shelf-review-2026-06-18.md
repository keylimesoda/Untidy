# Recent Shelf Spec Review — 2026-06-18

Repo: `keylimesoda/Untidy`  
Issue: #39 / UNTIDY-038  
Scope: review-only pass on `docs/specs/recent-shelf.md` and current Home/Library/Album/Playlist/Player navigation.  
Mode: no production-code changes.

## Verdict

**ACCEPT spec direction with one required MVP decision:** implement a minimal track context route/screen for track Recent taps. Do **not** route track taps to playback, and do not use Now Playing except as a narrow same-track affordance after the context screen exists.

Ric's product decisions are internally consistent and should stay as guardrails:

- Recent is local watch explicit play history.
- Tap opens detail/context, **not** autoplay.
- No artists in Recent because there is no Play Artist action.
- Do not record auto-advanced tracks.

## Evidence reviewed

- `docs/specs/recent-shelf.md`
- `gh issue view 39`
- `app/src/main/java/com/tidal/wear/MainActivity.kt`
- `app/src/main/java/com/tidal/wear/ui/library/LibraryScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/album/AlbumScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/playlist/PlaylistScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
- `core/model/src/main/java/com/tidal/wear/core/model/TidalModels.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/TidalMediaService.kt`
- `core/playback/src/main/java/com/tidal/wear/core/playback/PlaybackActions.kt`

## Findings

### Important — Track tap behavior must not reuse existing track-row semantics

Current track rows are play intents:

- Library favorite tracks call `onPlayTrack(track)`.
- Search/Discover track rows call `onPlayTrack(track)` or `onPlayQueue(...)`.
- Album and Playlist track rows call `onPlayQueue(tracks, index)`.
- Album/Playlist primary chips call `onPlayQueue(tracks, 0)`.

That is correct for existing browse/detail screens, but it means a Recent track row cannot simply reuse the existing playable track-row callback. Doing so would violate the explicit no-autoplay decision.

**Recommendation:** Recent rows should have distinct callbacks:

- `onOpenRecentTrack(trackOrId)` for track context.
- `onOpenAlbum(albumId)` for album recent.
- `onOpenPlaylist(playlistId)` for playlist recent.
- Separate `onPlayTrack` / `onPlayQueue` only inside detail/context actions.

### Important — MVP track tap should open a minimal Track Context screen

Untidy already has most of the context actions in Now Playing's action sheet:

- Add to playlist
- View album
- View artist
- Download / remove download state
- Queue / Home / output actions

But Now Playing is playback-centered and includes transport controls. Opening it for a non-loaded recent track would be ambiguous and could feel like a play action even if it does not start playback. The spec's fallback option — "Now Playing only when the same track is currently loaded; otherwise disabled/context message" — is acceptable only as a temporary fallback, but it is weaker UX and still leaves Recent with a dead-feeling row for normal history items.

**MVP recommendation:** add a small `TrackContextScreen` route for Recent track taps.

Suggested route/data shape:

- Route: `track/{trackId}` or `recent/track/{trackId}`.
- Initial display metadata can come from the local Recent entry: title, artist, album, artworkUrl.
- Fetch/fill richer track metadata when network is available using existing `TidalApiClient.track(id)` if needed.
- Never start playback on screen entry.

Suggested watch-sized actions:

1. `Play` — explicit action that calls `onPlayTrack(track)`.
2. `View album` — enabled only when `albumId` exists; otherwise show `Album unavailable`.
3. `View artist` — enabled only when `artistId` exists; otherwise show `Artist unavailable`.
4. `Add to playlist` — use the existing `AddToPlaylistSheet` pattern when track id is valid.
5. `Download` / `Downloaded` — keep current release constraints; if unavailable, say `Offline unavailable` or existing offline copy.

This preserves the product decision while giving track Recent rows a useful destination.

### Important — Recent writes should live at explicit UI intent boundaries, not playback/session state

The spec says to record explicit play history and exclude auto-advanced queue tracks. Current code has a clean split that supports this:

- UI helpers in `MainActivity.kt` start playback via `startTrackPlayback(track)` and `startQueuePlayback(tracks, startIndex)`.
- `TidalMediaService` handles queue progression, skip, resume, jump, and backend state.
- Auto-advance/queue transition state lives in playback service/backend code.

**Recommendation:** write Recent before/alongside the explicit UI command, not from service state updates or Now Playing state collection.

Concrete write points:

- `playTrack(track)` wrapper: write `RecentItem(type=Track, id=track.id, ...)` only after the app accepts the explicit playback request. If offline gating returns false, do not write.
- Album detail `Play Album` chip: write one album Recent entry, not the first track. This likely requires passing album metadata through a separate `onPlayAlbum(album, tracks)` or recording in `AlbumScreen` immediately before `onPlayQueue(tracks, 0)`.
- Playlist detail `Play all` chip: write one playlist Recent entry, not the first track. Same pattern as album.
- Album/Playlist individual track rows: treat as explicit track play and write a track Recent entry if that is the chosen intent.

Do **not** write from:

- `TidalMediaService.playTrack(...)` because it is also called from queue setup and previous/current-track behavior.
- backend/media-session state changes.
- `NowPlayingStateHolder`.
- resume/pause/skip/jump commands.

### Minor — Album/playlist Recent records need metadata at the call site

`MainActivity.playQueue(tracks, startIndex)` only receives tracks, so it cannot know whether the explicit intent was "Play Album", "Play Playlist", or "play this track in this queue". Album and Playlist screens do have their collection metadata locally.

**Recommendation:** do not infer collection Recent from queue contents. Add explicit collection-level callbacks (`onPlayAlbum(album, tracks)`, `onPlayPlaylist(playlist, tracks)`) or let the detail screen write the collection Recent before invoking queue playback. Inference from `track.album` or playlist title will be fragile.

### Minor — Sign-out clearing needs an explicit hook

The spec says Recent should clear on sign-out with auth/local sensitive state. Current auth sign-out clears auth preferences in `TidalAuthRepository`, while `SettingsScreen` only navigates via `onSignedOut`. A separate Recent store will need an explicit clear path when sign-out succeeds.

**Recommendation:** add a small `clearRecent()` call to the sign-out success path rather than relying on auth prefs clearing unrelated app-local storage.

## Recommended MVP acceptance language for track tap

Replace/resolve the open track-detail gap with:

> Track Recent tap opens a minimal Track Context screen populated from local Recent metadata and refreshed from TIDAL when available. The screen does not autoplay. It shows title, artist, album, artwork, and explicit actions: Play, View album when `albumId` exists, View artist when `artistId` exists, Add to playlist, and Download/Offline status when eligible. If richer metadata cannot be loaded, the screen still opens from local metadata and disables unavailable actions with clear copy.

This is the smallest coherent MVP because it avoids silent autoplay, avoids dead rows, and reuses already-proven player action concepts without making Now Playing pretend to be metadata detail.

## Verification

- Review only; no production code changed.
- `git diff --check` run after writing this document.
