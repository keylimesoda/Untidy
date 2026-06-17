# Wear OS UX Walkthrough — UNTIDY-018 / Issue #19

Date: 2026-06-17  
Reviewer stance: senior Wear OS UX review focused on round/small-screen constraints, Wear Compose/Material behavior, media controls, glanceability, rotary/scroll ergonomics, and watch input friction.

## Summary verdict

Untidy is much closer to a real Wear OS music client than a phone UI squeezed onto a watch. The strongest choices are watch-appropriate: device-code auth instead of in-watch credential entry, large chip-style home/library targets, rotary-enabled browse lists, a separate glanceable Now Playing surface, ambient-mode handling, queue-first playback, and honest disabled/offline states.

The biggest UX risk before ship is not the visual direction; it is *watch-context friction at transitions*. Search currently uses an invisible 1 dp `EditText`/IME host, which produced a completely black search-entry screen in emulator evidence. Several important screens also render first/last list rows flush into the circular viewport without enough safe top/bottom padding, so actionable rows are visibly clipped at the round edges. Playback actions are directionally good, but the action sheet uses a vertical pager pattern with minimal affordance, no rotary hook in the sheet itself, and some disabled/write actions that need clearer watch-safe copy before real users touch them.

No P0 production blocker was found from source inspection plus authenticated emulator read-only walkthrough. I would treat the search-entry blank screen and circular safe-area clipping as P1 before public beta because they directly affect first-session task completion on a watch.

## Method and evidence

### Read source/tracker

- Work board: `docs/work-items/BOARD.md` marks UNTIDY-018 as P1/in-progress with report target `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`.
- GitHub issue: `gh issue view 19 --repo keylimesoda/Untidy` confirmed the requested paths and acceptance criteria.
- Product framing: `README.md` describes a standalone Wear OS 4+ app, emulator-tested, with real-watch/Galaxy validation pending.
- Key source inspected:
  - `app/src/main/java/com/tidal/wear/MainActivity.kt`
  - `app/src/main/java/com/tidal/wear/ui/onboarding/OnboardingScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/search/SearchScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/library/LibraryScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/discover/DiscoverScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/album/AlbumScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/artist/ArtistScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/playlist/PlaylistScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/player/ActionsSheet.kt`
  - `app/src/main/java/com/tidal/wear/ui/player/AddToPlaylistSheet.kt`
  - `app/src/main/java/com/tidal/wear/ui/player/QueueSheet.kt`
  - `app/src/main/java/com/tidal/wear/ui/settings/SettingsScreen.kt`
  - `app/src/main/java/com/tidal/wear/ui/components/RotaryScroll.kt`
  - `app/src/main/java/com/tidal/wear/ui/components/TidalResultChip.kt`

### Emulator evidence captured

I used the repo's emulator flow without mutating TIDAL library state:

```bash
source scripts/env-android.sh
UNTIDY_EMULATOR_INSTALL_LAUNCH=1 scripts/ensure-wear-emulator.sh
```

Artifacts are under `reports/ux-wear-os-walkthrough-2026-06-17/`:

- `01-launch.png` / `.xml` — app launch/home authenticated state.
- `02-home.png` / `.xml` — home at top.
- `03-search-entry.png` / `.xml` — search entry screen.
- `04-search-results-moby.png` / `.xml` — read-only search results for `moby`.
- `06-library.png` / `.xml` — library top categories.
- `07-home-scrolled-downloads-settings.png` / `.xml` — home scrolled to disabled downloads/settings.
- `08-settings.xml` was accidentally still home due tap targeting; `08-settings-after-retap.xml` captured system UI after a second tap, so settings runtime evidence is source-only for this pass.
- `emulator-ensure.log`, `runtime-notes.txt` — setup evidence.

Runtime was an emulator (`emulator-5554`) with a 454x454 round-ish viewport. Real-watch caveats are listed at the end.

## Path-by-path notes

### 1. First-run/auth device-code flow

**What works well**

- The app uses device-code auth, not username/password on the watch. This is the right Wear OS pattern because text entry on a watch is high-friction and should be avoided for account setup. Evidence: `OnboardingScreen.kt:68-86` starts device auth and awaits completion; `OnboardingScreen.kt:135-167` shows `Go to link.tidal.com` plus a large code.
- The code display is centered, high contrast, and scales down for longer codes (`OnboardingScreen.kt:158-166`).
- `keepScreenOn` during auth is appropriate for a device-code pairing window (`OnboardingScreen.kt:62-65`).

**Concerns**

