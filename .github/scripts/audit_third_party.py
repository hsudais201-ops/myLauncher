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

LICENSE_MARKERS = ("license", "licence", "copying", "notice", "copyright", "patent")
LICENSE_DIR_NAMES = {"license", "licenses", "notices", "copyright", "copyrights"}
SKIP_DIRS = {".git", ".gradle", "build", ".idea", ".kotlin"}
APK_EXTENSIONS = {".so", ".jar", ".dex", ".ttf", ".otf", ".png", ".webp", ".jpg", ".jpeg", ".json"}
BLOCKED_APK_BASENAMES = {"minecraft_ten.ttf"}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_license_candidate(path: Path) -> bool:
    lowered = path.name.lower()
    if any(part.lower() in LICENSE_DIR_NAMES for part in path.parts[:-1]):
        return True
    marker_pattern = r"(^|[._-])(" + "|".join(LICENSE_MARKERS) + r"s?)([._-]|$)"
    return bool(re.search(marker_pattern, lowered))


def license_inventory(root: Path) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    for current, dirs, files in os.walk(root):
        dirs[:] = sorted(name for name in dirs if name not in SKIP_DIRS)
        for name in sorted(files):
            path = Path(current) / name
            if not is_license_candidate(path):
                continue
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


def declared_submodule_inventory(root: Path, status_text: str) -> list[dict[str, str]]:
    config_path = root / ".gitmodules"
    if not config_path.is_file():
        return []
    declarations: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    for line in config_path.read_text(encoding="utf-8", errors="replace").splitlines():
        section = re.match(r'^\s*\[submodule\s+"([^"]+)"\]\s*$', line)
        if section:
            if current is not None:
                declarations.append(current)
            current = {"name": section.group(1), "path": "", "url": ""}
            continue
        if current is not None:
            key_value = re.match(r"^\s*(path|url)\s*=\s*(.*?)\s*$", line)
            if key_value:
                current[key_value.group(1)] = key_value.group(2)
    if current is not None:
        declarations.append(current)
    observed = {item.get("path", ""): item for item in submodule_inventory(status_text) if item.get("path")}
    for item in declarations:
        status = observed.get(item["path"])
        item["checkout_state"] = status["state"] if status else "declared-but-no-submodule-status-entry"
        item["checked_out_commit"] = status.get("commit", "UNKNOWN") if status else "UNKNOWN"
        if not item["path"]:
            item["checkout_state"] = "invalid-declaration-missing-path"
    return declarations


def upstream_credit_inventory(root: Path) -> list[dict[str, str]]:
    readme = root / "README.md"
    if not readme.is_file():
        return []
    lines = readme.read_text(encoding="utf-8", errors="replace").splitlines()
    start = next((i for i, line in enumerate(lines) if re.match(r"^##\s+Credits\s*&\s*Dependencies\s*$", line, re.I)), None)
    if start is None:
        return []
    results: list[dict[str, str]] = []
    for line in lines[start + 1:]:
        if re.match(r"^##\s+", line):
            break
        match = re.match(r"^\s*[-*]\s+(.+?)\s*$", line)
        if not match:
            continue
        raw = match.group(1).strip()
        lowered = raw.lower()
        unresolved = bool(re.search(r"\bunknown\b|all rights reserved|\barr\b", lowered))
        urls = re.findall(r"\]\((https?://[^)]+)\)", raw)
        results.append({
            "entry": raw,
            "license_review": "review-required" if unresolved else "verify-exact-component-license",
            "source_urls": "; ".join(urls),
        })
    return results


def blocked_apk_asset_paths(entries: list[dict[str, Any]]) -> list[str]:
    """Return package paths that this repository explicitly excludes pending rights proof."""
    return sorted({
        str(entry["path"])
        for entry in entries
        if Path(str(entry["path"])).name.lower() in BLOCKED_APK_BASENAMES
    })


