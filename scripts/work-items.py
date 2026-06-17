#!/usr/bin/env python3
"""Tiny repo-local work item tracker for Untidy.

Examples:
  python3 scripts/work-items.py list
  python3 scripts/work-items.py board
  python3 scripts/work-items.py set UNTIDY-002 status review
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "docs" / "work-items" / "work-items.json"
BOARD = ROOT / "docs" / "work-items" / "BOARD.md"
STATUSES = ["in-progress", "review", "todo", "blocked", "done"]


def load_items() -> list[dict[str, Any]]:
    return json.loads(STORE.read_text())


def save_items(items: list[dict[str, Any]]) -> None:
    STORE.write_text(json.dumps(items, indent=2) + "\n")


def item_sort_key(item: dict[str, Any]) -> tuple[str, str]:
    return (item.get("priority", "P9"), item.get("id", ""))


def render_board(items: list[dict[str, Any]]) -> str:
    lines = ["# Untidy Work Board", "", "_Generated from `work-items.json`._", ""]
    by_status: dict[str, list[dict[str, Any]]] = {status: [] for status in STATUSES}
    for item in items:
        by_status.setdefault(item.get("status", "todo"), []).append(item)
    for status, bucket in by_status.items():
        lines += [f"## {status}", ""]
        if not bucket:
            lines += ["_None._", ""]
            continue
        for item in sorted(bucket, key=item_sort_key):
            labels = ", ".join(item.get("labels", []))
            owner = item.get("owner", "unassigned")
            spec = item.get("spec")
            lines.append(f"### {item['id']} — {item['title']}")
            lines.append("")
            lines.append(f"- Priority: {item.get('priority', '')}")
            lines.append(f"- Type: {item.get('type', '')}")
            lines.append(f"- Area: {item.get('area', '')}")
            lines.append(f"- Owner: {owner}")
            if labels:
                lines.append(f"- Labels: {labels}")
            if spec:
                lines.append(f"- Spec: `{spec}`")
            if item.get("githubUrl"):
                lines.append(f"- GitHub: {item['githubUrl']}")
            nxt = item.get("next")
            if nxt:
                lines.append(f"- Next: {nxt}")
            acceptance = item.get("acceptance", [])
            if acceptance:
                lines.append("- Acceptance:")
                for criterion in acceptance:
                    lines.append(f"  - {criterion}")
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def cmd_list(args: argparse.Namespace) -> None:
    items = load_items()
    if args.status:
        items = [i for i in items if i.get("status") == args.status]
    for item in sorted(items, key=item_sort_key):
        print(f"{item['id']} [{item.get('priority')}/{item.get('status')}] {item['title']}")


def cmd_board(args: argparse.Namespace) -> None:
    text = render_board(load_items())
    BOARD.write_text(text)
    if not args.quiet:
        print(f"wrote {BOARD}")


def cmd_set(args: argparse.Namespace) -> None:
    items = load_items()
    for item in items:
        if item.get("id") == args.id:
            current = item.get(args.field)
            item[args.field] = args.value
            save_items(items)
            BOARD.write_text(render_board(items))
            print(f"{args.id}: {args.field} {current!r} -> {args.value!r}")
            return
    raise SystemExit(f"unknown work item id: {args.id}")


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(required=True)

    p_list = sub.add_parser("list")
    p_list.add_argument("--status")
    p_list.set_defaults(func=cmd_list)

    p_board = sub.add_parser("board")
    p_board.add_argument("--quiet", action="store_true")
    p_board.set_defaults(func=cmd_board)

    p_set = sub.add_parser("set")
    p_set.add_argument("id")
    p_set.add_argument("field", choices=["status", "owner", "priority", "next"])
    p_set.add_argument("value")
    p_set.set_defaults(func=cmd_set)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
