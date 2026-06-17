# UNTIDY-020 Round-screen safe-area padding plan

Date: 2026-06-17
Issue: #21 / UNTIDY-020
Source: senior Wear OS UX walkthrough (`docs/ux/wear-os-ux-walkthrough-2026-06-17.md`)

## Goal

Make first and last actionable rows look intentionally readable/tappable on round Wear OS screens without turning Wear lists into phone-style padded lists.

## Evidence to preserve

The UX walkthrough captured three concrete clipping cases on a 454x454 Wear emulator:

- Home: bottom Library row is partially clipped in `reports/ux-wear-os-walkthrough-2026-06-17/02-home.png`; top row clipping appears after scroll in `07-home-scrolled-downloads-settings.png`.
- Search results: lower visible result is partly cut off in `04-search-results-moby.png`.
- Library: `Tracks` text lands at `[150,430][222,454]` in `06-library.xml`, exactly at the bottom edge.

## Scope for the code pass

Primary P1 surfaces:

1. Home signed-in menu (`MainActivity.kt`) — main action list and scrolled downloads/settings state.
2. Search results (`SearchScreen.kt`) — grouped results list after query submit.
3. Library index and category lists (`LibraryScreen.kt`) — especially the top-level Playlists/Albums/Tracks rows.

Secondary audit surfaces if the first pass is clean:

- Discover list (`DiscoverScreen.kt`).
- Album / Artist / Playlist detail track lists.
- Settings list.

## Implementation guidance

Prefer small, shared padding constants over ad hoc per-screen magic numbers:

- Start with top/bottom content padding around `28.dp` to `36.dp` for 454px round profile.
- Keep horizontal padding unchanged unless rows are also clipped laterally.
- Preserve `ScalingLazyColumn` / Wear edge scaling behavior; do not replace with phone Compose lists.
- If a screen already uses `contentPadding`, tune only the vertical edge padding.
- Avoid touching the active Search entry prompt work from #20 except where Search results list padding is explicitly needed.

## Validation checklist

Minimum static gate after code changes:

```bash
source scripts/env-android.sh >/dev/null
bash ./gradlew :app:compileDebugKotlin lintDebug --no-daemon
git diff --check
```

Runtime evidence to capture before closing #21:

1. Home first view: primary rows fully readable.
2. Home scrolled near Downloads/Settings: first and last visible actionable rows are not accidentally clipped.
3. Search results for `moby`: first and last visible result rows settle inside the round safe area.
4. Library index: `Tracks` / final visible category row is not cut at y=454.

Suggested artifact directory:

`reports/round-safe-area-2026-06-17/`

## Completion criteria

#21 should stay open until screenshots/XML prove the padding change on the emulator. A docs-only plan is not enough to close it; this file is just the handoff for a non-conflicting implementation pass.
