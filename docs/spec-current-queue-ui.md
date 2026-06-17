# Feature Spec #3 — Current Queue UI

_Last updated: 2026-06-16_

## Status

Spec only. No production code changes are included with this document.

## PM problem statement

Untidy can start album, playlist, artist-top-track, discover-section, and single-track playback as a queue, but the player UI does not let a listener inspect or act on the active queue. On a watch this creates avoidable uncertainty: after tapping a track inside a long list, the user cannot quickly answer “what is playing now?”, “what comes next?”, or “can I jump to the song I meant?” without leaving playback and reconstructing the original album/playlist context.

The Queue UI should make the active playback queue visible and actionable from the Now Playing experience without turning the watch into a phone-sized playlist manager. The goal is a small, confidence-building queue surface: see current, previous, and upcoming tracks; jump to another item; understand when the queue is unavailable; and return to playback with minimal gesture cost.

## Goals

- Add a clear **Queue** entry point from the player secondary actions / pull-under screen.
- Show the active queue in a Wear-friendly list optimized for small round screens.
- Highlight the current item and expose nearby context: previous, current, next.
- Support tap-to-jump within the active queue.
- Preserve existing transport controls for previous/next/play/pause.
- Make queue-unavailable states honest rather than silent.
- Keep the first implementation no-emulator-required at the design/spec level, then implement with small testable service/state changes.

## Non-goals for v1

- Full queue editing: reorder, remove, swipe-to-delete, drag handles.
- “Play next” / “add to queue” from search or library.
- Cross-device TIDAL Connect queue sync.
- Offline/download queue management.
- Infinite/cursor-backed remote queue loading beyond Untidy’s current 100-track watch queue cap.
- Lyrics, credits, recommendations, or desktop-style queue metadata.

## Current product/technical context

- `TidalPlayerScreen` already has a two-page vertical pager:
  - Page 0: Now Playing with art, like, queue icon placeholder, title/artist, previous/play/next.
  - Page 1: `ActionsSheet`, a pull-under/secondary action surface with download, output, add to playlist, view album, view artist.
- The main Now Playing page already imports and renders a `QueueMusic` icon, but the current behavior is a placeholder toast: “Queue coming soon.”
- Playback service state already tracks:
  - `currentQueue: List<TidalTrack>`
  - `currentQueueIndex: Int`
  - previous/next actions through `skipToPreviousInQueue()` and `skipToNextInQueue()`
- Queue handoff is now process-death-tolerant via `PlaybackQueueStore.payloadFor(...)` fallback, while keeping the watch-oriented 100-track cap.
- `NowPlayingStateHolder` currently exposes only the current track, position/duration, play state, volume, and error. It does **not** expose queue contents or queue index.

## UX principles for small round Wear screens

1. **One primary action per view.** The player remains about playback; the queue sheet is about selecting what to hear next.
2. **Center-weighted information.** On round screens, the center row is easiest to read. The current item should sit near the vertical center when possible.
3. **Large tap targets.** Rows should be at least ~48dp high; dense phone-style list rows are not acceptable.
4. **Short labels, graceful truncation.** Track title first, artist second. One-line title, one-line artist; fade/ellipsis rather than wrapping into cramped rows.
5. **Rotary-first scrolling.** The queue list must work with touch and crown/bezel scrolling. Tap targets should not require precision.
6. **Fast escape.** Back returns to the player; vertical swipe/page returns to the Now Playing card if using pager integration.
7. **No mystery states.** Empty/unavailable queue states need explicit copy and a recovery action.

## Entry point and navigation flow

### Required entry point: player action sheet / pull-under screen

The user requirement is explicit: **there should be a button to view the current queue from a pull-under screen / secondary player actions surface.**

Add a first-class row near the top of `ActionsSheet`:

1. **Queue** — icon `QueueMusic`; subtitle/count if available, e.g. `12 tracks · 4/12`.
2. Download.
3. Output.
4. Add to playlist.
5. View album.
6. View artist.

Recommended ordering: Queue should be above Download because it is playback-session navigation, not library/device management. On a watch, the most likely secondary playback need is “what’s next?”

### Optional shortcut: Now Playing corner icon

The existing queue icon on the main player page can remain as a shortcut, but it should not be the only entry point. The icon is small and peripheral on a round face; the action-sheet row is more discoverable and satisfies the requirement.

Proposed behavior:

