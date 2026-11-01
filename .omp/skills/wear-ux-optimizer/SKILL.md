---
name: wear-ux-optimizer
description: >
  Iteratively improve Untidy (Wear OS TIDAL client, com.tidal.wear) end-to-end
  user workflows, usability, visual quality, accessibility and device
  performance using evidence, on-device journeys and regression tests.
  Use for any UX audit, workflow improvement, polish, performance, or
  "improve the app" request on this repo.
---

# Untidy Wear OS UX Optimizer

Closed-loop optimization program. The deliverable is an **improved app backed
by evidence**, never an audit report.

**Loop:** Discover → Observe → Measure → Critique → Hypothesize → Implement →
Exercise → Compare → Accept/Revert → Learn → Repeat.

A UX improvement is only "verified" if it was **exercised on a device**
(emulator OK; physical watch required for performance conclusions).
A passing build is not evidence. Screenshots alone are not evidence.

## Resume protocol (every session — do not rediscover the app)

1. `read docs/ux/findings.md` — take the highest-priority `open` finding.
2. `read docs/ux/scorecard.md` + `docs/ux/coverage.md` — baselines and what is
   actually covered; `docs/ux/experiments.md` for accepted/reverted precedent.
3. Bring up the environment (below). Never edit app behavior before a baseline
   exists for the affected workflow.
4. Run one bounded experiment cycle (see `references/optimization-loop.md`).
   Three well-scoped experiments per session, then reassess. No uncontrolled
   loops.
5. End of session: update findings/scorecard/experiments/coverage + write a
   cycle report to `docs/ux/reports/YYYY-MM-DD-<topic>.md`. Commit.

## Environment (validated 2026-11-01)

```bash
source scripts/env-android.sh          # sets JAVA_HOME, ANDROID_HOME, PATH
bash ./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
```

- SDK: `~/Work/toolchains/android-sdk` (has `cmdline-tools`, `emulator`, AVD
  `wear6` = Wear OS 6, 432x432, x86_64). JDK 21: `~/Work/toolchains/jdk21`.
- Android CLI (`android`, in `~/.local/bin`): use `--sdk ~/Work/toolchains/android-sdk`
  or set `--sdk=...` in `~/.androidrc`. Commands used by this program:
  `android screen capture|describe|layout`, `android input tap|swipe|rotate`,
  `android emulator ...`, `android profiler`, `android journeys run`.
- Official skills live in `.agents/skills/` (wear-compose-m3, android-profiler,
  testing-setup, android-cli) — consult them before implementing Wear UI or
  profiling work.
- Physical watch (Galaxy Watch, SM_R955U, 454x454, Android 16/Wear OS 6):
  find it with `adb devices -l | grep R955` — serial is an mDNS address and
  changes across networks. The released app is installed and signed-in;
  treat it as READ-ONLY: screenshots, `uiautomator dump`, launch, perf probes.
  NEVER install debug builds, clear data, or perform destructive flows on it.
- Emulator is the automation target: install debug builds there freely.

```bash
UNTIDY_AVD_NAME=wear6 bash scripts/ensure-wear-emulator.sh   # boots + optionally installs
adb -s <serial> shell am start -W -n com.tidal.wear/.MainActivity
```

Key identifiers: package `com.tidal.wear`; activities `.MainActivity`,
`.PlayerActivity`; media service `com.tidal.wear.core.playback.TidalMediaService`;
log tags `Untidy/Player`, `Untidy/DirectPlayback`, `Untidy/API`.

Auth on emulator = TIDAL device-code flow (user enters code at tidal.com on
another screen). Until a seeded-auth fixture exists (finding UX-AUTH-001),
post-auth journeys must run on the physical watch read-only or use fixture
screenshots; do not fake auth state in production code.

## Operating principles

1. Optimize **user goals**, not screens. Before touching a component: why is
   the user here, what do they need, what action, what next, what if
   interrupted/mistaken, how fast. Prefer eliminating an interaction over
   prettifying it.
2. The watch is a distinct medium: glanceable, round, fat targets, rotary,
   shallow navigation, interruption-tolerant, power-sensitive. Apply
   https://developer.android.com/docs/quality-guidelines/wear-app-quality.
3. Evidence classes are ranked — never conflate them:
   **measured defect** > **observed friction** > **hypothesis** > **subjective
   preference**. Unverified claims stay labeled hypotheses.
4. Prefer simplicity, in order: remove work → remove decisions → make
   obvious → improve feedback/recovery → responsiveness → visual delight.
5. Preserve established behavior, correctness, accessibility, user data, and
   repo architecture conventions unless there is demonstrated reason to change.

## Finding record (required format)

Every finding in `docs/ux/findings.md`:

```
id, workflow, screen?, category(friction|wear-design|perf|correctness|accessibility),
severity P0-P3, confidence low|med|high, evidence_type measured|observed|hypothesis|preference,
evidence[refs], observation, user_consequence, hypothesis, proposed_change,
expected_benefit, verification_method, measured_result, status open|accepted|reverted|inconclusive
```

## Experiment cycle

See `references/optimization-loop.md`. Hard rules:

- Write the hypothesis (problem, change, expected metric deltas, invariants,
  falsifier) **before** editing.
- Smallest coherent change; standard Wear components; no drive-by rewrites,
  dependency upgrades, or multi-workflow experiments.
- After editing: build → install → **replay the journey on the device** →
  before/after screenshots + uiautomator XML → run tests → measure.
- Verdict ∈ accept | accept-provisionally | revise | revert | inconclusive.
  Never force a positive verdict; noisy/small deltas are inconclusive.
- Regression gates (see rubric) block acceptance. Known pre-existing failures
  stay recorded separately from new regressions.

## Independent review agents

For audits, fan out read-only scouts with the four lenses (workflow friction /
Wear-native design / responsiveness+efficiency / correctness+regression).
One coordinator owns device access; reviewers never fight over the emulator.
Final validation reviewer independent from the implementer where feasible.

## Wear-specific checklist

`references/wear-guidelines.md`. Every applicable item gets explicit
evaluation per audit: navigation, information presentation, interaction,
animation, state & recovery, efficiency.

## Device automation traps (read before touching a device)

`references/automation-notes.md` — watch auto-sleep, swipe-type capture on
input surfaces, emulator network-failure trick, density conversions,
uiautomator clickable-vs-text nodes, pm clear side effects. Keep appending.

## Self-improvement of the system

After each group of iterations: which reviewers found real problems, false
positives, unpredicted regressions, redundant tests, misleading metrics; then
update this skill's heuristics, fixtures, and checklists. The evaluation
system must NOT rewrite success criteria to make recent changes pass. Stable
regression tests are not weakened or deleted by the optimizing agent.

## Safety boundaries

Authorized: inspect + improve code, install tooling/skills, install debug
builds on emulator, emulator data wipes. Never: publish releases, deploy,
clear real watch user data, modify signing/secrets, add telemetry/analytics,
broad production dependency changes without evidence, destructive operations
on the physical watch. Keep changes reversible via Git; commit per experiment.

If a device/SDK/permission is unavailable: do everything possible without it,
record the limitation, never claim an unrun test passed.

## Repository artifacts

- `docs/ux/` — README, architecture, workflows, navigation, findings,
  scorecard, experiments, decisions, coverage, `reports/`, `evidence/`
- `.omp/skills/wear-ux-optimizer/` — this skill
- Journey/fixture code goes in the existing test structure; work-item tracking
  stays in `docs/work-items/` (mirror accepted findings there).
