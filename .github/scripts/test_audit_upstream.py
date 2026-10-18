from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("audit_upstream.py")
SPEC = importlib.util.spec_from_file_location("audit_upstream", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
audit_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit_module)


class AuditUpstreamTests(unittest.TestCase):
    def fixture(self, root: Path, *, manifest: str | None = None,
                gradle: str | None = None, application_mk: str | None = None) -> Path:
        upstream = root / "upstream"
        app = upstream / "app_pojavlauncher"
        (app / "src/main/jni").mkdir(parents=True)
        (app / "src/main").mkdir(parents=True, exist_ok=True)
        (app / "build.gradle").write_text(
            gradle or """android {
  compileSdk = 37
  defaultConfig {
    minSdkVersion 21
    targetSdkVersion 34
  }
}
""",
            encoding="utf-8",
        )
        (app / "src/main/AndroidManifest.xml").write_text(
            manifest or """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-feature android:glEsVersion="0x00020000" />
  <uses-feature android:name="android.hardware.type.pc" android:required="false" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
  <application android:allowBackup="true" android:resizeableActivity="true">
    <activity android:name=".LauncherActivity" android:exported="false" />
    <service android:name=".ProgressService" android:foregroundServiceType="dataSync" />
  </application>
</manifest>
""",
            encoding="utf-8",
        )
        (app / "src/main/jni/Application.mk").write_text(
            application_mk or """APP_PLATFORM := android-21
APP_STL := c++_shared
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
""",
            encoding="utf-8",
        )
        return upstream

    def test_report_captures_sdk_abis_permissions_and_backup_review(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            report, summary = audit_module.audit_upstream(self.fixture(Path(temp)))
        self.assertIn("| Android compile SDK | 37 |", report)
        self.assertIn("| Android minimum SDK (minSdkVersion) | 21 |", report)
        self.assertIn("| Android target SDK (targetSdkVersion) | 34 |", report)
        self.assertIn("armeabi-v7a, arm64-v8a, x86, x86_64", report)
        self.assertIn("android.permission.INTERNET", report)
        self.assertIn("maxSdkVersion", report)
        self.assertIn("android:allowBackup is true", report)
        self.assertIn("RECORD_AUDIO is declared", report)
        self.assertIn("target SDK 34", summary)

    def test_missing_internet_permission_fails_closed(self) -> None:
        manifest = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application />
</manifest>
"""
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp), manifest=manifest)
            with self.assertRaisesRegex(audit_module.AuditError, "INTERNET"):
                audit_module.audit_upstream(upstream)

    def test_inconsistent_sdk_levels_fail_closed(self) -> None:
        gradle = """android {
  compileSdk = 33
  defaultConfig {
    minSdkVersion 21
    targetSdkVersion 34
  }
}
"""
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp), gradle=gradle)
            with self.assertRaisesRegex(audit_module.AuditError, "compileSdk 33"):
                audit_module.audit_upstream(upstream)

    def test_unknown_abi_fails_closed(self) -> None:
        application_mk = """APP_PLATFORM := android-21
APP_ABI := armeabi-v7a invented-abi
"""
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp), application_mk=application_mk)
            with self.assertRaisesRegex(audit_module.AuditError, "unrecognized Android ABI"):
                audit_module.audit_upstream(upstream)

    def test_native_api_above_min_sdk_fails_closed(self) -> None:
        application_mk = """APP_PLATFORM := android-24
APP_ABI := arm64-v8a
"""
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp), application_mk=application_mk)
            with self.assertRaisesRegex(audit_module.AuditError, "exceeds app minSdkVersion"):
                audit_module.audit_upstream(upstream)

    def test_malformed_manifest_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp), manifest="<manifest")
            with self.assertRaisesRegex(audit_module.AuditError, "well-formed XML"):
                audit_module.audit_upstream(upstream)

    def test_missing_required_source_file_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            upstream = self.fixture(Path(temp))
            (upstream / "app_pojavlauncher/src/main/jni/Application.mk").unlink()
            with self.assertRaisesRegex(audit_module.AuditError, "Application.mk"):
                audit_module.audit_upstream(upstream)


if __name__ == "__main__":
    unittest.main()
