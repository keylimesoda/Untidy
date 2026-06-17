# Feature Spec: Add Current Track to Playlist

_Last updated: 2026-06-16_

## Summary

Add a Wear-first flow that lets the user add the currently playing TIDAL track to either an existing playlist or a newly created playlist from the player action sheet.

This document is a product/UX/engineering spec only. It does not implement the feature.

## Goals

- Make the existing `Add to playlist` action sheet row functional.
- Support two user intents:
  1. Add the current track to an existing editable playlist.
  2. Create a new playlist, then add the current track to it.
- Keep the flow usable on small round Wear OS screens with short labels, clear loading, and simple recovery.
- Use the existing authenticated TIDAL API client/auth repository patterns where possible.
- Avoid playback interruption: adding to a playlist should be a side action, not a transport action.

## Non-goals

- Full playlist management: reorder, remove tracks, edit metadata, delete playlists, cover art, collaboration, or privacy controls beyond what is required to create a playlist.
- Adding anything other than the current track.
- Offline playlist sync/download.
- Emulator/device validation in this spec phase.
- Production code changes in this spec phase.

## Current app context

Known code shape at time of spec:

- `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
  - Already contains: `ActionRow(Icons.Filled.PlaylistAdd, "Add to playlist", ..., onAddToPlaylist)`.
- `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
  - Current `onAddToPlaylist` handler is a placeholder toast: `"Coming soon"`.
  - Player state already exposes `NowPlayingUiState.track` via `NowPlayingStateHolder`.
- Auth scopes already include `playlists.read playlists.write` in app/core auth Gradle and auth repository defaults.
- `core/tidal-api/src/main/java/com/tidal/wear/core/api/TidalApiClient.kt`
  - Has read/list patterns for playlists: `favorites()`, `playlist(id)`, `playlistTracks(id)` and v2 `playlists/{id}` / `playlists/{id}/relationships/items` reads.
  - Likely does not yet have write methods for creating playlists or adding playlist items.
- `core/model/src/main/java/com/tidal/wear/core/model/TidalModels.kt`
  - `TidalPlaylist` currently has `id`, `title`, `creator`, `artworkUrl` only.

## User stories

1. As a listener, when I hear a track I want to save, I can add it to a playlist from the player without opening the phone.
2. As a listener, I can create a quick new playlist from the watch and immediately add the current track to it.
3. As a listener, I get a clear success confirmation and can continue listening without losing my place.
4. As a listener on an unreliable connection, I can retry the failed add without navigating back through the whole flow.

## Entry point

### Primary entry

Player screen → vertical pager page 2/action sheet → `Add to playlist` row.

### Preconditions

The action is enabled only when:

- User is authenticated with a user token, not anonymous/client-only catalog mode.
- `NowPlayingUiState.track != null`.
- `track.id` is non-blank and not a fixture/synthetic id.
- The current track is a TIDAL catalog track that can be referenced by playlist APIs.

### Disabled/blocked handling

If preconditions fail, do not open a broken picker.

- No current track: show toast/snackbar-style transient message: `Nothing playing`.
- Fixture/synthetic/current media id missing: `Track unavailable`.
- Not signed in or token expired: show `Sign in to edit playlists` with one primary action to open/sign-in flow if this is reachable from current navigation architecture; otherwise show a dismissible error.
- Missing scope/403: show `Playlist access denied` and recommend re-auth in details/logs.

## Recommended navigation model

Use a modal, player-owned flow rather than a full app navigation route for v1.

Reasoning:

- The user starts from `PlayerActivity`, which is separate from the main app nav graph.
- The action is contextual to the current track.
- The flow should return to playback after success/cancel.
- A bottom-sheet/full-screen modal Compose state is simpler than cross-activity routing for v1.

Recommended UI states in `TidalPlayerScreen` or a dedicated child composable:

