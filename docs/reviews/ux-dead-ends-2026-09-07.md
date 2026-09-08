# Navigation and recovery audit — 2026-09-07

Scope: beta 5 source (`3721daf` plus documentation commit `5af271a`). These are findings for review, not changes to the affected flows. The separately requested output-picker window is the only UI implementation in this pass.

## High priority

### 1. A second artist page can be mistaken for the return-to-player root

Trigger: Now Playing → View artist A → related artist B → Back.

`MainActivity.kt:230` tracks `destination.route`, a pattern such as `artist/{artistId}`. `planExternalRoute` also stores a pattern as `returnToPlayerRoot`. At line 459, any destination with that pattern invokes `finish()`, even if it is a different artist deeper in the stack. The same problem applies to repeated album destinations. The resulting Back operation can skip the intermediate browsing history.

Confidence: direct code finding; this exact nested-artist path still needs hardware reproduction. Existing unit tests exercise route planning, not stack identity.

Recommendation: remember the actual root back-stack entry ID and compare entry identity, with a common policy for physical Back and swipe dismiss.

### 2. Clearing history targets a destination that may already have been removed

Trigger: sign in, browse multiple pages, then use the player Home action or a player-originated artist/album route.

`MainActivity.kt:828` implements `navigateAndClear` by popping inclusively to `graph.startDestinationId`. The graph starts at Onboarding, and the first navigation to Home removes Onboarding. Later calls can therefore target a destination no longer in the stack and retain the previous history. This conflicts with the `clearBackStack` policy and can expose stale screens on Back or swipe dismiss.

Confidence: direct code finding; resulting stack depends on entry history. Earlier successful navigation smoke tests did not establish every history-clearing case.

Recommendation: clear to the graph/root entry that is still present, then validate Home → deep browsing → player → Home and repeated artist/album round trips.

### 3. Rejected playback can strand the user on a loading screen

Trigger: cold session, no network, tap a track that is not downloaded. A manifest/controller failure before metadata loads has a similar result.

The playback helpers in `MainActivity.kt:269–291` call `openPlayer()` even if playback was rejected. With no current track, `TidalPlayerScreen.kt:527` renders `PlayerLoadingState`; its controls, action grip, and error line are all inside the `loadedTrack != null` branch at line 333. The user sees “Loading track…” without a retry or Home action. Physical Back still escapes, so this is a recovery dead end rather than an inescapable activity.

Recommendation: remain on the originating screen when a start is rejected; give an empty/failed player an explicit recovery state with Retry and Home.

### 4. “Downloaded” playback still requires a network manifest

Trigger: start a downloaded track after losing connectivity.

`DirectManifestPlaybackBackend.kt:286` resolves a fresh manifest before opening the downloaded cache. `fetchPlaybackManifest` at line 384 obtains an access token and calls TIDAL, including a DOWNLOAD manifest request at line 392. The cache also has a network upstream at line 300. A downloaded marker therefore does not guarantee an offline start or that every played byte comes from local storage.

Hardware evidence: starting the downloaded “The Memory of Trees” requested a DOWNLOAD manifest, received a HiRes entitlement rejection, then resolved FLAC before opening the cache. No network-disconnection test was performed because it would interrupt wireless ADB.

Recommendation: persist the authorized download manifest and format together with the completed download; start local playback from that descriptor. Validate cold offline starts, expired authentication, and settings changes without silently falling back to streaming.

## Medium priority / lost context

### 5. Returning to Search may discard the query/results and reopen input

Trigger: Search → album/artist → Back; also activity recreation while viewing results.

`SearchScreen.kt:90–95` stores the query, results, and input state with `remember`. Line 159 launches the system input activity on every fresh composition. Recreating that destination can replace the original results with another keyboard/voice prompt.

Confidence: source-derived lifecycle risk; destination disposal behavior and exact hardware return paths need reproduction.

Recommendation: keep query/results in a back-stack-scoped state holder and only launch input automatically for a new search.

### 6. Ambient mode discards the current player sub-screen

Trigger: open Queue, the actions menu, or Add to playlist; let the watch enter ambient, then wake it.

`TidalPlayerScreen.kt:177` replaces the entire interactive subtree with the ambient view. Queue, actions, and playlist state are held in that subtree with `remember` at lines 227–231. They are lost when it leaves composition. A pending playlist operation uses that subtree's coroutine scope and may be cancelled while a server write is in flight.

Recommendation: retain navigation and pending-operation state above the ambient/interactive branch. Suspend rendering and input work while preserving the user's destination.

### 7. A failed Recent-track metadata lookup has no retry

Trigger: open an older Recent track with incomplete album/artist IDs during a temporary API failure.

`TrackContextScreen.kt:83–88` ignores lookup failures, clears loading, and offers no retry. Album/artist actions at lines 108/112 then only show “unavailable.” Returning connectivity does not rerun the effect, whose sole key is `trackId`.

Recommendation: expose a small retry action and distinguish missing metadata from a failed lookup.

### 8. Add to playlist temporarily swallows Back during a write

Trigger: select a playlist on a slow connection, then press Back.

`AddToPlaylistSheet.kt:155` handles Back but does nothing while `addingTarget` is set. The adding view at line 193 has no cancel action. This is bounded by the request timeout, not a permanent trap. Release builds also have no Create playlist option when the account has zero editable playlists; that state offers Reload only.

Recommendation: define safe navigation away from an in-flight write, preserving its eventual result, and tell users how to create a playlist when none is available.

## Existing recovery paths retained

- Album, playlist, artist, and artist-albums request failures provide Retry actions.
- Library has retry/offline recovery and a Back handler for category selection.
- Search cancellation already falls back to Home if its stack cannot be popped.
- Queue and Add to playlist have local Back handlers outside the in-flight write case.
- The new output window offers physical Back, Wear swipe-to-dismiss, and an explicit Back row even with zero outputs; dismissal leaves the action menu composed underneath.

Recommended fix order: root-entry identity and stack clearing together; empty-player recovery; downloaded-start reliability; then retained Search/player context and smaller retry improvements.

## Resolution in 0.6.0-beta.6

All eight findings above were addressed in the beta 6 working tree. Return-to-player uses concrete back-stack entry identity and stack clearing targets the persistent graph entry. Rejected and failed playback now has Home/Retry recovery, Search retains its session in a destination ViewModel, ambient player state is retained, Recent details offers retry, and playlist writes may finish after dismiss without swallowing Back.

Completed downloads now atomically persist their authorized DASH descriptor and full track metadata, and playback uses a cache-only source before any authentication or metadata lookup. Legacy/incomplete downloads present an explicit non-destructive Repair action; their existing cache remains until a replacement succeeds. Download requests honor the selected audio preset and retain that downloaded quality if the streaming preset later changes.

Validation: lint, release assembly, and 95 JVM tests passed. On Galaxy Watch6 hardware, the legacy beta 5 download showed the repair state, repaired successfully, and then cold-started as `source=offline-download-descriptor` with FLAC from the local cache. The output picker opened in its own Wear dialog and physical Back returned to the pull-up at the preserved scroll position. A complete combinatorial navigation traversal remains valuable regression automation; this pass covered policies and the formerly observed hardware path.
