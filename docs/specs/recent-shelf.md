# Recent Shelf Feature Spec

## Status

- GitHub: #39 / UNTIDY-038
- Stage: Spec
- Owner: Tommy / Ralph as delegated
- Created: 2026-06-18

## Problem

Search is painful on a watch, and most listening sessions revolve around a small handful of items. Untidy needs a quick way to return to things the user explicitly chose to play without re-searching or digging through Library.

## Product decision summary

Add a top-level **Recent** shelf on Home for watch-local explicit play history.

Important decisions from Ric:

- Tapping a Recent item opens the item's metadata/detail/context page, **not** immediate autoplay.
- Recent should include only items the user explicitly clicked play on.
- Do **not** include artists because Untidy has no explicit `Play Artist` action.
- Do **not** add auto-advanced queue tracks; that would flood the list.

## Item types

Allowed in MVP:

- `track`
- `album`
- `playlist`

Not allowed in MVP:

- `artist`
- search query
- downloaded group
- auto-advanced queue item
- recommendation/mix unless user explicitly tapped play on that concrete playable item and it maps cleanly to album/playlist/track

## Write rules

Record one Recent entry when the user explicitly starts playback from:

- track row / explicit Play Track
- album Play Album
- playlist Play Playlist

Do not record when:

- queue auto-advances to the next track
- user opens a detail page without playing
- user uses Now Playing resume/pause/skip controls
- app restores playback after process/service restart
- media session/controller resumes existing playback

Dedupe:

- Key by `type + id`.
- If an entry already exists, update `lastPlayedAt` and latest display metadata, then move it to top.

Capacity:

- Cap at 20 items for MVP.
- Drop oldest item beyond cap.

Storage:

- Local watch-only storage.
- No TIDAL API dependency.
- No cross-device sync.

## Data model

```kotlin
enum class RecentItemType { Track, Album, Playlist }

data class RecentItem(
    val type: RecentItemType,
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val lastPlayedAt: Long,
)
```

Field semantics:

- Track subtitle: artist if available.
- Album subtitle: artist if available; fallback `Album`.
- Playlist subtitle: `Playlist` or owner/description if already available.

## Home placement

Recommended Home order:

1. Search
2. Recent
3. Downloads
4. Library
5. Discover
6. Settings

Reasoning:

- Search remains primary for new intent.
- Recent is the fastest return path.
- Downloads is the offline shelf.
- Library/Discover remain deeper browse paths.

Home chip copy:

- Label: `Recent`
- Secondary: `Things you played`

## Recent screen

Screen title: `Recent`

Rows:

- Mixed list of track/album/playlist entries, most recent first.
- Use existing TidalResultChip / Wear list grammar.
- Include concise type signal where useful:
  - `Track` / artist
  - `Album` / artist
  - `Playlist`

Empty state:

- `Nothing recent yet`
- `Play music to build this list`

## Tap behavior

Tapping opens context/detail, not autoplay.

- Album → Album detail route.
- Playlist → Playlist detail route.
- Track → track context/detail page if available.

Track detail gap:

Untidy may not yet have a standalone Track detail page. The implementation must resolve this before coding track tap behavior.

Preferred MVP options, in order:

1. Add a minimal Track context screen/sheet with title, artist, album, and actions: `Play`, `View album` if album id exists, `View artist` if artist id exists, `Add to playlist`, `Download` if eligible.
2. If minimal Track context is too much for first slice, track rows may open Now Playing **only when the same track is currently loaded**; otherwise track tap should show a clear disabled/context message. Do not silently autoplay.

## Offline behavior

Recent is intent history, not a storage manager.

- Downloaded items can show a small downloaded signal later.
- If network is unavailable and item is not downloaded:
  - row remains visible
  - tap opens detail/context if possible
  - play action from detail says `Not downloaded` / `Connect to stream this track`
- Downloads screen remains the primary offline storage shelf.

## Privacy/account behavior

- Recent is local to this watch.
- No server writes.
- On sign-out, clear Recent along with auth/local sensitive state unless Ric explicitly chooses account-retained local history later.

## Acceptance criteria

Spec/review:

- [ ] UX/dev review confirms type set, write rules, tap behavior, and track detail fallback.

Implementation:

- [ ] Home includes `Recent` chip.
- [ ] Recent screen exists.
- [ ] Explicit Play Track writes track recent.
- [ ] Play Album writes album recent.
- [ ] Play Playlist writes playlist recent.
- [ ] Queue auto-advance does not write track recent.
- [ ] No artists are written/displayed.
- [ ] Dedupe by `type + id` and cap at 20.
- [ ] Recent persists across app restart.
- [ ] Tapping album opens album detail.
- [ ] Tapping playlist opens playlist detail.
- [ ] Track tap uses approved track-context behavior; no silent autoplay.

Verification:

- [ ] Unit tests for store/dedupe/cap/no-artist semantics.
- [ ] Compile/lint gate passes.
- [ ] Emulator smoke captures Home → Recent and at least one item state.
- [ ] Physical watch validation: Recent reachable/readable and detail navigation sane.
