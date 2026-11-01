# Findings backlog

Canonical IDs `UX-NNN` (provenance: lens IDs UXF/UXW/UXP/UXC from the 2026-11-01
four-lens audit + coordinator device exercises). Ranked by
`impact × frequency × confidence / (effort × risk)` with frequency **unknown**
throughout — ranking leans on confidence, severity, and low-effort leverage.
Status ∈ open / accepted / reverted / inconclusive / blocked(<reason>).

## Priority queue

| UX | Sev | Conf | Evidence | Summary | Effort | Blocked by |
|---|---|---|---|---|---|---|
| UX-001 | P2 | high | measured+observed | Onboarding: dead-tap Done + 36dp + redundant — **ACCEPTED EXP-001** | low | — |
| UX-002 | P2 | high | measured+observed | Settings account chips: clickable dead affordance — **ACCEPT-PROVISIONALLY EXP-003** | low | device-verify needs auth |
| UX-003 | P2 | high | observed | "Erase account" says erase, does sign-out | low | device-verify needs auth |
| UX-004 | P2 | high | measured+observed | Track Context has no Like; favs require playback | low-med | device-verify needs auth |
| UX-005 | P2 | medium | measured | Recent downloaded track: "Album/Artist unavailable" | ? | needs triage |
| UX-006 | P2 | high | observed | Download shows fake static 5% progress | med | needs auth+download |
| UX-007 | P2 | high | observed | OAuth slow_down polling not progressive — **ACCEPTED EXP-002** | low | — |
| UX-008 | P2 | med | observed | wear-ongoing unused; notification denial ignored; no return affordance | med-high | needs auth |
| UXC-001 | P1 | high | observed | Playback+queue lost on process death | med | needs auth to verify |
| UXC-002 | P1 | high | observed | Failed refresh strands "signed-in" unusable session | med | — (unit-testable) |
| UXC-003 | P1 | med | hypothesis | Un-guarded Bluetooth enumeration w/o BLUETOOTH_CONNECT | low-med | — |
| UXP-001 | P2 | med | hypothesis | Startup/recomposition does sync pref reads + JSON decode | med | measure first |
| UXP-002 | P2 | med | hypothesis | Download tap blocks UI (runBlocking DataStore) | low | needs auth |
| UX-009 | P2 | high | observed | No auth fixture for emulator journeys | med | user TIDAL account |
| UXC-005 | P2 | high | observed | Queue silently truncated at 100 tracks | med | needs auth |
| UXC-008 | P2 | high | observed | playbackErrorUiMessage dead code; generic error text | low | needs auth to verify |
| UXW-003 | P2 | high | observed | M2/M3 + SLC/TLC component mix is inconsistent | high | policy first |
| UXW-004 | P2 | med | observed | Secondary text below reviewed size range | low-med | — |
| UXP-003 | P2 | low-med | hypothesis | Unkeyed list rows in long lists | low | measure first |
| UXP-004 | P3 | high | observed | No startup trace markers | low | — |
| UXC-004 | — | — | — | (merged into UX-007) | | |
| UXC-006 | P2 | med | hypothesis | BT route loss: picker vs service preference diverge | med | device-heavy |
| UXC-007 | P2 | med | hypothesis | Player-return swipe-dismiss vs Back may diverge | low | needs auth |
| UXC-010 | — | — | — | (merged into UX-008) | | |
| UXC-011 | P2 | med | hypothesis | Auth deadlines use wall clock | med | — |
| UXC-012 | P2 | low | hypothesis | Multi-device refresh-token rotation unverified | — | needs 2 installs+test acct |
| UX-010 | P3 | med | measured | Watch cold start 2716ms (1 sample) vs emu 688ms | — | baseline only |
| UX-011 | P3 | med | measured | Library Artists row clipped until scroll; home bottom rows mid-clip | low | — |
| UX-012 | P2 | med | observed | No quick entry to settings/downloads/recents (home scroll only) | med | needs auth |

