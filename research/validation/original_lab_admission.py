"""Fail-closed source admission for the EXISTING independent Python lab.

This does not execute or import external source. It only verifies a locally
supplied metadata manifest and declared source file hashes, returning an audit
status. A matching manifest is NOT proof of author identity, source safety,
training efficacy, or that the source is really the user's previous lab.

Never upload the user's protected athlete dataset or FIT files to CI.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
from typing import Any

SCHEMA = "stridelab-original-lab-source-handoff-v1"
REQUIRED = {"schema", "lab_id", "source_commit", "entrypoint_file",
            "source_files", "fixture_manifest_sha256", "policy_ids"}
MAX_FILE_SIZE = 2_000_000
SAFE_ROOTS = {"src", "tests", "schemas"}
SAFE_TOP_FILES = {"pyproject.toml", "README.md"}
FORBIDDEN_PARTS = {"datasets", "data", "private", "secrets", "credentials",
                   "__pycache__", ".git", "activities", "recordings"}
SHA256 = re.compile(r"^[a-f0-9]{64}$")
COMMIT = re.compile(r"^[a-f0-9]{40}$")


class InvalidLabHandoff(ValueError):
    pass


def _relative_source_path(value: Any) -> PurePosixPath:
    if not isinstance(value, str) or not value:
        raise InvalidLabHandoff("MISSING_FILE_PATH")
    if "\\" in value or "\x00" in value or value.startswith("/"):
        raise InvalidLabHandoff("UNSAFE_SOURCE_PATH")
    p = PurePosixPath(value)
    if not p.parts or any(part in ("", ".", "..") for part in p.parts):
        raise InvalidLabHandoff("UNSAFE_SOURCE_PATH")
    if any(part.lower() in FORBIDDEN_PARTS for part in p.parts):
        raise InvalidLabHandoff("PRIVATE_SOURCE_PATH_FORBIDDEN")
    if not ((p.parts[0] in SAFE_ROOTS and p.suffix in (".py", ".json", ".yaml", ".yml", ".toml"))
            or value in SAFE_TOP_FILES):
        raise InvalidLabHandoff("SOURCE_PATH_NOT_ALLOWLISTED")
    return p


def _manifest_contract(manifest: dict[str, Any]) -> list[tuple[PurePosixPath, str]]:
    if not isinstance(manifest, dict) or not REQUIRED.issubset(manifest):
        raise InvalidLabHandoff("MISSING_MANIFEST_FIELDS")
    if manifest.get("schema") != SCHEMA:
        raise InvalidLabHandoff("UNSUPPORTED_MANIFEST_SCHEMA")
    if not isinstance(manifest["lab_id"], str) or not manifest["lab_id"].strip():
        raise InvalidLabHandoff("MISSING_LAB_ID")
    if not isinstance(manifest["source_commit"], str) or not COMMIT.fullmatch(manifest["source_commit"]):
        raise InvalidLabHandoff("INVALID_SOURCE_COMMIT")
    if not isinstance(manifest["fixture_manifest_sha256"], str) or not SHA256.fullmatch(manifest["fixture_manifest_sha256"]):
        raise InvalidLabHandoff("INVALID_FIXTURE_REFERENCE")
    policies = manifest["policy_ids"]
    if not isinstance(policies, list) or not policies or any(
        not isinstance(x, str) or not re.fullmatch(r"[a-zA-Z0-9_.-]{3,80}", x)
        for x in policies
    ) or len(set(policies)) != len(policies):
        raise InvalidLabHandoff("INVALID_POLICY_IDS")
    files = manifest["source_files"]
    if not isinstance(files, list) or not files or len(files) > 1000:
        raise InvalidLabHandoff("INVALID_SOURCE_INVENTORY")
    result: list[tuple[PurePosixPath, str]] = []
    seen = set()
    for row in files:
        if not isinstance(row, dict):
            raise InvalidLabHandoff("INVALID_SOURCE_ENTRY")
        p = _relative_source_path(row.get("path"))
        digest = row.get("sha256")
        if not isinstance(digest, str) or not SHA256.fullmatch(digest):
            raise InvalidLabHandoff("INVALID_SOURCE_SHA256")
        if str(p) in seen:
            raise InvalidLabHandoff("DUPLICATE_SOURCE_FILE")
        seen.add(str(p))
        result.append((p, digest))
    entry = str(_relative_source_path(manifest["entrypoint_file"]))
    if entry not in seen or not entry.endswith(".py"):
        raise InvalidLabHandoff("ENTRYPOINT_NOT_IN_SOURCE_MANIFEST")
    return result


def inspect_source(root: str | Path | None, manifest_name: str = "lab-handoff.json") -> dict[str, Any]:
    """Treat absent source as BLOCKED, invalid source as REJECTED."""
    result: dict[str, Any] = {
        "schema": "stridelab-original-lab-admission-result-v1",
        "original_lab_status": "BLOCKED_SOURCE_NOT_PROVIDED",
        "verification": "NOT_PERFORMED",
        "execution": "NOT_PERFORMED",
        "origin_verified": False,
        "athlete_data_used": False,
        "review_required": True,
        "files_verified": 0,
    }
    if root is None:
        return result
    root = Path(root).resolve()
    manifest_path = root / manifest_name
    if not root.is_dir() or not manifest_path.is_file():
        result.update(original_lab_status="BLOCKED_SOURCE_OR_MANIFEST_MISSING")
        return result
    try:
        raw = manifest_path.read_bytes()
        if len(raw) > MAX_FILE_SIZE:
            raise InvalidLabHandoff("MANIFEST_TOO_LARGE")
        manifest = json.loads(raw)
        entries = _manifest_contract(manifest)
        hashes = []
        for relative, wanted in entries:
            real = root.joinpath(*relative.parts)
            if real.is_symlink() or not real.is_file():
                raise InvalidLabHandoff("SOURCE_FILE_MISSING_OR_SYMLINK")
            if not real.resolve().is_relative_to(root):
                raise InvalidLabHandoff("SOURCE_OUTSIDE_ROOT")
            if real.stat().st_size > MAX_FILE_SIZE:
                raise InvalidLabHandoff("SOURCE_FILE_TOO_LARGE")
            digest = hashlib.sha256(real.read_bytes()).hexdigest()
            if digest != wanted:
                raise InvalidLabHandoff("SOURCE_HASH_MISMATCH")
            hashes.append(digest)
        result.update(
            original_lab_status="READY_FOR_MANUAL_ORIGIN_AND_INTERFACE_REVIEW",
            verification="MANIFEST_AND_LISTED_FILE_HASHES_MATCH",
            manifest_sha256=hashlib.sha256(raw).hexdigest(),
            source_commit_claim=manifest["source_commit"],
            policy_ids_claimed=list(manifest["policy_ids"]),
            source_inventory_sha256=hashlib.sha256("".join(hashes).encode()).hexdigest(),
            files_verified=len(entries),
        )
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        result.update(original_lab_status="REJECTED_SOURCE_HANDOFF",
                      verification="FAILED",
                      rejection_code=str(exc) if isinstance(exc, InvalidLabHandoff)
                                     else type(exc).__name__)
    return result


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--source-root", default=None)
    p.add_argument("--output", default="research-original-lab-admission.json")
    args = p.parse_args()
    output = inspect_source(args.source_root)
    out = Path(args.output)
    out.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("ORIGINAL LAB ADMISSION:", output["original_lab_status"])
    print("Execution:", output["execution"], "Origin verified:", output["origin_verified"])
    print("No protected athlete dataset was read.")
    return 0 if output["original_lab_status"] != "REJECTED_SOURCE_HANDOFF" else 1


if __name__ == "__main__":
    raise SystemExit(main())