- The `Done` button is always enabled but only calls `onDone()` if `isAuthenticated` is true (`OnboardingScreen.kt:170-172`). A user who enters the code on a phone and taps too early gets no visible feedback. On a watch, dead taps are especially costly because users often assume the tiny screen missed input.
- Error strings are short, which is good, but some are ambiguous for recovery: `Trying again...` for `slow_down` (`OnboardingScreen.kt:190-199`) does not tell the user to wait rather than retry.

**Recommendation**

- Keep the device-code model. Change the button state/copy to one of: `Waiting…` disabled until authenticated, or `I entered it` with a visible `Still waiting for TIDAL` response if auth has not completed. This avoids dead-tap ambiguity.

### 2. Home → Search → results → album/artist/track

**What works well**

- Search is the primary home action when signed in and no track is active (`MainActivity.kt:465-470`), which matches the standalone music-watch job: get to music quickly.
- Search results are grouped by Songs/Albums/Artists/Playlists with short caps (`SearchScreen.kt:181-218`), preventing unbounded watch scrolling.
- Results use large Wear chips with artwork thumbnails and one-line labels (`SearchScreen.kt:317-347`, `TidalResultChip.kt:32-69`).
- Runtime search results worked read-only: `04-search-results-moby.png` / `.xml` shows `Search results`, query `moby`, and song rows.

**Concerns**

- Search entry screen is visibly blank in runtime evidence: `03-search-entry.png` is black, because the real input host is a 1 dp, nearly transparent Android `EditText` (`SearchScreen.kt:153-162`, `SearchScreen.kt:390-430`). This may summon IME on emulator/real devices, but visually it gives no prompt, no current query, no cancel affordance, and no reassurance that the app is intentionally waiting for dictation/keyboard.
- The top search result list clips at the circular bottom edge: `04-search-results-moby.png` shows the second visible result partially cut off. The XML confirms visible text is inside bounds as low as `[148,414][372,438]` on a 454 px screen. ScalingLazyColumn edge scaling is expected, but actionable chips should not look accidentally truncated.
- The search input relies on IME behavior. This is inherently device/OEM-sensitive on Wear OS: Gboard, Samsung keyboard, voice input, and emulator keyboard behavior can differ. A black screen during the launch delay makes that fragility more obvious.

**Recommendation**

- Replace the blank input state with a real Wear search prompt surface: title `Search TIDAL`, a large chip/button `Speak or type`, secondary text `Use voice, keyboard, or paste from phone`, and the hidden `EditText` only as an implementation detail. Keep the device keyboard path, but never leave the user staring at black.
- Add enough `contentPadding` to search results so the first/last actionable rows settle inside the circular safe area. Consider centering the selected/first row using ScalingLazyColumn defaults but with top/bottom padding similar to `QueueSheet`.

### 3. Home → Library → playlists/albums/tracks

**What works well**

- The library index uses four categories rather than dumping a large mixed collection (`LibraryScreen.kt:113-147`). That is watch-appropriate.
- Category chips are large and count-bearing (`LibraryScreen.kt:227-248`), giving good glanceability.
- Runtime library data appeared: `06-library.png` / `.xml` shows `Playlists 47`, `Albums 50`, `Tracks` partially visible.

**Concerns**

- The bottom category is visibly clipped at the circular edge in runtime evidence: `06-library.png` shows `Tracks` partly off-screen; XML has `Tracks [150,430][222,454]`, exactly at the bottom edge. On a round watch, this reads as cramped and can reduce confidence that the row is tappable.
- Back behavior inside category drilldown is good (`BackHandler(enabled = selectedCategory != null)` in `LibraryScreen.kt:96`), but there is no visible category breadcrumb beyond title. That is acceptable but worth validating on real hardware with accidental back/swipe gestures.

**Recommendation**

- Add top/bottom content padding to `LibraryScreen`'s `ScalingLazyColumn` and verify first/last category rows are fully readable on 384/454 px round profiles.
- Keep the category-first structure.

### 4. Playback start, Now Playing, action sheet, Queue, View Album, View Artist

**What works well**

- Playback entry points preserve album/playlist queues (`MainActivity.kt:602-612`) and direct track playback carries album/artist ids for later navigation (`MainActivity.kt:580-592`). That supports watch-friendly `play now, inspect later` flows.
- Now Playing has a compact glance layout: album art, title, artist, transport controls, perimeter progress, queue/like actions (`TidalPlayerScreen.kt:249-399`).
- Ambient mode is explicitly handled with reduced UI and burn-in offset (`PlayerActivity.kt:30-46`, `TidalPlayerScreen.kt:557-620`). This is important Wear-specific work and should not be removed.
- Queue sheet is one of the stronger screens: it auto-centers current item, supports rotary scrolling, haptic feedback for jumps, and distinguishes current item (`QueueSheet.kt:55-95`, `QueueSheet.kt:127-190`).
- View Album/View Artist are guarded against missing ids instead of navigating to broken detail screens (`TidalPlayerScreen.kt:432-446`).

