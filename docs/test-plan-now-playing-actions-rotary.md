# Now Playing Actions Discoverability + Rotary Test Plan

Date: 2026-06-17
Issue: [#23 / UNTIDY-022](https://github.com/keylimesoda/Untidy/issues/23)
Scope: validate the Now Playing vertical action page, action-sheet discoverability, and rotary/bezel behavior without touching live TIDAL library state.

## Source observations

- `TidalPlayerScreen` uses a two-page `VerticalPager`; page 0 is Now Playing and page 1 is `ActionsSheet` (`app/src/main/java/com/tidal/wear/ui/player/TidalPlayerScreen.kt:217-223`, `:409-410`).
- Page 0 maps rotary input to volume through `rotaryScrollable(...)`, so crown/bezel input on the primary Now Playing page is intentionally volume-oriented rather than action-sheet scrolling (`TidalPlayerScreen.kt:225-245`).
- The only visible action-page affordance on Now Playing is a small bottom pill/handle (`TidalPlayerScreen.kt:391-398`). This is minimal and needs runtime validation for discoverability.
- `ActionsSheet` uses `TransformingLazyColumn` (`ActionsSheet.kt:82-86`) but does not currently apply the repo's explicit `rotaryScrollableWithFocus` helper (`ui/components/RotaryScroll.kt:14-26`). Runtime validation should check whether Wear Compose focus/rotary behavior is sufficient or whether the helper should be added.

## Validation matrix

| Area | Device | Steps | Pass condition | Evidence to capture |
|---|---|---|---|---|
| Discover action page | Wear emulator | Launch authenticated Now Playing with a current track; observe page 0 without prior instructions. | There is a visible, understandable cue that more actions are below/swipeable. | Screenshot/XML of page 0; note whether the bottom handle is visible against album art/accent. |
| Swipe to actions | Wear emulator | Swipe vertically from Now Playing to page 1 and back. | Page transition is smooth; user can return to controls; no accidental volume-only trap. | Screenshot/XML of page 1 and logcat filtered for crashes. |
| Rotary on Now Playing | Wear emulator or real watch | Rotate crown/bezel on page 0. | Volume changes with haptic feedback; action page does not unexpectedly scroll into view. | Short note with observed volume overlay behavior. |
| Rotary in action sheet | Wear emulator | Navigate to page 1; rotate crown/bezel through action rows. | Rows scroll predictably, first and last actionable rows are reachable, no focus dead zone. | Screenshot/XML after scrolling to lower rows; logcat if input fails. |
| Rotary in action sheet | Real Wear device preferred | Repeat page 1 rotary/bezel test on physical hardware, especially Galaxy Watch bezel/crown behavior if available. | Same as emulator, with no OEM-specific focus failure. | Device model + short observed-result note. |
| Action-sheet safety | Emulator/real watch | Use non-mutating rows only: Queue, Output, View album/artist where metadata exists. Do not create playlists. | No live library mutation occurs; missing metadata errors stay inline/watch-readable. | Screenshot/XML of any inline status message. |

## Decision rules

- If users miss the action page or the bottom handle is too subtle, add a tiny watch-native affordance such as a concise `Actions` / `Swipe for actions` hint that fades or stays below controls without cluttering the face.
- If action-sheet rotary is inconsistent, wire the `TransformingLazyColumn` to the explicit focus/rotary helper pattern used elsewhere in the app, or add an equivalent focused rotary modifier that works with Wear Compose lazy state.
- If emulator passes but real watch fails, keep #23 open and block final release readiness on physical-device validation rather than treating emulator behavior as sufficient.

## Current next step

Run the emulator validation matrix first, then repeat the rotary/bezel rows on a real Wear device if available before moving #23 to review.
