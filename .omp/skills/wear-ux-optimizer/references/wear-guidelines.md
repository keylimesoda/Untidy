# Wear OS platform guidance (checklist per audit)

Authoritative: https://developer.android.com/docs/quality-guidelines/wear-app-quality
Consult `.agents/skills/wear-compose-m3/SKILL.md` before UI implementation work;
follow the repo's actual library versions — no gratuitous migration.

## Navigation
- common actions reachable quickly; context always obvious
- swipe-to-dismiss consistent with screen role (not on confirm states)
- back behavior consistent; interrupted workflows resume meaningfully
- no unnecessary layers/dialogs; shallowest comprehensible path
  (two obvious actions > one ambiguous gesture)

## Information presentation
- most useful info first, glanceable; secondary deprioritized
- controls understandable without instructions
- text readable at supported sizes; check round-edge clipping (crown-safe area)
- no phone-sized density crammed onto the watch

## Interaction
- touch targets adequate (Wear M3: 48dp min; prefer 52dp+ primary)
- no accidental activation of destructive/common actions
- rotary input where appropriate (scroll, focus, value adjust)
- lists behave predictably (TransformingLazyColumn semantics)
- prompt feedback to every input; gestures match platform conventions

## Animation
- motion communicates state/structure only; smooth; short; non-blocking
- never obscures content; remove animation/work that serves nothing

## State & recovery
- backgrounding, wrist-off/tilt-to-wake, connectivity loss, permission denial,
  process recreation, background-op failure: all handled; progress preserved
- offline downloads: state truthfully shown, recovery offered

## Efficiency
- no expensive work on UI thread; no unnecessary recompositions
- efficient list rendering; no large allocations during interactions
- idle/polling behavior justified; background scheduling appropriate
- reference: https://developer.android.com/training/wearables/compose/performance
  Gmail Wear startup case study (methodology, not a forecast):
  https://developer.android.com/topic/performance/appstartup/case-study-gmail-wear

## Accessibility
- contentDescription/semantics on all icon-only controls
- screen-reader traversal order; focus/rotary ordering; contrast on
  ambient-adjacent palettes; tap target size

## Untidy-specific attention areas
- Now Playing actions + rotary (docs/test-plan-now-playing-actions-rotary.md)
- downloads/offline lifecycle truthfulness (docs/ux/offline-download-lifecycle-ux)
- round safe area (docs/ux/round-screen-safe-area-plan)
- recent shelf entry point (docs/reviews/recent-shelf-review, docs/specs/recent-shelf.md)
- playback notification / ongoing activity on the phone side