- Tap queue icon on main player page → open Queue screen/sheet directly.
- Pull under to actions → tap Queue row → open Queue screen/sheet.
- Back from Queue → return to the previous player page/sheet.

### Navigation model recommendation

Use a third vertical-pager page or a nested modal-like full-screen queue panel inside `PlayerActivity`, not a separate top-level app destination for v1.

Recommended v1 shape:

- Page 0: Now Playing.
- Page 1: Actions.
- Queue opened from page 0 or page 1 as an in-player sub-surface with its own `BackHandler`.

Rationale:

- The queue is transient player state, not a durable browse destination.
- It should feel like part of Now Playing.
- A separate `Activity` would complicate return behavior and state sharing.
- A Nav route can still be introduced later if the player gains more nested surfaces.

## Queue screen layout

### Header

Compact header at top, respecting round screen clipping:

- Title: `Queue`
- Secondary text when known: `{currentPosition}/{total} tracks`, e.g. `4/18 tracks`
- Small drag handle or top affordance if presented from pull-under context.

For 192dp round screens, keep the header minimal:

```text
Queue
4/18
```

### List rows

Each row:

- Left: small state indicator or compact index.
- Center: title + artist.
- Optional right: duration only if it does not crowd the row; omit for v1 if layout is tight.

Row states:

- **Current item:** cyan accent, equalizer/playing indicator or `Now` label, bold title.
- **Previous items:** muted text, optional check/played style; still tappable for jump-back.
- **Upcoming items:** normal text.
- **Unplayable/missing id:** disabled/muted, not tappable, optional `Unavailable` text.

Recommended row copy:

- Current: `Now · {title}` or title plus a `Now` pill.
- Next item can optionally show `Next` in muted/cyan caption for the immediate next row.

### Initial scroll position

When the Queue opens:

- If queue has items, scroll so current item is centered or slightly above center.
- If current index is 0, show row 0 near top with enough upcoming context.
- If current index is near the end, show the current and prior context.

This is especially important for long album/playlist queues where opening at the top would be disorienting.

### Controls inside queue

v1 should avoid duplicating the whole player. Provide only these queue-specific actions:

- Tap current item: no-op or brief haptic; do not restart unless explicitly selected again after confirmation. Recommendation: no-op.
- Tap previous/upcoming item: jump to that index and begin playback.
- Hardware/back gesture: close queue.
- Rotary: scroll list.

Transport controls remain on the main player page. If testing shows users want play/pause while viewing queue, add a sticky tiny play/pause footer in v2, but avoid it in v1 to preserve list space.

## Queue behavior requirements

### Current item

The queue UI must show exactly one current item when queue state is known.

- Current item is derived from service-owned `currentQueueIndex`, not by matching title/artist.
- If the current track is not in the active queue, show a single-item “Now playing” state rather than fabricating a queue.
- If playback starts from `ACTION_PLAY_TRACK` without queue, queue state should be `Single track` or `No queue` depending on data availability.

### Next / previous

- Existing previous/next buttons continue to call service actions.
- Queue UI should react to index changes after next/previous.
- Previous behavior remains service-owned:
  - If previous exists, move to previous queue item.
  - If no previous exists, replay/reload current track per current service behavior.
- Queue rows before and after the current index are visual context, not independent queues.

### Tap-to-jump

Tap-to-jump is a core v1 behavior.

Expected behavior:

1. User opens Queue.
2. User taps any enabled row with index `N`.
3. App sends a jump action to playback service.
4. Service validates `N` against current queue bounds.
5. Service sets `currentQueueIndex = N` and plays that track.
6. UI immediately updates current row and optionally closes back to Now Playing.

Recommendation: after a successful jump, return to Now Playing automatically. This matches watch ergonomics: selection is the action, and the user likely wants playback confirmation. If usability testing suggests browsing multiple jumps, make auto-return configurable or delay it.

### Queue count and cap disclosure

Untidy currently caps watch queues to 100 playable tracks. The Queue UI should not over-explain this in normal use, but it should avoid lying.

- If the source list had more than 100 tracks and the service only knows the capped queue, v1 may simply show the known queue count.
- Future data model can expose `isTruncated` and `originalCount` so the empty/footer text can say `Showing first 100 tracks on watch`.

## Empty, loading, and error states

### No active queue

When playback is idle or started without a queue:

Title: `No queue yet`