```kotlin
sealed interface AddToPlaylistFlowState {
    data object Closed : AddToPlaylistFlowState
    data class LoadingPlaylists(val track: TidalTrack) : AddToPlaylistFlowState
    data class ChoosingPlaylist(val track: TidalTrack, val playlists: List<EditablePlaylistSummary>) : AddToPlaylistFlowState
    data class CreatingPlaylist(val track: TidalTrack, val draftName: String = "") : AddToPlaylistFlowState
    data class Adding(val track: TidalTrack, val target: PlaylistTarget) : AddToPlaylistFlowState
    data class Success(val track: TidalTrack, val playlistName: String) : AddToPlaylistFlowState
    data class Error(val track: TidalTrack, val message: String, val retry: RetryAction?) : AddToPlaylistFlowState
}
```

This shape is illustrative, not implementation-required.

## Full workflow

### Flow A: Add to existing playlist

1. User opens player action sheet.
2. User taps `Add to playlist`.
3. App validates current track and user auth.
4. App opens `Add to playlist` modal/screen.
5. App loads editable playlists.
   - Show compact loading state: `Loading playlists…`.
   - Keep playback running.
6. App displays playlist chooser:
   - First row: `+ New playlist`.
   - Then existing playlists, sorted by most recently modified if API provides it; otherwise existing library order.
   - Each row: playlist title, optional creator/track count if known, optional artwork thumbnail if already available.
7. User selects an existing playlist.
8. App enters adding state:
   - Disable repeated taps.
   - Show `Adding…` on selected row or centered state.
9. App calls playlist item add API.
10. On success:
    - Show success confirmation: `Added to <playlist name>`.
    - Haptic confirmation if available.
    - Auto-close to player/action sheet after ~1 second, or leave a `Done` button if auto-close is too surprising in device testing.
11. On failure:
    - Show an inline error with `Retry` and `Cancel`.
    - Keep the selected playlist context so retry is one tap.

### Flow B: Create new playlist, then add current track

1. User opens player action sheet.
2. User taps `Add to playlist`.
3. App loads playlist chooser.
4. User taps `+ New playlist`.
5. App opens create playlist screen.
6. App proposes a default name:
   - Preferred: `Untidy playlist` if text input is painful.
   - Better v1.1: `New playlist` plus date or track artist, e.g. `Untidy – Jun 16`.
7. User can edit the playlist name using Wear text input/IME.
8. User taps `Create`.
9. App creates playlist via write API.
10. App immediately adds current track to the newly created playlist.
11. On success:
    - Show `Created <playlist name>` and `Added track` or a single combined message: `Added to <playlist name>`.
12. On create failure:
    - Show `Couldn’t create playlist` with retry.
    - Preserve draft name.
13. On create success but add failure:
    - Show `Playlist created, track not added` with `Retry add`.
    - Do not silently create an empty playlist and close.
    - Include the new playlist in retry target.

### Flow C: Cancel/back

- Back/crown/back gesture from chooser returns to player action sheet or player page, depending on current sheet stack behavior.
- Back from create form returns to chooser with loaded playlists preserved.
- Back during network `Adding…` should either be disabled for the brief request or interpreted as cancel UI only; do not assume HTTP request cancellation means the server did not mutate state.

## Wear UX requirements

### Layout

- Use a full-screen Wear modal/screen rather than a phone-style bottom sheet; round screens have too little vertical room for nested sheets.
- Keep rows 48dp-ish like existing `ActionRow` patterns.
- Center content inside safe circular bounds; avoid critical text at corners.
- Use `TransformingLazyColumn` or equivalent Wear list component for playlist chooser.
- Use large tap targets and short labels.

### Text

- Header: `Add to playlist`.
- Current track context: one compact line, e.g. `Track Title · Artist`, max 1 line each or 2 lines total.
- Existing playlist row title max 1 line with ellipsis.
- Create row: `+ New playlist`.
- Primary action labels: `Create`, `Retry`, `Cancel`, `Done`.

### Input constraints

Text input is the hardest part on a watch. Recommended phased approach:

- v1: provide default playlist name and allow edit if the platform IME is reliable; creation can work with one tap.
- v1 fallback: if text entry cannot be made acceptable, hide create-new behind `New playlist` → default name confirmation.
- v2: support voice input or better Wear text entry affordance if available.