def potential_content_review_candidates(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    patterns = re.compile(r"minecraft|mojang|mc-head|angle_license|pffft|openal-soft", re.I)
    return [entry for entry in entries if patterns.search(str(entry["path"]))]


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
            stripped = line.strip()
            if not stripped or stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            if re.match(r"^(implementation|api|runtimeOnly|compileOnly|classpath|kapt|annotationProcessor)\b(?:\s+|\s*\()", stripped):
                results.append({"path": path.relative_to(root).as_posix(), "line": str(number), "text": stripped[:300]})
    return results


def build_report(source: Path, apk: Path, submodule_text: str) -> dict[str, Any]:
    selected_apk_entries = apk_inventory(apk)
    return {
        "scope": "Pinned legacy Amethyst-based build only",
        "legal_status": "INCOMPLETE — inventory evidence only; not legal clearance",
        "source_root": str(source),
        "apk": str(apk),
        "apk_sha256": sha256_file(apk),
        "submodules": submodule_inventory(submodule_text),
        "declared_submodules": declared_submodule_inventory(source, submodule_text),
        "license_notice_files": license_inventory(source),
        "upstream_credits": upstream_credit_inventory(source),
        "gradle_dependency_declarations": gradle_dependency_lines(source),
        "apk_selected_entries": selected_apk_entries,
        "blocked_asset_paths": blocked_apk_asset_paths(selected_apk_entries),
        "potential_content_review_candidates": potential_content_review_candidates(selected_apk_entries),
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
        "- APK SHA-256: " + report["apk_sha256"],
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
    lines.extend(["", "## Submodules declared by .gitmodules", "", "| Name | Path | URL | Checkout state | Commit |", "|---|---|---|---|---|"])
    for item in report["declared_submodules"]:
        lines.append("| " + item.get("name", "").replace("|", "\\|") + " | " + item.get("path", "").replace("|", "\\|") + " | " + item.get("url", "").replace("|", "\\|") + " | " + item.get("checkout_state", "UNKNOWN") + " | " + item.get("checked_out_commit", "UNKNOWN") + " |")
    if not report["declared_submodules"]:
        lines.append("| No .gitmodules declarations found | — | — | UNKNOWN | UNKNOWN |")
    lines.extend(["", "## License / notice candidates", "", "| Path | Bytes | SHA-256 |", "|---|---:|---|"])
    for item in report["license_notice_files"]:
        lines.append("| " + item["path"] + " | " + item["bytes"] + " | " + item["sha256"] + " |")
    if not report["license_notice_files"]:
        lines.append("| No matching files found | — | — |")
    lines.extend(["", "## Upstream README credits and dependency claims", "", "| Review status | Credit / component | Source URL(s) |", "|---|---|---|"])
    for item in report["upstream_credits"]:
        entry = item["entry"].replace("|", "\\|")
        urls = item["source_urls"].replace("|", "\\|")
        lines.append("| " + item["license_review"] + " | " + entry + " | " + urls + " |")
    if not report["upstream_credits"]:
        lines.append("| No Credits & Dependencies section found | — | — |")
    lines.extend(["", "The status above is a triage flag based on the upstream README wording, not an independent determination that a component is infringing or redistributable. Verify each component's actual license at the exact source revision.", ""])
    lines.extend(["", "## Gradle dependency declaration candidates", "", "| File | Line | Declaration |", "|---|---:|---|"])
    for item in report["gradle_dependency_declarations"]:
        text = item["text"].replace("|", "\\|")
        lines.append("| " + item["path"] + " | " + item["line"] + " | " + text + " |")
    if not report["gradle_dependency_declarations"]:
        lines.append("| No matching declarations found | — | — |")
    lines.extend(["", "## Selected APK entries (path, size, SHA-256)", "", "| APK path | Bytes | SHA-256 |", "|---|---:|---|"])
    for item in report["apk_selected_entries"]:
        lines.append("| " + item["path"] + " | " + str(item["bytes"]) + " | " + item["sha256"] + " |")
    lines.extend(["", "## Blocked asset regression gate", "", "These filenames are excluded pending a verified redistribution grant. The build workflow fails if any blocked basename appears in the selected APK entry set.", ""])
    if report["blocked_asset_paths"]:
        lines.extend("- `" + path + "`" for path in report["blocked_asset_paths"])
    else:
        lines.append("No blocked asset paths were found in the selected APK entry set.")
    lines.extend(["", "## Potential content / component review candidates", "", "| APK path | Bytes | SHA-256 |", "|---|---:|---|"])
    for item in report["potential_content_review_candidates"]:
        lines.append("| " + str(item["path"]).replace("|", "\\|") + " | " + str(item["bytes"]) + " | " + str(item["sha256"]) + " |")
    if not report["potential_content_review_candidates"]:
        lines.append("| No filename-based review candidates identified | — | — |")
    lines.append("")
    lines.append("Filename matching is only a review aid. A match does not prove a rights violation, and no match does not prove a file is licensed.")
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
    if report["blocked_asset_paths"]:
        print("BLOCKED: unverified assets remain in the APK: " + ", ".join(report["blocked_asset_paths"]))
        return 3
    print("Blocked asset regression gate passed.")
    print("Legal clearance remains INCOMPLETE.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
