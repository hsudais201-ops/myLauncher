#!/usr/bin/env python3
"""Regression tests for optional Feature Store package integrity metadata."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_feature_catalog import validate_items  # noqa: E402


VALID_SHA256 = "a" * 64


def feature(**overrides: object) -> dict:
    value = {
        "id": "external-feature",
        "name": "External feature",
        "builtIn": False,
        "packageUrl": "https://downloads.example.test/feature.zip",
        "sha256": VALID_SHA256,
    }
    value.update(overrides)
    return value


class FeatureCatalogPackageIntegrityTests(unittest.TestCase):
    def test_https_package_with_sha256_is_accepted(self) -> None:
        self.assertEqual(
            validate_items({"items": [feature()]}),
            ["external-feature"],
        )

    def test_package_without_sha256_is_rejected(self) -> None:
        item = feature()
        item.pop("sha256")
        with self.assertRaisesRegex(SystemExit, "pin a 64-character SHA-256"):
            validate_items({"items": [item]})

    def test_non_https_package_is_rejected(self) -> None:
        with self.assertRaisesRegex(SystemExit, "must be HTTPS"):
            validate_items({"items": [feature(packageUrl="http://downloads.example.test/feature.zip")]})

    def test_malformed_sha256_is_rejected(self) -> None:
        with self.assertRaisesRegex(SystemExit, "pin a 64-character SHA-256"):
            validate_items({"items": [feature(sha256="not-a-digest")]})

    def test_sha256_without_package_url_is_rejected(self) -> None:
        with self.assertRaisesRegex(SystemExit, "must not set sha256 without a packageUrl"):
            validate_items({"items": [feature(packageUrl=None)]})


if __name__ == "__main__":
    unittest.main()