Body: `Start an album, playlist, or track list to see what’s next.`

Action: `Browse library` is optional but not required for v1. Since this surface is inside PlayerActivity, the simplest v1 action is `Back to player`.

### Single-track playback

If one track is playing without a list:

Title: `Single track`

Show one current row if metadata is available. Body/footer: `Start an album or playlist for an upcoming queue.`

### Queue unavailable / service reconnecting

When the controller is connected but queue extras have not arrived:

Title: `Queue unavailable`

Body: `Playback is active, but Untidy can’t read the queue yet.`

Recommendation: use this only after a short grace period; otherwise show lightweight loading.

### Empty after failed handoff

If service reports `queueSize = 0` for a queue-start attempt:

Title: `Couldn’t load queue`

Body: `Try starting playback again from the album or playlist.`

This should pair with existing playback error surfacing when applicable.

### Track-level unavailable row

Disabled row copy:

- Title from metadata if available.
- Caption: `Unavailable`.

Do not attempt playback for blank/missing IDs.

## Technical architecture

### Ownership boundary

The playback service should remain the source of truth for the active queue.

- `TidalMediaService` owns `currentQueue` and `currentQueueIndex`.
- UI observes queue snapshot state through Media3 session/controller state.
- UI sends queue commands back to service; it should not mutate queue state locally.

### Recommended queue state exposure

Expose a compact queue snapshot from service to UI through Media3 custom layout/extras or a custom session command. For v1, the least invasive reliable approach is:

1. Add serializable queue snapshot data in `:core:model` or `:core:playback`:
   - `PlaybackQueueSnapshot`
   - `PlaybackQueueItem`
2. Service publishes snapshot whenever queue/index/current track changes.
3. `NowPlayingStateHolder` reads snapshot and adds it to `NowPlayingUiState`.
4. Queue UI renders from `NowPlayingUiState.queue`.

Potential transport options:

#### Option A — Media metadata/session extras

Use Media3 metadata/extras to attach:

- `queuePayload` JSON for capped queue.
- `queueIndex` integer.
- `queueUpdatedAt` monotonic/version value.

Pros:

- Simple to observe from existing `MediaController` listener.
- No new command flow for reads.

Cons:

- Large extras may be awkward; 100 tracks with titles/artists/artwork can be bigger than ideal.
- Need care around binder size and update frequency.

#### Option B — Custom session command for snapshot

Define custom Media3 command, e.g. `com.tidal.wear.command.GET_QUEUE`, returning a bundle payload.

Pros:

- Pulled only when Queue screen opens or when a version changes.
- Better for avoiding oversized always-on metadata.

Cons:

- More plumbing and async state in `NowPlayingStateHolder`.

#### Option C — In-process app playback repository

Use a singleton/repository flow shared by service and app process.

Pros:

- Simple when app and service are in the same process.

Cons:

- Less robust across process death and not as clean as session/controller state.

Recommendation: **Option B for production shape**, with Option A acceptable for a very small first pass if the JSON payload remains bounded and tested. Given Wear constraints and the existing 100-track cap, Option A may be the fastest implementation; Option B is cleaner if queue UI grows.

### Commands needed

Add service action or Media3 custom command:

- `ACTION_JUMP_TO_QUEUE_INDEX` or `COMMAND_JUMP_TO_QUEUE_INDEX`
- Extra: `EXTRA_QUEUE_INDEX: Int`

Service validation:

```kotlin
private fun jumpToQueueIndex(index: Int) {
    if (currentQueue.isEmpty()) return
    val bounded = index.takeIf { it in currentQueue.indices } ?: return
    currentQueueIndex = bounded
    val track = currentQueue[bounded]
    playTrack(track.id, track)
}
```

Prefer a service/custom command over direct UI manipulation so Bluetooth/headset/media-session state remains consistent.

### Compose surface

Add a new composable under `app/src/main/java/com/tidal/wear/ui/player/`, for example:

- `QueueSheet.kt`
  - `QueueSheet(state, onJumpToIndex, onBack)`
  - internal `QueueRow(...)`

Integrate from `TidalPlayerScreen`:

- Add `showQueue` local state or pager page.
- Pass `onViewQueue` into `ActionsSheet`.
- Replace current queue-icon toast with `showQueue = true`.
- `BackHandler(enabled = showQueue) { showQueue = false }`.

### Accessibility / semantics