### Feedback

- Success: cyan check icon + haptic confirmation + short message.
- Failure: red/error text + retry button; do not use only toast for actionable failures.
- Loading: simple spinner/progress indicator and text.
- Avoid long blocking overlays that obscure playback state for more than necessary.

### Ambient/playback interaction

- Do not show the add-to-playlist flow in ambient mode.
- If the watch enters ambient during the flow, close or suspend the modal and leave playback unchanged.
- Playback controls should continue via media session; the flow should not pause playback.

## Success states

### Existing playlist success

- Message: `Added to <playlist name>`.
- Optional secondary: current track title if it fits.
- Auto-dismiss after a short delay or explicit `Done`.

### New playlist success

- Message: `Added to <playlist name>`.
- If space allows: `Playlist created` as secondary text.

### Idempotent/duplicate success

If TIDAL rejects duplicates or reports the track already exists:

- Treat as a non-fatal success if the final state is that the track is in the playlist.
- Message: `Already in <playlist name>`.
- Avoid adding duplicate entries unless TIDAL playlist semantics explicitly allow duplicates and users expect that.

## Error states and retry

| Scenario | User message | Retry behavior | Notes |
|---|---|---|---|
| Network timeout | `Connection timed out` | Retry same operation | Use existing API timeout patterns; keep target selected. |
| Offline/no network | `No connection` | Retry when network returns/manual retry | Optional: detect connectivity before call. |
| 401/token expired | `Sign in again` | Re-auth required | Do not loop retries. |
| 403/missing scope | `Playlist access denied` | Re-auth/re-consent | Scopes include write already, but old tokens may not. |
| 404 playlist missing | `Playlist not found` | Reload playlists | Playlist may have been deleted on another device. |
| 409/duplicate/conflict | `Already in playlist` or `Try again` | Depends on response | Need live API validation. |
| 429 rate limit | `Too many requests` | Retry after delay | Respect `Retry-After` if present. |
| 5xx TIDAL error | `TIDAL unavailable` | Retry | Log safe reason. |
| Create succeeded, add failed | `Playlist created, track not added` | Retry add | Preserve new playlist id. |
| Track id invalid/blank | `Track unavailable` | No retry | Should be prevented at entry. |

## Loading and retry details

- Load playlists with a visible `Loading playlists…` state.
- If loading playlists fails, show:
  - `Couldn’t load playlists`
  - Buttons: `Retry`, `Cancel`.
- If adding fails after choosing a playlist, show the target name and a one-tap retry:
  - `Couldn’t add to <playlist>`
  - Buttons: `Retry`, `Cancel`.
- If creating fails, preserve draft name.
- Debounce all write actions; never send multiple add/create requests from repeated taps.

## API endpoints and unknowns

The exact TIDAL OpenAPI write endpoints need live documentation/probing before implementation. Current Untidy code uses `https://openapi.tidal.com/v2/` JSON:API-style reads plus legacy v1 fallbacks for some read flows. The write flow should prefer the official v2 API if available.

### Existing read endpoints in code

- `GET /v2/playlists/{id}?countryCode=...`
- `GET /v2/playlists/{id}/relationships/items?countryCode=...&include=items`
- Library/favorites reads include playlists via `TidalApiClient.favorites()` and legacy `GET /v1/users/{userId}/favorites/playlists` fallback.

### Endpoint candidates to verify

Likely required capabilities:

1. List current user’s editable playlists.
   - Current `favorites()` may include saved/favorite playlists, but not necessarily playlists owned/editable by the user.
   - Need to know whether TIDAL exposes `GET /users/{userId}/playlists`, `GET /my-collection/playlists`, or another endpoint for owned playlists.
2. Create playlist.
   - Candidate shape: `POST /v2/playlists` or user-scoped equivalent.
   - Need required body fields: title/name, description, privacy/public flag, user relationship.
3. Add track to playlist items.
   - Candidate shape: `POST /v2/playlists/{id}/relationships/items` or `PATCH` relationship update.
   - Need JSON:API relationship body shape for a track resource identifier.
