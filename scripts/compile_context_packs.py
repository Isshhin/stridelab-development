#!/usr/bin/env python3
r"""
Validate the StrideLab Obsidian Knowledge Graph (`docs/wiki/`), compile portable
single-file Markdown context packs (`docs/context-packs/`), and safely mirror
managed outputs into `F:\StrideLab\Workflow Vault` without touching existing user notes.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import re
import shutil
import subprocess
import sys


DEFAULT_WIKI_DIR = Path("docs/wiki")
DEFAULT_PACKS_DIR = Path("docs/context-packs")
DEFAULT_VAULT_PATH = Path(r"F:\StrideLab\Workflow Vault")
INDEX_STEM = "00-StrideLab-Index"

FENCED_CODE_RE = re.compile(r"```.*?```", re.DOTALL)
INLINE_CODE_RE = re.compile(r"`[^`\n]+`")
WIKILINK_RE = re.compile(r"\[\[([^\]\n]+)\]\]")
FRONTMATTER_RE = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n", re.DOTALL)

SENSITIVE_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("private_key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("openai_like_key", re.compile(r"\bsk-[A-Za-z0-9]{16,}\b")),
    ("github_token", re.compile(r"\bghp_[A-Za-z0-9]{20,}\b")),
    ("jwt_token", re.compile(r"\beyJhbGci[A-Za-z0-9_-]*\.[A-Za-z0-9._-]+\b")),
    (
        "raw_uuid",
        re.compile(
            r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"
        ),
    ),
    (
        "email_address",
        re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    ),
]


@dataclass(frozen=True)
class WikiNode:
    stem: str
    rel_path: Path
    abs_path: Path
    raw_text: str
    frontmatter: dict[str, str | list[str]]
    body: str
    links: list[str]

    @property
    def title(self) -> str:
        val = self.frontmatter.get("title")
        if isinstance(val, str) and val.strip():
            return val.strip().strip('"').strip("'")
        return self.stem


def parse_frontmatter(raw: str) -> tuple[dict[str, str | list[str]], str]:
    match = FRONTMATTER_RE.match(raw)
    if not match:
        return {}, raw
    fm_block = match.group(1)
    body = raw[match.end() :]
    meta: dict[str, str | list[str]] = {}
    current_list_key: str | None = None
    for line in fm_block.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.startswith("- ") and current_list_key is not None:
            item = stripped[2:].strip().strip('"').strip("'")
            existing = meta.setdefault(current_list_key, [])
            if isinstance(existing, list):
                existing.append(item)
            continue
        current_list_key = None
        if ":" in line:
            key, val = line.split(":", 1)
            key = key.strip()
            val = val.strip()
            if val == "":
                meta[key] = []
                current_list_key = key
            else:
                meta[key] = val.strip('"').strip("'")
    return meta, body.lstrip("\r\n")


def strip_code_spans(text: str) -> str:
    without_fenced = FENCED_CODE_RE.sub("", text)
    return INLINE_CODE_RE.sub("", without_fenced)


def extract_wikilinks(text: str) -> list[str]:
    clean = strip_code_spans(text)
    targets: list[str] = []
    for raw_target in WIKILINK_RE.findall(clean):
        target = raw_target.split("|", 1)[0].split("#", 1)[0].strip()
        if target:
            targets.append(target)
    return targets


def load_nodes(wiki_dir: Path) -> tuple[dict[str, WikiNode], list[tuple[str, list[Path]]]]:
    nodes: dict[str, WikiNode] = {}
    by_stem: dict[str, list[Path]] = {}
    for md_path in sorted(wiki_dir.rglob("*.md")):
        stem = md_path.stem
        rel = md_path.relative_to(wiki_dir)
        by_stem.setdefault(stem, []).append(rel)
        raw_text = md_path.read_text(encoding="utf-8")
        fm, body = parse_frontmatter(raw_text)
        links = extract_wikilinks(body)
        if stem not in nodes:
            nodes[stem] = WikiNode(
                stem=stem,
                rel_path=rel,
                abs_path=md_path,
                raw_text=raw_text,
                frontmatter=fm,
                body=body,
                links=links,
            )
    collisions = [(stem, paths) for stem, paths in by_stem.items() if len(paths) > 1]
    return nodes, collisions


def scan_sensitive(label: str, text: str) -> list[str]:
    findings: list[str] = []
    for kind, pattern in SENSITIVE_PATTERNS:
        for match in pattern.finditer(text):
            findings.append(f"{label}: flagged {kind} match ({match.group(0)[:18]}...)")
    return findings


def validate_graph(
    nodes: dict[str, WikiNode], collisions: list[tuple[str, list[Path]]]
) -> tuple[list[str], list[str], list[str], list[str]]:
    collision_msgs = [
        f"{stem}: {', '.join(p.as_posix() for p in paths)}" for stem, paths in collisions
    ]
    broken: list[str] = []
    incoming: dict[str, set[str]] = {stem: set() for stem in nodes}
    pii_hits: list[str] = []

    for stem, node in sorted(nodes.items()):
        if not node.frontmatter:
            broken.append(f"{node.rel_path.as_posix()}: missing YAML frontmatter")
        for target in node.links:
            if target not in nodes:
                broken.append(f"{node.rel_path.as_posix()} -> [[{target}]] (missing target)")
            elif target != stem:
                incoming[target].add(stem)
        pii_hits.extend(scan_sensitive(node.rel_path.as_posix(), node.raw_text))

    orphans = sorted(
        node.rel_path.as_posix()
        for stem, node in nodes.items()
        if stem != INDEX_STEM and len(incoming[stem]) == 0
    )
    return collision_msgs, broken, orphans, pii_hits


def git_short_commit(repo_root: Path) -> str:
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=repo_root,
            text=True,
            stderr=subprocess.DEVNULL,
        )
        return out.strip() or "unknown"
    except Exception:
        return "unknown"


def slugify_heading(title: str) -> str:
    slug = title.lower()
    slug = re.sub(r"[^a-z0-9\s-]", "", slug)
    slug = re.sub(r"\s+", "-", slug.strip())
    return slug


def order_full_nodes(nodes: dict[str, WikiNode]) -> list[WikiNode]:
    folder_rank = {
        ".": 0,
        "architecture": 1,
        "features": 2,
        "ui": 3,
        "decisions": 4,
        "roadmap": 5,
    }
    preferred_arch = [
        "App-Architecture",
        "Engine-1.5",
        "Storage-and-FIT-Contracts",
        "Hard-Guardrails",
    ]

    def sort_key(node: WikiNode) -> tuple[int, int, str]:
        parent = node.rel_path.parent.as_posix()
        rank = folder_rank.get(parent, 99)
        sub_rank = preferred_arch.index(node.stem) if node.stem in preferred_arch else 50
        return (rank, sub_rank, node.rel_path.as_posix())

    return sorted(nodes.values(), key=sort_key)


def build_pack_markdown(
    pack_title: str,
    subtitle: str,
    commit_hash: str,
    generated_utc: str,
    ordered_nodes: list[WikiNode],
) -> str:
    lines: list[str] = [
        f"# {pack_title}",
        "",
        f"> **Generated:** {generated_utc}  ",
        f"> **Verified Commit:** `{commit_hash}`  ",
        f"> **Included Nodes:** {len(ordered_nodes)}  ",
        f"> **Purpose:** {subtitle}",
        "",
        "## Table of Contents",
    ]
    for i, node in enumerate(ordered_nodes, start=1):
        anchor = slugify_heading(f"{i}. {node.title}")
        status = node.frontmatter.get("status", "verified")
        lines.append(
            f"{i}. [{node.title}](#{anchor}) — `{node.rel_path.as_posix()}` (`{status}`)"
        )

    for i, node in enumerate(ordered_nodes, start=1):
        area = node.frontmatter.get("area", "unknown")
        status = node.frontmatter.get("status", "unknown")
        key_files = node.frontmatter.get("key_files", [])
        lines.extend(
            [
                "",
                "---",
                "",
                f"## {i}. {node.title}",
                "",
                f"- **Source Node:** `docs/wiki/{node.rel_path.as_posix()}`",
                f"- **Area / Status:** `{area}` / `{status}`",
            ]
        )
        if isinstance(key_files, list) and key_files:
            files_fmt = ", ".join(f"`{f}`" for f in key_files)
            lines.append(f"- **Key Files:** {files_fmt}")
        lines.extend(["", node.body.rstrip()])

    lines.append("")
    return "\n".join(lines)


def mirror_directory(src: Path, dst: Path) -> int:
    dst.mkdir(parents=True, exist_ok=True)
    src_files = {p.relative_to(src): p for p in src.rglob("*") if p.is_file()}
    for existing in sorted(dst.rglob("*"), reverse=True):
        rel = existing.relative_to(dst)
        if existing.is_file() and rel not in src_files:
            existing.unlink()
        elif existing.is_dir() and not any(existing.iterdir()):
            existing.rmdir()
    copied = 0
    for rel, src_file in sorted(src_files.items()):
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src_file, target)
        copied += 1
    return copied


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate StrideLab docs/wiki Knowledge Graph, compile single-file context packs, and mirror safely to Workflow Vault."
    )
    parser.add_argument("--wiki-dir", type=Path, default=DEFAULT_WIKI_DIR)
    parser.add_argument("--packs-dir", type=Path, default=DEFAULT_PACKS_DIR)
    parser.add_argument("--vault-path", type=Path, default=DEFAULT_VAULT_PATH)
    parser.add_argument(
        "--no-vault-sync",
        action="store_true",
        help="Skip mirroring to Obsidian Workflow Vault.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    wiki_dir = args.wiki_dir if args.wiki_dir.is_absolute() else repo_root / args.wiki_dir
    packs_dir = args.packs_dir if args.packs_dir.is_absolute() else repo_root / args.packs_dir

    if not wiki_dir.is_dir():
        print(f"ERROR: Wiki directory not found: {wiki_dir}", file=sys.stderr)
        return 1

    nodes, collisions = load_nodes(wiki_dir)
    if not nodes:
        print(f"ERROR: No Markdown nodes found under {wiki_dir}", file=sys.stderr)
        return 1

    collision_msgs, broken, orphans, pii_hits = validate_graph(nodes, collisions)
    print(f"Graph Nodes: {len(nodes)}")
    print(f"Duplicate Filenames: {len(collision_msgs)}")
    print(f"Broken Wikilinks: {len(broken)}")
    print(f"Orphan Nodes: {len(orphans)}")
    print(f"PII / Secret Findings: {len(pii_hits)}")

    if collision_msgs or broken or orphans or pii_hits:
        for msg in collision_msgs:
            print(f"  [COLLISION] {msg}", file=sys.stderr)
        for msg in broken:
            print(f"  [BROKEN LINK] {msg}", file=sys.stderr)
        for msg in orphans:
            print(f"  [ORPHAN NODE] {msg}", file=sys.stderr)
        for msg in pii_hits:
            print(f"  [SENSITIVE DATA] {msg}", file=sys.stderr)
        return 1

    commit_hash = git_short_commit(repo_root)
    generated_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    packs_dir.mkdir(parents=True, exist_ok=True)

    full_nodes = order_full_nodes(nodes)
    pack_specs: list[tuple[str, str, str, list[WikiNode]]] = [
        (
            "STRIDELAB_FULL_CONTEXT.md",
            "StrideLab — Full Portable Context Pack",
            "Complete architecture, guardrails, active features, UI system, ADRs, and marked roadmap ideas.",
            full_nodes,
        ),
        (
            "STRIDELAB_UI_PACK.md",
            "StrideLab — UI Design System, Screens & Manual Logging Pack",
            "AMOLED UI system, screens map, Freeform Logger, Templates/Custom Planner, and guardrails.",
            [
                nodes[s]
                for s in [
                    "00-StrideLab-Index",
                    "UI-Design-System",
                    "Screens-Map",
                    "Freeform-Logger",
                    "Templates-and-Custom-Planner",
                    "Hard-Guardrails",
                ]
            ],
        ),
        (
            "STRIDELAB_ENGINE_PACK.md",
            "StrideLab — Architecture, Engine 1.5 & FIT Contracts Pack",
            "App architecture, deterministic Engine 1.5 coaching rules, SQLite/FIT contracts, Guided Player, and ADRs.",
            [
                nodes[s]
                for s in [
                    "00-StrideLab-Index",
                    "App-Architecture",
                    "Engine-1.5",
                    "Storage-and-FIT-Contracts",
                    "Hard-Guardrails",
                    "Guided-Workout-Player-and-Debrief",
                    "ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging",
                    "ADR-03-Offline-First-Independent-Local-Ownership",
                ]
            ],
        ),
        (
            "STRIDELAB_SHARESTUDIO_PACK.md",
            "StrideLab — Share Studio & Privacy Architecture Pack",
            "Share Studio canvas renderer, dark studio tray, privacy trimming, pixel invariants, and UI design system.",
            [
                nodes[s]
                for s in [
                    "00-StrideLab-Index",
                    "Share-Studio",
                    "UI-Design-System",
                    "Hard-Guardrails",
                    "ADR-02-GPS-200m-Default-Privacy-Trimming",
                ]
            ],
        ),
    ]

    bundle_pii_hits: list[str] = []
    print("\nGenerated Context Packs:")
    for filename, title, subtitle, selected_nodes in pack_specs:
        content = build_pack_markdown(title, subtitle, commit_hash, generated_utc, selected_nodes)
        bundle_pii_hits.extend(scan_sensitive(filename, content))
        if bundle_pii_hits:
            for msg in bundle_pii_hits:
                print(f"  [BUNDLE SENSITIVE DATA] {msg}", file=sys.stderr)
            return 1
        out_path = packs_dir / filename
        out_path.write_text(content, encoding="utf-8", newline="\n")
        size_kb = out_path.stat().st_size / 1024.0
        approx_tokens = len(content) // 4
        print(
            f"  - {out_path.relative_to(repo_root).as_posix()}: {size_kb:.2f} KB (~{approx_tokens} tokens, {len(selected_nodes)} nodes)"
        )

    if not args.no_vault_sync:
        kg_dst = args.vault_path / "Knowledge-Graph"
        packs_dst = args.vault_path / "Context-Packs"
        kg_count = mirror_directory(wiki_dir, kg_dst)
        pack_count = mirror_directory(packs_dir, packs_dst)
        print(f"\nVault Synced ({args.vault_path}):")
        print(f"  - Knowledge-Graph: {kg_count} files -> {kg_dst}")
        print(f"  - Context-Packs: {pack_count} files -> {packs_dst}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