**Concerns**

- The action sheet is reached by a vertical pager page (`TidalPlayerScreen.kt:217-224`, `409-448`) with only a small page handle at the bottom of Now Playing (`TidalPlayerScreen.kt:391-398`). Users may not discover it without prior knowledge. Wear OS users expect swipe, bezel, and clearly labeled affordances, but a 24x3 handle alone is subtle.
- Rotary input on Now Playing is reserved for volume changes (`TidalPlayerScreen.kt:231-247`), which is reasonable for a media screen, but it competes conceptually with scroll/pager navigation. On watches with rotating bezel/crown, users may expect crown to scroll the action sheet once on page 1; `ActionsSheet` uses `TransformingLazyColumn` but has no explicit `rotaryScrollableWithFocus` hook (`ActionsSheet.kt:78-132`). It may still behave depending on library defaults/version, but source evidence does not show the same focus/rotary handling used elsewhere.
- Transport visual circles are 28/36 dp but clickable boxes are 48 dp (`TidalPlayerScreen.kt:522-554`). Hit targets are good; visual targets may look small on real watch during motion/workout. This is a real-device validation item, not necessarily a code defect.
- Toasts are used for important missing-id/action failures (`TidalPlayerScreen.kt:425-443`). On Wear OS, toasts are easy to miss if the wrist drops or ambient transition happens. For repeated action failures, inline feedback in the sheet is more reliable.

**Recommendation**

- Add a visible `Actions`/`More` affordance or tiny text hint near the bottom handle until real watch testing proves discoverability.
- Add explicit rotary focus handling to `ActionsSheet` or validate current `TransformingLazyColumn` behavior on Wear Compose version + Samsung bezel.
- Keep the 48 dp touch boxes; consider slightly larger visual transport controls if real-watch testing shows missed taps during movement.

### 5. Add to Playlist, including New test playlist behavior — UX only, no write executed

**What works well**

- The flow snapshots the current track before opening the sheet (`TidalPlayerScreen.kt:422-430`), avoiding state drift if playback changes while the sheet opens.
- It loads editable playlists, handles retry/cancel, gives success feedback, and auto-dismisses after 1.2 s (`AddToPlaylistSheet.kt:134-149`, `AddToPlaylistSheet.kt:180-199`).
- Error strings are short and watch-readable (`AddToPlaylistSheet.kt:384-419`).
- Rows are 48 dp high (`AddToPlaylistSheet.kt:243-271`, `363-379`), appropriate for touch on a small watch.

**Concerns**

- `New test playlist` is the first row and is enabled (`AddToPlaylistSheet.kt:219-230`, `363-379`). It performs a real create+add write (`AddToPlaylistSheet.kt:103-131`). From a UX perspective, this is dangerous because it looks like a normal user feature while the subtitle says `Creates faux Untidy playlist`. Users should not have to understand developer/test semantics on the watch.
- There is no confirmation before creating a new playlist. Watch taps are high-error-rate, especially in scrollable lists.
- `BackHandler` blocks back while adding but not while `creatingPlaylist` (`AddToPlaylistSheet.kt:152-154`). If a create is in progress, back can close the sheet while the write continues, which is confusing even if technically safe.
- The sheet uses `TransformingLazyColumn` without the repo's explicit rotary focus helper. Same caveat as action sheet.

**Recommendation**

- Before public/beta UX, either hide `New test playlist` behind debug/developer mode or make it visibly experimental and require a confirmation row: `Create test playlist?` / `Cancel`.
- Change copy from `Creates faux Untidy playlist` to user-comprehensible language if kept: `Creates “Untidy Test - YYYY-MM-DD” and adds this track`.
- Treat `creatingPlaylist` like `addingTarget` in back handling: block or show `Creating…` until complete.

### 6. Offline/download disabled/proof-in-progress states

**What works well**

- Home's Downloads row is disabled and labeled `Proof in progress` (`MainActivity.kt:396-402`). Runtime `07-home-scrolled-downloads-settings.png` / `.xml` shows this state.
- Settings lists offline as disabled with `Download proof in progress` and related options disabled (`SettingsScreen.kt:162-166`).
- Player action sheet labels disabled downloads as `Offline unavailable` (`ActionsSheet.kt:93-105`, `141-146`), and the player has an explicit toast string for proof status (`TidalPlayerScreen.kt:113-117`).

