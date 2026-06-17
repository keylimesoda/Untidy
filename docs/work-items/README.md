# Untidy Work Items

This is the repo-local tracker for Untidy test work, feature work, decisions, and cleanup.

- Canonical tracker: GitHub Issues at `https://github.com/keylimesoda/Untidy/issues`
- Workflow rules: `docs/work-items/WORKFLOW.md`
- Local import mirror: `docs/work-items/work-items.json`
- Human board: generated at `docs/work-items/BOARD.md`
- Helper: `scripts/work-items.py`

Why GitHub Issues?

- It is tied to the repo, durable, and visible beside code/specs/commits.
- Labels/milestones provide the Jira-like workflow without another service.
- The local JSON/board is now a staging/import mirror, not the canonical tracker.

Common commands:

```bash
python3 scripts/work-items.py list
python3 scripts/work-items.py board
python3 scripts/work-items.py set UNTIDY-002 status review
python3 scripts/work-items.py set UNTIDY-009 owner Tommy
```

Status values currently used:

- `todo`
- `in-progress`
- `review`
- `done`
- `blocked`

Priority values:

- `P0` — blocks core validation or app stability
- `P1` — high-value next feature/test
- `P2` — important but after playback baseline
- `P3` — cleanup/polish