Rows should expose concise content descriptions:

- Current: `Now playing, {title}, {artist}`
- Upcoming: `Play {title} by {artist}`
- Previous: `Replay {title} by {artist}`
- Disabled: `{title}, unavailable`

The action-sheet row should be `View queue`.

## Data model needs

Recommended model:

```kotlin
data class PlaybackQueueSnapshot(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val sourceTitle: String? = null,
    val originalCount: Int? = null,
    val isTruncated: Boolean = false,
    val version: Long = 0L,
)

data class PlaybackQueueItem(
    val index: Int,
    val track: TidalTrack,
    val playable: Boolean = track.id.isNotBlank(),
)
```

Minimum v1 fields:

- items: `List<TidalTrack>` or `List<PlaybackQueueItem>`
- currentIndex: `Int`
- version/update token: optional but useful for recomposition and custom-command refresh

Nice-to-have v1.1 fields:

- source type: album / playlist / artist / discover / unknown
- source title: album or playlist name
- original count and truncation flag
- queue id for diagnostics only, not UI logic

## Phased implementation plan

### Phase 0 — Spec and design review

- Land this spec.
- Confirm queue entry placement in `ActionsSheet` and optional main-player shortcut.
- Decide Option A vs Option B for queue snapshot transport.

No emulator required.

### Phase 1 — Read-only Queue UI

Deliver:

- `QueueSheet` composable.
- `ActionsSheet` row: `Queue` / `View queue`.
- Main player queue icon opens the queue instead of toast.
- `NowPlayingUiState` includes queue snapshot.
- Current row highlighted.
- Empty/single-track/unavailable states.

Implementation notes:

- No tap-to-jump yet if splitting risk is helpful.
- Unit-test snapshot serialization/parsing if using JSON extras.
- JVM tests only; emulator not required for initial compile confidence.

### Phase 2 — Tap-to-jump

Deliver:

- Service action/custom command to jump to index.
- `NowPlayingViewModel.jumpToQueueIndex(index)`.
- Row click behavior.
- Bounds validation in service.
- UI update after jump; recommended auto-return to Now Playing.

Tests:

- JVM/fake service logic test for valid/invalid indexes if seam allows.
- Serialization test for queue snapshot.
- Compile/lint gate.

### Phase 3 — Polish for watch ergonomics

Deliver:

- Initial scroll-to-current.
- Haptic feedback on row jump.
- `Next` label for immediate next item.
- Better footer for 100-track cap when `isTruncated` is available.
- Accessibility descriptions.

### Phase 4 — Future queue management

Potential follow-ups only after v1 validates:

- Remove from queue.
- Reorder queue.
- Add to queue / play next from library/search.
- Source-aware queue headers and return-to-source links.
- Larger remote/cursor-backed queues.

## Acceptance criteria

- Pull-under/player action sheet includes a visible Queue row/button.
- Queue can be opened without leaving the player context.
- Queue list renders correctly on small round screens with rotary scrolling.
- Current item is visually distinct.
- Previous and next items are visible when available.
- Tapping a non-current enabled item jumps playback to that queue index.
- Empty, single-track, unavailable, and error states have clear copy.
- Existing previous/play/next transport controls still work.
- No emulator is required for the initial implementation gate; use compile/lint/JVM tests.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Queue snapshot payload too large for extras | Keep 100-track cap; consider custom command pull model; avoid artwork bitmaps, URLs only. |
| UI and service queue state diverge | Service remains source of truth; UI sends commands and observes snapshots only. |
| Round-screen clipping makes rows hard to tap | 48dp rows, center-weighted list, rotary scroll, short text. |
| Jump command races with current load/play | Service serializes jump through existing `playTrack` coroutine path; ignore invalid indexes. |
| Single-track playback looks broken | Explicit `Single track` state instead of empty queue. |

## Recommendation

Implement this as a small, staged player feature, starting with the action-sheet Queue row and read-only queue snapshot. The highest-value path is:

1. Add queue snapshot model/exposure from `TidalMediaService` to `NowPlayingStateHolder`.
2. Add `QueueSheet` opened from `ActionsSheet` and the existing main-player queue icon.
3. Add service-owned tap-to-jump once read-only rendering is stable.

Do not implement reorder/remove in the first pass. The core UX win is visibility plus jump-to-track; that is enough to make album/playlist playback feel trustworthy on the watch.
