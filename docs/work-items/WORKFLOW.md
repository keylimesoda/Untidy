# Untidy Issue Workflow

This repo uses GitHub Issues as the canonical work tracker. Local files under `docs/work-items/` are a mirror/staging aid for agents and should be kept in sync with GitHub.

Canonical board:

- Issues: https://github.com/keylimesoda/Untidy/issues
- PRs: https://github.com/keylimesoda/Untidy/pulls
- Local mirror: `docs/work-items/work-items.json`
- Generated local board: `docs/work-items/BOARD.md`

## Status labels

Every open issue should have exactly one `status:*` label.

### `status:todo`

Use when:

- Work is defined but not actively being worked.
- Spec may exist, but implementation/test work has not started.

Required before leaving in `todo`:

- Clear next action in issue body/comment or local `next` field.
- Priority label (`P0`–`P3`).
- Type label (`type:*`).
- Area label (`area:*`).

### `status:in-progress`

Use when:

- A human or agent is actively working the issue.
- A subagent has been spawned for it.
- Parent session is currently validating/fixing it.

Required updates:

- Comment when substantial work starts, especially if multiple agents are active.
- Keep the local mirror in sync:

```bash
python3 scripts/work-items.py set UNTIDY-010 status in-progress
python3 scripts/work-items.py board --quiet
```

Then sync GitHub label:

```bash
gh issue edit 10 --remove-label status:todo --remove-label status:review --remove-label status:blocked --add-label status:in-progress
```

### `status:review`

Use when:

- Implementation/test/spec work is complete enough for review.
- Acceptance criteria are believed satisfied, or remaining gaps are explicitly documented.
- Code is in a PR or a committed branch.

Required before moving to review:

- Comment with evidence:
  - code paths changed
  - verification command/result
  - emulator/runtime artifacts if applicable
  - remaining known warnings/gaps
- If code changed, run the relevant gate. Preferred full gate:

```bash
source scripts/env-android.sh
bash ./gradlew lintDebug assembleDebug testDebugUnitTest --no-daemon
git diff --check
```

- Link PR/commit if available.

### `status:blocked`

Use when:

- Work cannot proceed without external information, credentials, product decision, upstream API/SDK proof, or user action.

Required before blocking:

- Explain the blocker in the issue.
- Record the exact unblock question/action.
- Keep blocked work narrow. If part of the issue is complete, comment with what is done and what remains blocked.

Example:

- Downloads/offline is blocked until a sanctioned TIDAL offline/download path is proven. Fake UI has been neutralized, but product download implementation should not proceed from streaming manifests.

### `status:done`

Use when:

- Acceptance criteria are satisfied.
- Code/docs are merged or intentionally closed without code.
- Verification evidence is linked.

Required before closing/marking done:

- PR merged or explicit decision to close without merge.
- Final comment summarizes evidence.
- Local mirror updated.

If closing from CLI:

```bash
gh issue close 5 --comment "Done in PR #14. Verification: ..."
python3 scripts/work-items.py set UNTIDY-005 status done
python3 scripts/work-items.py board --quiet
```

## Priority labels

- `P0` — blocks core validation, stability, or ability to test.
- `P1` — high-value feature/test/decision after P0 is under control.
- `P2` — important but after playback baseline or API assumptions are proven.
- `P3` — cleanup, polish, warning reduction, refactor backlog.

## Type labels

- `type:bug` — broken behavior or crash.
- `type:feature` — user-facing capability.
- `type:test` — validation task / QA evidence.
- `type:spike` — research/proof-of-concept.
- `type:cleanup` — warnings/refactors/polish.
- `type:decision` — documented recommendation needed.
- `type:task` — general work item.

## Area labels

Use at least one `area:*` label:

- `area:tooling`
- `area:qa`
- `area:playback`
- `area:platform`
- `area:ui`
- `area:api`
- `area:stability`
- `area:offline`
- `area:cleanup`

## Agent operating rules

When starting work:

1. Read the issue and linked spec/docs.
2. Move issue to `status:in-progress`.
3. Comment if the work is non-trivial or parallelized.
4. Keep scope tight; do not mix unrelated issues unless explicitly batching.

When implementing:

1. Prefer small, testable patches.
2. Update/add pure tests where possible.
3. Do not perform external writes unless explicitly approved or clearly reversible and authorized.
4. For live TIDAL mutations, record whether the write was reversible and whether it was restored.

When validating:

1. Use the smallest meaningful gate for the change.
2. Use full gate before PR/checkpoint updates.
3. Capture emulator artifacts under `reports/` only; reports are local and gitignored.
4. Summarize artifact names in issue comments rather than committing raw screenshots/logs.

When finishing:

1. Comment with evidence.
2. Move issue to `status:review`, `status:blocked`, or `status:done`.
3. Update local mirror:

```bash
python3 scripts/work-items.py set UNTIDY-XXX status review
python3 scripts/work-items.py board --quiet
```

4. Sync GitHub labels.
5. If code changed, make sure the PR references the issue.

## Current project convention

PR #14 is the current active integration PR for the first large emulator/playback/feature batch:

- https://github.com/keylimesoda/Untidy/pull/14

Most issues in `status:review` should remain open until PR #14 lands or Ric explicitly chooses to close them before merge.

## SELinux/emulator note

The local Wear emulator currently requires the temporary `selinuxuser_execheap` workaround on the ThinkPad. Do not leave it on as a standing workstation setting.

Preferred wrapper:

```bash
scripts/run-wear-emulator.sh
```

When manual:

```bash
sudo setsebool selinuxuser_execheap on
# run emulator tests
sudo setsebool selinuxuser_execheap off
```

Never use persistent `setsebool -P selinuxuser_execheap on` without an explicit security decision.