**Concerns**

- Because the Home downloads chip is disabled, `onOffline` toast will not fire from the disabled chip (`MainActivity.kt:396-402` with `enabled = false`). That is okay if the label is enough, but users cannot tap for an explanation.
- There are three variants of copy: `Proof in progress`, `Download proof in progress`, and `Offline unavailable`. That can make the status feel like three separate concepts.

**Recommendation**

- Use one consistent watch-short phrase: `Offline coming later` or `Offline proof in progress`. If disabled rows are non-clickable, make the secondary line fully explanatory.
- In the action sheet, prefer `Offline proof in progress` over `Offline unavailable` so it matches the roadmap rather than sounding like a permanent account/device failure.

### 7. Settings/account/playback/download sections

**What works well**

- Settings are grouped into Account, Playback, Catalog, Downloads, About, Debug, Legal (`SettingsScreen.kt:62-183`), which is appropriate for a watch settings list.
- Uses radio-style `ToggleChip`s for mutually exclusive quality and explicit/clean preferences (`SettingsScreen.kt:239-289`). That matches Wear expectations better than sliders or nested screens.
- Account erase is a two-step inline confirmation (`SettingsScreen.kt:71-103`), good for destructive account removal.

**Concerns**

- Disabled settings use `Chip(onClick = {}, enabled = false)` (`SettingsScreen.kt:228-236`). This is conventional, but on Wear it can look like broken controls if the secondary text is too terse. The downloads section has several disabled rows in a row, which can feel like dead UI.
- `StatusChip` for account has an empty click handler (`SettingsScreen.kt:209-224`). Semantically it is announced/focusable as a chip but does nothing. Prefer a non-clickable status row or make it open account details.
- Runtime settings capture failed due tap/system UI issue, so this section is source-grounded only.

**Recommendation**

- Convert purely informational rows to non-clickable rows where possible, or give them a clear action. Avoid no-op chips on a watch because focus/tap cost is high.
- Consider collapsing disabled download subsettings behind one disabled summary row until offline proof is implemented.

### 8. Error states: network/manifest failure, missing metadata IDs, empty API states

**What works well**

- Browse screens have short loading/empty/error copy: `Search unavailable`, `No results found`, `Library unavailable`, `Album unavailable`, `Playlist unavailable`, `Artist unavailable` (`SearchScreen.kt:177-180`, `LibraryScreen.kt:306-318`, `AlbumScreen.kt:134-138`, `PlaylistScreen.kt:134-138`, `ArtistScreen.kt:124-127`). This is generally right for watch glanceability.
- Library and Discover retry is tap-to-retry on the error chip (`LibraryScreen.kt:306-318`, `DiscoverScreen.kt:268-281`).
- Now Playing displays playback/like errors inline near the bottom (`TidalPlayerScreen.kt:376-388`), which is better than log-only or toast-only for manifest failures.
- Add-to-playlist maps common HTTP/network states to concise strings (`AddToPlaylistSheet.kt:384-419`).

**Concerns**

- Album/playlist/artist errors are passive status text only; no direct retry action (`AlbumScreen.kt:134-138`, `PlaylistScreen.kt:134-138`, `ArtistScreen.kt:124-127`). On a watch, retry should be one tap if a network hiccup happens on LTE/Wi-Fi transition.
- Missing metadata IDs for View Album/View Artist use toasts (`TidalPlayerScreen.kt:432-446`), which may be missed.
- Some empty states explain next action well (`LibraryScreen.kt:291-302`), while others are terse (`No tracks found`, `No artist content found`).

**Recommendation**

- Add tap-to-retry chips to album/playlist/artist detail errors, matching Library/Discover.
- For missing album/artist IDs, keep the toast as a quick fallback but prefer sheet-local status text when invoked from Actions.

## Severity-ranked findings

