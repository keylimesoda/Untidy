# User-goal workflows (code-derived counts + 2026-11-01 device exercises)

Tap counts are app actions; scroll distance, load latency, and the external
TIDAL-site authorization are not counted. Frequency is **unknown** — no usage
analytics exist; ordering below is by likely recurrence, flagged as hypothesis.
WF-002/003/004/005/007/008 were exercised live on the physical watch
(432x432) on 2026-11-01 — screenshots in `evidence/2026-11-01-watch-*.png`.
WF-001 was exercised on the `wear6` emulator (454x454).

| ID | User goal | Entry | Path (taps) | Success condition | Exit/recovery | Friction | Coverage |
|---|---|---|---|---|---|---|---|
| WF-001 | Sign in first time | cold start | code shown; authorize off-watch; app auto-navigates on completion | Home reached | Retry chip on error; code expiry "Code expired" | **UX-001**: Done button is a dead tap while pending, redundant on success, 36dp high | emulator, measured |
| WF-002 | Search for music | Home → Search row (1) | system RemoteInput keyboard opens (voice/keyboard/type); query submit; result tap | playback or detail opened | Back key returns Home | keyboard auto-open absorbs edge swipes (automation + swipe-type caveat); watch-home "Plays from TIDAL" footer visible | watch, measured |
| WF-003 | Replay something recent | Home → Recent shelf (1) or See all (2) | track → Track Context → Play (1); playlist/album opens detail then play | playback starts | swipe-dismiss at any step | **UX-004**: no Like in Track Context; **UX-005**: downloaded track shows "Album/Artist unavailable" | watch, measured |
| WF-004 | Browse collection | Home → Library (1, after scroll) | category (On this watch/Playlists 46/Albums 50/Artists) → item → detail → play | playback | swipe-dismiss | categories are in-screen state; Artists row clipped until scroll (UX-011 P3); 3 taps to playback | watch, measured |
| WF-005 | Play a downloaded track | Home → Offline shelf (1) | downloaded track row "Downloaded · tap to play" | local playback | — | shelf rows 390x122px targets OK | watch, mapped (not tapped — read-only policy) |
| WF-006 | Change a setting | Home → Settings (1, after scroll) → control (1) | 2 taps | setting persists | swipe-dismiss | **UX-002**: account status chips are clickable dead affordances | watch, measured |
| WF-007 | Sign out / remove account from watch | Settings → Erase account → confirm | 2 taps, 2-step confirm | local sign-out, downloads kept | cancel row | **UX-003**: "Erase account" label implies deleting the TIDAL account | code-observed (not executed — read-only watch) |
| WF-008 | Change playback while playing | from anywhere via player | player pull-up (gesture) → action | action applies | Back closes sheets | actions reachable only via guarded pull-up; more-actions hint mitigates | player not exercised 2026-11-01 |
| WF-009 | Download for offline | album/playlist detail → download chip (1) | collection enqueues | WorkManager drains | remove download: 2 taps + confirm | static 5% "Downloading" state (UX-006); synchronous enqueue path (UXP-002) | code-observed |
| WF-010 | Return to active playback | watch face / dismissed app | media notification / ongoing activity | player opens | — | **UX-008**: `wear-ongoing` unused; no return affordance verified | not exercised (auth needed) |

## Player-owned surfaces

Now Playing + `ActionsSheet` (pull-up; Download, View album/artist, quality…) +
`QueueSheet` (1 tap from player; pick track +1) + `AddToPlaylistSheet`
(player: pull-up+tap+pick = 3 actions; from Track Context: 2 taps) +
`AudioOutputPicker` ("Play on"; permission gap UXC-003).

## Navigation graph

See `navigation.md` — Home is hub; details reachable from shelves/search/
player (player routes clear the main stack with guarded return).
