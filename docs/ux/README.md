# Untidy UX Optimization Program

Continuous, evidence-driven improvement of Untidy's Wear OS experience.
The operating manual is the project skill: `.omp/skills/wear-ux-optimizer/SKILL.md`
(read it via `skill://wear-ux-optimizer`). This directory is the program's
memory: findings, baselines, experiments, and evidence persist across sessions.

## How to run a session

1. Read `findings.md` — pick the top open item.
2. Read `scorecard.md` + `coverage.md` + `experiments.md` — baselines, coverage, precedent.
3. Bring up the environment (skill's Environment section; `scripts/env-android.sh`,
   `UNTIDY_AVD_NAME=wear6 bash scripts/ensure-wear-emulator.sh`).
4. Run bounded experiment cycles per `references/optimization-loop.md` in the skill.
5. Update every artifact touched; write `reports/YYYY-MM-DD-<topic>.md`; commit.

## Files

- `architecture.md` — what Untidy is, module by module (2026-11-01)
- `workflows.md` — user-goal inventory with interaction counts
- `navigation.md` — navigation graph + known routing friction
- `findings.md` — the prioritized backlog (canonical IDs UX-NNN; lens IDs preserved)
- `scorecard.md` — measured baselines and metric definitions
- `coverage.md` — what has actually been exercised on a device (never claim more)
- `experiments.md` — EXP-NNN records: hypothesis, change, before/after, verdict
- `decisions.md` — program + product decisions with rationale
- `evidence/` — screenshots, UI dumps, traces (referenced by ID in findings/experiments)
- `reports/` — human-readable session summaries

## Ground rules (non-negotiable)

- A UX claim of "improved" requires on-device exercise; measurements rank above
  observations, which rank above hypotheses, which rank above preference.
- The physical watch is read-only (no installs, no Play/Favorite/Download/erase
  actions, no data wipes). It is the only signed-in device until auth fixtures exist.
- The program does not weaken regression tests or success criteria to make
  changes pass.