4. Duplicate semantics.
   - Need to know whether duplicate track entries are allowed, rejected, or silently deduped.
5. Editable ownership metadata.
   - Need to know how to tell whether the signed-in user can modify a playlist.

### API implementation recommendation

Before UI implementation, run a narrow API spike with the current auth token:

- Confirm current-user id endpoint and token scopes.
- List owned/editable playlists.
- Create a test playlist with a clearly disposable name.
- Add a known current/test track.
- Re-add the same track to learn duplicate behavior.
- Delete test playlist if delete endpoint exists; otherwise use a harmless name and manually clean up.

Do not ship UI until this write contract is validated against live TIDAL responses.

## Auth scopes

Known configured scopes include:

```text
user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback
```

Feature requires:

- `user.read`: identify current user / ownership where needed.
- `playlists.read`: list existing playlists and verify target playlist metadata.
- `playlists.write`: create playlist and add item.

Potential auth edge cases:

- Existing installed users may have tokens minted before `playlists.write` was added.
- TIDAL may require re-consent for write scopes.
- Client-credentials catalog mode is insufficient; playlist write must require user auth.
- 403 should prompt re-auth/re-consent rather than repeated write retries.

## Data model needs

Existing `TidalPlaylist` is enough for display in read-only lists but likely insufficient for write UX.

Recommended new/expanded model, depending on implementation style:

```kotlin
data class EditablePlaylistSummary(
    val id: String,
    val title: String,
    val creator: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
    val editable: Boolean = true,
    val lastModified: Instant? = null,
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null,
    val visibility: PlaylistVisibility = PlaylistVisibility.Private,
)

data class AddTrackToPlaylistResult(
    val playlistId: String,
    val playlistTitle: String,
    val trackId: String,
    val outcome: AddTrackOutcome,
)

enum class AddTrackOutcome { Added, AlreadyPresent }
```

Additional state needs:

- Current track snapshot at flow entry. Do not let a track transition mid-flow silently change what gets added.
- Playlist loading cache for the duration of the modal.
- Pending operation flag/id to prevent duplicate writes.
- Safe error reason for UI plus detailed log reason for debugging.

## Edge cases

### Current track changes during flow

- Snapshot the `TidalTrack` when the user taps `Add to playlist`.
- Header should display that snapshotted track.
- If playback advances before user confirms, still add the snapshotted track.
- Optional v2: show `Adding previous track` if current playback has changed.

### No track / fixture track

- Do not enter the flow.
- Show `Track unavailable` or `Nothing playing`.

### Duplicate taps

- Disable the selected row and create button while request is in flight.
- Use an operation token or state machine to ignore repeated taps.

### Empty playlist list

- Show empty state: `No playlists yet` plus primary `New playlist`.

### Very long playlist list

- Limit initial display to a reasonable count if API returns many; add simple search only in v2.
- Preferred v1 sorting: recently modified or user-owned first.
- If no modified timestamp exists, use API order.

### Non-editable playlists

- Do not show non-editable playlists as add targets, or show disabled with `Can’t edit` if users expect to see them.
- Avoid allowing add attempts to public/editorial playlists.

### Region/catalog restrictions

- Adding may fail if track is unavailable in user region or not playlist-eligible.
- Message: `Can’t add this track`.

### Explicit/clean variants

- Use the exact current track id.
- Do not auto-swap clean/explicit variants in this feature.

### Connectivity changes

- If the request fails due to network, leave user on retry state.
- Do not queue writes offline for v1; server-side playlist state is sensitive and conflicts are likely.

### App/process death

- v1 does not need durable pending writes.
- If process dies during add, on next launch do not claim success.
- Because HTTP write may have succeeded server-side before death, duplicate retry behavior should tolerate `AlreadyPresent`.

### Token expiration mid-flow

- If playlist list succeeds but add returns 401/403, prompt re-auth/re-consent.

## Architecture recommendation

### Minimal v1 structure