| Severity | Path/screen | Evidence | Recommendation |
|---|---|---|---|
| P1 | Search entry | Runtime `reports/ux-wear-os-walkthrough-2026-06-17/03-search-entry.png` is black; source uses a 1 dp alpha `EditText` host at `SearchScreen.kt:153-162` and transparent text at `SearchScreen.kt:390-430`. | Add a visible Wear search prompt/chip and status text while still using IME/voice under the hood. Never present a blank black screen as the input UI. |
| P1 | Round-screen list safe areas | Runtime clipping: home `02-home.png` bottom Library partial; search `04-search-results-moby.png` bottom row partial; library `06-library.png`/XML has `Tracks [150,430][222,454]`; home scrolled `07-home-scrolled-downloads-settings.png` top row clipped. Source lists often omit `contentPadding`, e.g. `LibraryScreen.kt:98-103`, `SearchScreen.kt:165-169`. | Add top/bottom content padding tuned for round 384/454 px profiles; verify first/last actionable rows are readable/tappable after scroll settles. |
| P1 | Add to Playlist new test playlist | `New test playlist` is first enabled row (`AddToPlaylistSheet.kt:219-230`, `363-379`) and performs real create+add (`AddToPlaylistSheet.kt:103-131`). | Hide behind debug/developer mode or add explicit confirmation and clearer copy. Do not present test-write action as ordinary first-row UX. |
| P1 | Action sheet discoverability/rotary | Now Playing only shows a small bottom handle (`TidalPlayerScreen.kt:391-398`) for vertical pager actions; action sheet uses `TransformingLazyColumn` without explicit repo rotary helper (`ActionsSheet.kt:78-132`). | Add a visible `Actions`/`More` affordance or first-run hint; validate vertical pager + crown/bezel behavior on real Wear devices; add explicit rotary focus if needed. |
| P2 | Auth `Done` dead tap | `Done` button is enabled but does nothing unless `isAuthenticated` is true (`OnboardingScreen.kt:170-172`). | Disable as `Waiting…` until auth completes, or show `Still waiting for TIDAL` when tapped early. |
| P2 | Detail-screen retry | Album/playlist/artist detail errors are passive status text (`AlbumScreen.kt:134-138`, `PlaylistScreen.kt:134-138`, `ArtistScreen.kt:124-127`). | Use a retry chip, consistent with Library/Discover tap-to-retry. |
| P2 | Settings no-op/status chips | Account status has `onClick = { }` (`SettingsScreen.kt:209-224`); disabled rows stack in Downloads (`SettingsScreen.kt:162-166`, `228-236`). | Make informational rows non-clickable or actionable; collapse disabled download subsettings until supported. |
| P2 | Offline copy consistency | Home says `Proof in progress` (`MainActivity.kt:396-402`), Settings says `Download proof in progress` (`SettingsScreen.kt:162-166`), action sheet says `Offline unavailable` (`ActionsSheet.kt:141-146`). | Use one phrase across surfaces, preferably `Offline proof in progress` or `Offline coming later`. |
| P2 | Missing metadata feedback | View Album/View Artist missing IDs use toast only (`TidalPlayerScreen.kt:432-446`). | Show inline action-sheet feedback when invoked from the sheet; keep toast as fallback. |
| P3 | Transport visual size | Click boxes are 48 dp but visual circles are 28/36 dp (`TidalPlayerScreen.kt:522-554`). | Real-watch test during movement/workout; enlarge visual controls only if missed taps occur. |
| P3 | Search result grouping caps | Search caps songs to 6, albums/artists/playlists to 4 (`SearchScreen.kt:181-218`). | Good for glanceability; later consider a `More` row only if users need deep search browsing. |

## Top follow-up issues recommended

1. **Fix Wear search entry UX and IME robustness** — visible prompt, voice/type affordance, non-blank state, OEM keyboard validation. Severity P1.
2. **Normalize round-screen content padding across browse/home lists** — search, library, home, discover, album/playlist/artist details. Severity P1.
3. **Gate or confirm New test playlist creation in Add to Playlist** — prevent accidental live write from first row; improve copy. Severity P1.
4. **Validate Now Playing action-sheet discoverability and rotary behavior on real watch** — especially Samsung rotating bezel/crown and vertical pager expectations. Severity P1/P2 depending on real-device result.
5. **Make detail error states retryable and sheet errors inline** — album/playlist/artist retry chips; missing metadata feedback inside action sheet. Severity P2.

## Real-watch validation caveats

- Emulator evidence is useful for layout, obvious clipping, and flow reachability, but it cannot prove Samsung One UI Watch bezel behavior, crown acceleration, haptics, palm/ambient transitions, or Bluetooth output routing.
- Search input is the highest real-watch caveat: keyboard/voice UX varies by OEM and installed IME. The current hidden `EditText` pattern may behave differently on Galaxy Watch vs emulator.
- Ambient mode and burn-in offset are source-implemented, but real OLED/AOD readability and pixel-shift behavior need hardware validation.
- Transport controls should be tested while walking/exercising with sweaty/partial touches; emulator mouse clicks overestimate precision.
- Audio output settings should be validated with real paired Bluetooth earbuds because emulator audio devices are not representative.

## Deliverable status

- Report created at `docs/ux/wear-os-ux-walkthrough-2026-06-17.md`.
- Runtime artifacts captured under `reports/ux-wear-os-walkthrough-2026-06-17/`.
- No production code was modified.
