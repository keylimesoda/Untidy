# Evaluation rubric & regression gates

## Scorecard dimensions (vector, never one composite number)
`docs/ux/scorecard.md` tracks per workflow:
- completion success (prerequisite gate)
- taps / swipes / screens visited / backtracks / confirmations / repeated inputs
- time to successful completion; time to first useful information
- error/recovery steps; ambiguous choices; scrolling amount
- startup latency (cold/warm, TotalTime), input-to-feedback latency,
  navigation transition latency, frame jank, allocation during interaction,
  background CPU/wakelock activity
- contrast failures, touch-target violations (Wear: ≥48dp), clipped content,
  missing semantics, inconsistent spacing, missing feedback states,
  focus/rotary ordering, text-scaling breaks

Establish budgets AFTER baseline measurement; never invent budgets first.

## Regression gates (block acceptance)
A change must NOT:
1. break a core workflow or lose user data
2. corrupt persisted settings
3. introduce a crash/ANR
4. make a primary action inaccessible (touch target, semantics, focus)
5. break back/swipe-dismiss behavior
6. cause unintended destructive actions
7. introduce major clipping/unreadability on any supported shape
8. cause a reproducible significant perf regression (median + tail, ≥3 runs)
9. increase background activity without justified user benefit
10. break a supported device configuration

## Evidence ranking
measured defect > observed friction > hypothesis > subjective preference.
Subjective polish may ride along with an objective fix but cannot justify
regression risk alone.

## Severity
- P0: workflow fails / data loss / crash / inaccessible primary action
- P1: major friction or measurable latency on high-frequency path
- P2: friction/polish/accessibility issue on normal paths
- P3: cosmetic preference

Severity ≠ confidence. A P2 hypothesis is not backlog priority 2 by itself.

## Screenshot reviews
Diffs are reviewed, not auto-good/auto-bad. Round-screen edge clipping is a
defect on 454x454 (physical) and 432x432 (wear6 AVD); check both for layout
changes.

## Device matrix status (record actuals in coverage.md; never claim unrun)
| Scenario | Vehicle |
| Typical round display | wear6 AVD 432x432 + physical 454x454 |
| Smaller/larger display | add AVDs later; mark UNTESTED until then |
| Large system font | AVD setting `font_scale` — UNTESTED until run |
| Rotary input | AVD supports rotary (input via `android input rotate` / key code 203/204) |
| Touch | both |
| Ambient mode | AVD aod; verify Doze/ambient behavior |
| Offline/disconnected | `adb shell svc wifi disable` / airplane |
| Interrupted/resumed | background→foreground, process kill (`am kill`) |
| Physical watch | perf validation only, READ-ONLY policy |