- Add a small `AddToPlaylistViewModel` scoped to `PlayerActivity` or a composable state holder if keeping logic simple.
- Inject/create `TidalApiClient(TidalAuthRepositoryProvider.get(context))` similarly to existing screens.
- Keep API writes in `core/tidal-api`; do not put Retrofit calls directly in UI.
- Keep UI as a dedicated `AddToPlaylistScreen`/`AddToPlaylistModal` composable under `ui/player` or a new `ui/playlist/add` package.

### Suggested seams

- `TidalApiClient.editablePlaylists(): List<EditablePlaylistSummary>`
- `TidalApiClient.createPlaylist(request): EditablePlaylistSummary`
- `TidalApiClient.addTrackToPlaylist(playlistId, trackId): AddTrackToPlaylistResult`

These seams allow JVM tests with a fake API client or repository wrapper.

### Testing strategy for later implementation

No emulator required for initial implementation checks:

- Unit tests for state machine:
  - no track blocks entry
  - load success → chooser
  - load failure → retry
  - existing playlist add success/error
  - create success + add success
  - create success + add failure
  - duplicate result maps to `Already in playlist`
  - track snapshot remains stable if now-playing changes
- API parser/request body tests if JSON:API body construction is custom.
- Compile/lint: `lintDebug`, `assembleDebug`, relevant JVM tests.

Device/emulator follow-up:

- Round/square Wear layout check.
- Text input usability.
- Network loss/retry.
- Token expiry/re-auth behavior.
- Real TIDAL create/add/duplicate semantics.

## Phased implementation plan

### Phase 0: API contract spike

Deliverable: short notes or test fixture proving write endpoints.

- Verify owned/editable playlist list endpoint.
- Verify create playlist endpoint/body.
- Verify add track endpoint/body.
- Verify duplicate behavior.
- Verify required scopes and old-token behavior.

Exit criteria:

- Known endpoints and request/response shapes.
- Known error codes for duplicate/permission/not found.
- Decision on whether `favorites()` is sufficient or a new owned-playlists endpoint is required.

### Phase 1: Existing playlist add only

- Wire action sheet to modal.
- Load editable playlists.
- Add current track to selected existing playlist.
- Handle loading, success, failure, retry.
- No create-new yet except disabled/coming-soon row if needed.

Exit criteria:

- User can add current track to an existing editable playlist.
- Duplicate add is safe.
- No playback interruption.
- Unit tests for flow state.

### Phase 2: Create new playlist + add

- Add `+ New playlist` row.
- Add default-name create form.
- Create playlist and add current track.
- Handle partial success where playlist is created but add fails.

Exit criteria:

- User can create a playlist from watch with acceptable text/default-name UX.
- Partial success is clear and recoverable.

### Phase 3: Polish and resilience

- Recently used playlists or last target at top.
- Better sorting/search if playlist count is high.
- Voice/text input improvements.
- Optional local optimistic refresh of library playlist list.
- Better analytics/logging if project has a telemetry pattern.

## Top risks

1. **Unknown TIDAL write API contract.** The app currently has read/list patterns only. Endpoint paths, JSON:API bodies, duplicate behavior, and editable playlist discovery must be validated before UI implementation.
2. **Wear text input friction.** Creating a new playlist can become painful on-watch. The default-name-first design should be used to keep creation viable.
3. **Editable vs favorite playlists.** Existing `favorites()` may return playlists the user saved but cannot edit. The feature needs owned/editable playlist filtering to avoid 403-heavy UX.
4. **Token/scope drift.** Scopes include `playlists.write`, but existing users may need re-auth/re-consent. 403 handling should be explicit.
5. **Duplicate/partial write ambiguity.** Process death, timeout, or duplicate adds may leave server state changed even when the app sees an error. Treat `already present` as success and make retries idempotent from the user’s perspective.

## Recommendation

Start with a narrow API spike before production UI work. Once write endpoints are proven, ship Phase 1 first: add the current track to an existing editable playlist with strong loading/error/retry states. Then add playlist creation in Phase 2 using a default-name-first Wear UX so the watch flow stays fast and low-friction.
