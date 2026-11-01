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
| UX-002 | P2 | high | measured | Settings account chips: clickable dead affordance — **ACCEPTED EXP-003** (device-verified signed-in) | low | — |
| UX-003 | P2 | high | observed+measured | "Erase account" says erase, does sign-out (emul sublabel already "Sign out on this watch") | low | — |
| UX-004 | P2 | high | measured+observed | Track Context has no Like; favs require playback | low-med | — |
| UX-005 | P2 | high | measured+triaged | Recent downloaded track: "Album/Artist unavailable" (see detail) | low-med | device-verify needs auth |
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
| UX-012 | P2 | med | observed | No quick entry to settings/downloads/recents (home scroll only) | med | — |
| UX-013 | — | — | withdrawn (watch) | NOT a product bug: Back trap was an emulator artifact (no IME: remote-input launcher left a phantom focus window). Real watch: honeyboard picker → Back returns to hint ("Back exits search") → Back to Home (verified w/ mCurrentFocus traces) | — | — |
| UX-014 | P3 | med | measured | IME-less emulator has no input fallback — automation-only, not user-facing | — | — |
| UX-015 | P3 | low | measured (emulator) | "Download over LTE" (data-plan-cost setting) toggles with no confirmation/undo | low | — |
| UX-016 | P2 | high | measured (emulator) | Download stalled at "0/20 saving in background" forever under Wi‑Fi-only constraint on metered net; Settings "Manage downloads: No downloads" contradicted playlist chip | med | UX-006 |
| UX-017 | P1 | high | measured+fixed (emulator+curl) | Library "Liked tracks" showed 47 of 281 favorites: legacy v1 listing page-capped (limit=50→47 parsed) and unordered; liked state (v2) fine. Fixed via v2 relationship+batch (264 shown) | done | EXP-006 |
| UX-018 | P3 | med | measured | Liked-tracks list rows lack artwork (v2 batch album resource carries no image URL) | low | EXP-006 |
| UX-019 | P3 | low | measured | Albums/Artists/Playlists library rows also legacy-capped at 50 — same truncation class as UX-017 for other kinds | med | — |
| UX-020 | P1 | high | measured+fixed (emulator) | Server-dead session stayed "signed in": stale "Library unavailable / Tap to retry" forever, no re-auth prompt (prefs-only hasUsableSession + Initializing no-op in nav). Fixed: permanent refresh rejection clears session and nav routes to fresh device code | done | EXP-008 |
| UX-021 | P0 | high | measured+fixed (watch) | Silent-but-progressing playback: HE-AAC compressed offload wedges on SM-R955U ~10-14 s in on BOTH speaker and A2DP routes (flinger: offload thread "0 active" tracks, no HAL writes) while Media3 keeps position/PLAYING advancing. Final fix (supersedes EXP-011's global disable): offload ON by default + per-device stall sentinel -> one auto-rebuild to software decode, suspension persisted; user "Smooth." | done | EXP-011, EXP-012 |
| UX-022 | P1 | high | measured+fixed (watch) | "Play on" picker only listed CONNECTED outputs — paired-but-disconnected headphones unreachable. Fixed: caption + "Connect Bluetooth headphones" row launching the system media-output switcher (Samsung OutputSwitcherJetpackInvocation resolved via queryIntentActivities; BT-settings fallback). Watch-verified: panel listed AirPods Max + Buds4 Pro, tap connected, route flipped to bt_a2dp | done | EXP-010 |
| UX-023 | P2 | med | measured+fixed (watch+emulator) | Media3 reports PLAYING and advances position while the audio renderer is stalled — UI lies about audibility. Fixed by the stall sentinel + one-shot player rebuild (EXP-012); emulator run shows zero false positives on a healthy pipeline | done | EXP-012 |

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

### UX-005 — measured on watch + triaged: Track Context "Album/Artist
unavailable" (evidence 2026-11-01-watch-13-track-context-scrolled.xml).
Root cause chain (Ux005Triage, transcript history://Ux005Triage):
`TrackContextScreen.kt:218-219` keys the chips on blank `track.albumId`/
`artistId`; `RecentRepository.recordTrack:63-76` stores whatever IDs the
played `TidalTrack` carried (offline-shelf `DownloadedTrackSummary.toTrack()`
supplies blank ones — OfflineDownloads.kt:10-18); and
`TidalApiClient.toTrack():1547-1569` takes relationship IDs only from
*included* resources, blank when the response carries relationship refs
without them [INFERENCE on payload shape]. Context lookup
(`TrackContextScreen.kt:76-91`) enriches screen state but never persists
back, so repair doesn't stick. Worse affordance: unavailable chips are
**clickable** and toast (TrackContextScreen.kt:114-120,228-264) — third dead
tap found.
Fix plan (minimal first, EXP-004+): (1) parse relationship IDs from the
reference itself in TidalApiClient; (2) persist context enrichment into the
recent item without re-recording (preserve order/lastPlayedAt); (3)
`enabled=false` for blank-ID chips (keep copy). Lazy self-repair of existing
history via online context opens; no schema migration needed (JSON fields
already exist, blank = absent).
Verification (signed-in emulator): online context open resolves labels;
offline reopen stays resolved (persistence); disabled chips untappable;
Home offline shelf + Search play paths record IDs.

### UX-013 — WITHDRAWN: emulator artifact, real watch behaves
Emulator (no IME): search input chip left the app in a state where Back,
swipe and relaunch all failed to escape. Same journey on the physical
watch: tap Keyboard input → focus moves to `honeyboard/.RemoteInputActivity`
(system picker), Back → returns to app hint screen that literally says
"Back exits search", Back → Home. `onCancel` in MainActivity already pops
correctly. The emulator's missing remote-input service created phantom
focus windows — an automation hazard, not user-facing. Skill lesson added:
check `dumpsys window mCurrentFocus` before classifying "stranded state"
findings on emulators.

### UX-017 — library liked-tracks truncation (NOT a store split)
First hypothesis (v1/v2 store split) was WRONG and is recorded as a
methodology lesson in experiments: my curl probes under-paginated both
stores (v2 links.next uses `page%5Bcursor%5D=` — naive `cursor=` regex
misses it; v1 paging has no stable order, offset-walk duplicates/drops).
Fully paginated: v2 relationships = 281, legacy v1 = 264 distinct entities
— same favorites store. Actual app bug: Library rows are built from the
legacy fallback (`favoriteTracks limit=50` → 47 parsed), so the category
count "Liked tracks · 47" and the list screen are a truncated, unordered
50-item sample of 281. Fix EXP-006: `favoriteTracksV2()` = full v2
relationship id pagination + batched metadata
(`GET /v2/tracks?filter[id]=…&include=artists,albums`; filter accepts max
20 values and SILENTLY drops extras — chunk by 20). Device: row now
"264", list scrolls past 200+. 17 relationship ids have no resolvable
track resource (server-side unavailable favorites) — expected loss.

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