## Detail (non-obvious only; lens transcripts hold full text — history://)

### UX-001 — Onboarding Done: dead tap, 36dp, redundant
Measured on `wear6` 2026-11-01: tapping Done while `authorization_pending`
(logcat shows polling) produced byte-identical before/after screenshots and
no log reaction (`evidence/2026-11-01-emul-onboard-{before,after}-done.png`;
logcat artifact recorded in `experiments.md` EXP-001). Code
`OnboardingScreen.kt:170-182` enables the button always but the handler is a
no-op until `isAuthenticated`; success auto-navigates anyway
(`OnboardingScreen.kt:85-110`), so the button is never needed. Button is
`height(36.dp)` (below 48dp Wear minimum; uiautomator shows 358x72px surface
+96px incl. padding on 454px device — density 2.0).
Fix direction: remove Done from the pending state; state the waiting
semantics in copy ("Enter the code… this screen updates automatically"); keep
Retry as the only button (error branch), sized ≥48dp.

### UX-002 — Settings account chips clickable but inert
Measured on watch 2026-11-01: "TIDAL account / User #38971465" chip
clickable=true `evidence/2026-11-01-watch-10-settings.xml`;
`SettingsScreen.kt:76-82,119-124,279-290` gives `StatusChip` `onClick={ }`.

### UX-005 — NEW, measured on watch: Track Context "Album unavailable" /
"Artist unavailable" for downloaded recent track Interlude I
(`evidence/2026-11-01-watch-13-track-context-scrolled.xml`). Links exist but
route metadata missing for downloaded recent tracks. Triage against
`RecentRepository` metadata and `spec-view-album-artist-from-now-playing`.

### UX-006 — download progress honesty
`TidalPlayerScreen.kt:150-175` flips to `Downloading(0.05f)` and only updates
on completion/failure; `OfflineTrackDownloader` ignores `DashDownloader`
progress callback. Users see a lie for minutes. Fix: real progress from
`DashDownloader.ProgressListener` or honest indeterminate state.

### UX-007 — slow_down backoff not progressive
`TidalAuthRepository.kt:177-178,392-402`: each slow_down maps to
`session.intervalSeconds + 5` (base-relative, not accumulating). Fix:
accumulate; unit-test sequences (no device needed).

### UX-009 — auth fixture gap (program-critical)
No deterministic signed-in emulator state. Options evaluated: (a) one-time
manual device-code sign-in on emulator, keep app data — simplest, needs user;
(b) debug-build-only token import via `run-as` — needs a legitimately obtained
token, no production hook, never commit tokens; (c) fakes in DI for
UI-component tests only. Decision pending in `decisions.md`.

### UXP-001 — composition-path I/O (highest-leverage perf item)
`MainActivity.kt:199-222` builds offline/recent shelves **in composition**:
`readOfflineDownloadedTracks()` scans `offline-downloads` keys and JSON-decodes
each track; not `remember`ed, reruns on recomposition; auth repo initializes
with sync SharedPreferences read. 27 downloaded tracks measured on the user's
watch today — decode count is real, cost is not yet measured. Verify with
Macrobenchmark StartupTimingMetric + Perfetto before/after (3+ launches).

## Merges and dedup provenance

- UXF-001 + UXW-001 + UXF-001 measured no-op → UX-001 (note: the 64x30px
  figure initially circulated was the non-clickable text node; the clickable
  surface is 358x72px/36dp. Keep the corrected number.)
- UXF-003 → UX-002; UXC-009 → UX-003 (different defects on same screen; kept
  separate: affordance vs wording).
- UXF-002 → UX-004; UXC-004 → UX-007; UXC-010 + notification gap → UX-008.
- Prior-audit items already resolved (search prompt, safe-area, detail Retry,
  player queue/view actions, beta-6 recovery) are NOT re-listed.
