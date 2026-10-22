#!/usr/bin/env python3
"""Validate Voidcraft's authored Feature Store catalog and bundled fallback."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATHS = (
    ROOT / "store" / "catalog.json",
    ROOT / "app" / "src" / "main" / "assets" / "store" / "catalog.json",
)


def read_catalog(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Invalid catalog at {path.relative_to(ROOT)}: {exc}") from exc
    if not isinstance(value, dict) or value.get("schemaVersion") != 1:
        raise SystemExit(f"{path.relative_to(ROOT)} must use catalog schema version 1.")
    items = value.get("items")
    if not isinstance(items, list) or not items:
        raise SystemExit(f"{path.relative_to(ROOT)} must contain an explicit, non-empty items list.")
    return value


def validate_items(catalog: dict) -> list[str]:
    ids: list[str] = []
    for index, item in enumerate(catalog["items"]):
        if not isinstance(item, dict):
            raise SystemExit(f"Catalog item {index} must be an object.")
        feature_id = item.get("id")
        name = item.get("name")
        if not isinstance(feature_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,100}", feature_id):
            raise SystemExit(f"Catalog item {index} has an invalid id.")
        if not isinstance(name, str) or not name.strip():
            raise SystemExit(f"Catalog item {feature_id} has no display name.")
        if feature_id in ids:
            raise SystemExit(f"Duplicate Feature Store id: {feature_id}")
        ids.append(feature_id)
        if not isinstance(item.get("builtIn"), bool):
            raise SystemExit(f"Catalog item {feature_id} must explicitly set builtIn to true or false.")
        package_url = item.get("packageUrl")
        package_digest = item.get("sha256")
        if package_url is not None:
            if not isinstance(package_url, str):
                raise SystemExit(f"Catalog item {feature_id} has an invalid packageUrl.")
            parsed = urlparse(package_url)
            if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
                raise SystemExit(f"Catalog item {feature_id} packageUrl must be HTTPS without embedded credentials.")
            if not isinstance(package_digest, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", package_digest):
                raise SystemExit(f"Catalog item {feature_id} must pin a 64-character SHA-256 digest for its package.")
        elif package_digest is not None:
            raise SystemExit(f"Catalog item {feature_id} must not set sha256 without a packageUrl.")
    return ids


def main() -> None:
    source, bundled = (read_catalog(path) for path in CATALOG_PATHS)
    source_ids = validate_items(source)
    bundled_ids = validate_items(bundled)
    if source != bundled:
        raise SystemExit(
            "The source and bundled catalogs differ. Copy store/catalog.json to "
            "app/src/main/assets/store/catalog.json after editing the store."
        )
    print(f"Feature Store catalog valid: {len(source_ids)} explicitly listed features.")
    print("Feature IDs: " + ", ".join(source_ids))


if __name__ == "__main__":
    main()
