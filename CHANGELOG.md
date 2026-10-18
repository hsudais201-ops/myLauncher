# Changelog

Not every feature listed in project planning documents is implemented. Entries describe repository changes and the evidence available for them.

## 2026-10-18 — Master mobile launcher formation

- Added MASTER_MOBILE_LAUNCHER_SPEC.md to consolidate the requested mobile-first feature scope, security/platform constraints, testing expectations, definition of done and phased delivery roadmap.
- Added FEATURE_AUDIT.md to separate source-evidenced implementation from partial, missing and unverified functionality.
- Added IMPLEMENTATION_PLAN.md with a prioritized audit, reliability, controls, modding, content and release-readiness sequence.
- Added ARCHITECTURE.md documenting the existing Amethyst-Android customization/build approach and CI boundaries.
- Added TESTING.md with current regression/build/smoke-test commands and a physical-device acceptance matrix.
- Linked these documents from the project entry point and existing feature roadmap.
- Refined the SDK/ABI audit against the exact pinned upstream Gradle, AndroidManifest.xml and Application.mk sources: compile SDK 37, target SDK 34, minimum SDK 21 upstream, and native NDK ABI declarations for ARM32, ARM64, x86 and x86_64. Full runtime/gameplay support on each ABI remains unverified.
- No runtime/product code change is claimed by this documentation update. No test or APK build result is claimed for this documentation-only change; run the workflow as part of review if a fresh full validation is required.

## 2026-10-18 — Credential exposure findings and mitigations in review

- Source inspection found account JSON contains Microsoft access/refresh-token fields and the pinned Microsoft authentication class logs authorization/form payloads, OAuth/Xbox/XSTS/Minecraft token values, and profile identifiers.
- PR #61 proposes excluding app-private `accounts/` JSON from Android legacy/cloud/device-transfer backups. Its full CI build, APK checks and API 28 emulator launcher-start test passed, but the PR is not merged.
- PR #62 proposes removing credential/profile values from authentication Logcat messages with a guarded patch and regression tests. Its regression stage passed; the complete build/emulator workflow was still running at the last check.
- Secure at-rest credential encryption is still not implemented or verified. A compatible Android Keystore migration remains a high-priority follow-up; backup exclusion/log redaction are not a substitute for encryption.
