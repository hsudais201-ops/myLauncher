#!/usr/bin/env python3
"""Generate an evidence inventory for the pinned legacy source tree and APK.

This is a discovery aid, not a license scanner or legal clearance decision.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import zipfile
from pathlib import Path
from typing import Any

LICENSE_NAMES = ("license", "copying", "notice", "copyright", "patents")
SKIP_DIRS = {".git", ".gradle", "build", ".idea", ".kotlin"}
APK_EXTENSIONS = {".so", ".jar", ".dex", ".ttf", ".otf", ".png", ".webp", ".jpg", ".jpeg", ".json"}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def license_inventory(root: Path) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    for current, dirs, files in os.walk(root):
        dirs[:] = sorted(name for name in dirs if name not in SKIP_DIRS)
        for name in sorted(files):
            lowered = name.lower()
            if not any(lowered == prefix or lowered.startswith(prefix + ".") or lowered.startswith(prefix + "_") or lowered.startswith(prefix + "-") for prefix in LICENSE_NAMES):
                continue
            path = Path(current) / name
            try:
                items.append({
                    "path": path.relative_to(root).as_posix(),
                    "sha256": sha256_file(path),
                    "bytes": str(path.stat().st_size),
                })
            except OSError as exc:
                items.append({"path": path.relative_to(root).as_posix(), "sha256": "READ_ERROR", "bytes": str(exc)})
    return items


def submodule_inventory(text: str) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    for line in text.splitlines():
        line = line.rstrip()
        if not line:
            continue
        match = re.match(r"^([ +-U])([0-9a-fA-F]{40})\s+(\S+)(?:\s+\((.*)\))?$", line)
        if match:
            marker, commit, path, description = match.groups()
            state = {" ": "checked-out", "+": "wrong-commit", "-": "not-initialized", "U": "merge-conflict"}[marker]
            results.append({"path": path, "commit": commit, "state": state, "description": description or ""})
        else:
            results.append({"raw": line, "state": "unparsed"})
    return results


def apk_inventory(apk: Path) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    with zipfile.ZipFile(apk) as archive:
        for item in sorted(archive.infolist(), key=lambda entry: entry.filename):
            path = Path(item.filename)
            if path.suffix.lower() in APK_EXTENSIONS or "license" in item.filename.lower() or "notice" in item.filename.lower() or "meta-inf/" in item.filename.lower():
                results.append({
                    "path": item.filename,
                    "bytes": item.file_size,
                    "sha256": hashlib.sha256(archive.read(item)).hexdigest(),
                })
    return results


def gradle_dependency_lines(root: Path) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    paths = sorted(root.rglob("*.gradle")) + sorted(root.rglob("*.gradle.kts"))
    for path in paths:
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        for number, line in enumerate(lines, 1):
            if re.search(r"\b(implementation|api|runtimeOnly|compileOnly|classpath|kapt|annotationProcessor)\s*(\(|\s)", line):
                results.append({"path": path.relative_to(root).as_posix(), "line": str(number), "text": line.strip()[:300]})
    return results


def build_report(source: Path, apk: Path, submodule_text: str) -> dict[str, Any]:
    return {
        "scope": "Pinned legacy Amethyst-based build only",
        "legal_status": "INCOMPLETE — inventory evidence only; not legal clearance",
        "source_root": str(source),
        "apk": str(apk),
        "submodules": submodule_inventory(submodule_text),
        "license_notice_files": license_inventory(source),
        "gradle_dependency_declarations": gradle_dependency_lines(source),
        "apk_selected_entries": apk_inventory(apk),
        "limitations": [
            "Presence of a license file does not prove it applies to every file in that component.",
            "A missing license/notice file does not prove public-domain status or permission.",
            "APK entries are not all independently identifiable from filenames; inspect nested archives and native binaries as needed.",
            "Gradle declarations do not include every transitive dependency, dynamically downloaded file, or embedded license.",
            "Review exact component licenses, recursive submodule history, distribution terms, and required notices before redistribution.",
        ],
    }


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Legacy build third-party evidence inventory",
        "",
        "**Status: " + report["legal_status"] + "**",
        "",
        "Generated by the audit script. This is an evidence inventory, not legal advice, an SPDX SBOM, or a license compatibility determination.",
        "",
        "- Source checkout: " + report["source_root"],
        "- APK: " + report["apk"],
        "",
        "## Recursive submodule revisions",
        "",
        "| Path | Commit | Checkout state | Description |",
        "|---|---|---|---|",
    ]
    for item in report["submodules"]:
        lines.append("| {path} | {commit} | {state} | {description} |".format(
            path=item.get("path", item.get("raw", "")).replace("|", "\\|"),
            commit=item.get("commit", "UNKNOWN"),
            state=item.get("state", "UNKNOWN"),
            description=item.get("description", "").replace("|", "\\|"),
        ))
    if not report["submodules"]:
        lines.append("| No submodule status captured | UNKNOWN | UNKNOWN | Check workflow output |")
    lines.extend(["", "## License / notice candidates", "", "| Path | Bytes | SHA-256 |", "|---|---:|---|"])
    for item in report["license_notice_files"]:
        lines.append("| " + item["path"] + " | " + item["bytes"] + " | " + item["sha256"] + " |")
    if not report["license_notice_files"]:
        lines.append("| No matching files found | — | — |")
    lines.extend(["", "## Gradle dependency declaration candidates", "", "| File | Line | Declaration |", "|---|---:|---|"])
    for item in report["gradle_dependency_declarations"]:
        text = item["text"].replace("|", "\\|")
        lines.append("| " + item["path"] + " | " + item["line"] + " | " + text + " |")
    if not report["gradle_dependency_declarations"]:
        lines.append("| No matching declarations found | — | — |")
    lines.extend(["", "## Selected APK entries (path, size, SHA-256)", "", "| APK path | Bytes | SHA-256 |", "|---|---:|---|"])
    for item in report["apk_selected_entries"]:
        lines.append("| " + item["path"] + " | " + str(item["bytes"]) + " | " + item["sha256"] + " |")
    lines.extend(["", "## Interpretation limits", ""])
    lines.extend("- " + item for item in report["limitations"])
    lines.extend([
        "",
        "## Required human follow-up",
        "",
        "Map each shipped component to its exact source/version and verified license; inspect recursive submodules and transitive dependencies; reconcile upstream README credits with actual license texts; confirm notices/source obligations; and review distribution terms before publishing an APK.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Path to the fully checked-out upstream source tree")
    parser.add_argument("--apk", type=Path, required=True, help="Built APK to inspect")
    parser.add_argument("--submodules-file", type=Path, required=True, help="File containing git submodule status --recursive output")
    parser.add_argument("--output", type=Path, required=True, help="Markdown report output path")
    parser.add_argument("--json-output", type=Path, required=True, help="JSON evidence output path")
    args = parser.parse_args()
    if not args.source.is_dir():
        parser.error("source directory does not exist: " + str(args.source))
    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        parser.error("APK does not exist or is empty: " + str(args.apk))
    if not args.submodules_file.is_file():
        parser.error("submodule status file does not exist: " + str(args.submodules_file))
    report = build_report(args.source.resolve(), args.apk.resolve(), args.submodules_file.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render_markdown(report), encoding="utf-8")
    args.json_output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("Wrote evidence inventory: " + str(args.output))
    print("Wrote machine-readable evidence: " + str(args.json_output))
    print("License/notice candidates: " + str(len(report["license_notice_files"])))
    print("Selected APK entries: " + str(len(report["apk_selected_entries"])))
    print("Legal clearance remains INCOMPLETE.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
