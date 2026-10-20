# VoidCraft development and staged testing

**Goal:** test on a computer first, allow only named owner/support testers into a private online beta, and build a separate public web portal later. A stage must not be advertised as available until its access controls exist and have been tested.

## Current status

- Stage 1 is the current development gate: regression tests, source patching, APK build/signature checks, and an Android 9/API 28 emulator install-and-launch smoke test run in GitHub Actions.
- The Developer & Testing screen in the app is informational. It does not run computer tests from Android, authenticate a tester, or unlock a remote environment.
- Stage 2 is not configured. The current repository is public, so its public pages and public-resource Actions artifacts must never be treated as private tester distribution.
- Stage 3, a dedicated VoidCraft web test portal, is a future project and is not implemented.

## Stage 1 — Computer tests (required before online testing)

Run from a trusted development computer with Python 3, Git, JDK 21, Android SDK command-line tools, Android platform 37, and NDK 27.3.13750724.

1. Review the diff and confirm the pinned Amethyst-Android commit is the one intended.
2. Run Python regression and patcher tests:

       python3 -m py_compile .github/scripts/customize_amethyst.py .github/scripts/apply_logo.py .github/scripts/patch_renderpearl_workaround.py .github/scripts/customize_ui.py .github/scripts/customize_developer.py .github/scripts/test_customizer.py .github/scripts/test_renderpearl_workaround.py .github/scripts/test_ui_customizer.py .github/scripts/test_developer.py
       python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v

3. Run shell syntax validation for the emulator helpers and run the Android build workflow.
4. Verify the APK signature and SHA-256. Keep the checksum paired with the exact APK and transfer them together through a trusted channel.
5. Install the build in the Android 9/API 28 emulator and confirm the launcher opens. Inspect the screenshot and diagnostics for unexpected content.
6. Test manually on the owner's own Android device or Chromebook: install/upgrade, account selector, local profile, version download/repair, launch attempt, renderer selection, controls, logs, backup/restore, and low-memory behavior. Use a throwaway test profile and disposable world.
7. Record tested commit, device/model, Android version, selected Minecraft/loader/renderer, exact steps, expected/actual result, and whether the test is automated, emulator-only, or physical-device tested.

**Stage 1 pass gate:** unit/regression tests, patch idempotence, generated XML validation, source-diff checks, APK build/signature checks, emulator smoke test, and reviewed diagnostics all pass. Any security failure blocks promotion. An emulator startup pass is not the same as a successful Minecraft gameplay test.

Useful local commands after the SDK and pinned upstream checkout are prepared:

       python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
       git -C upstream diff --check

Follow the build instructions in README.md for the exact pinned-source checkout and Gradle command.

## Stage 2 — Private online beta (not active)

Do not enable this stage until the owner selects an access-controlled host. The public repository and public Actions artifacts are not a private beta channel.

### Access model

- Start with an explicit allowlist: the owner and individually approved support-team accounts only. No open self-registration and no public download link.
- Assign separate roles. Owner can approve/revoke access and releases; support testers can download approved builds and submit reports only.
- Keep support testers out of release-signing keys, deployment secrets, production credentials, and real users' private worlds/accounts.
- Use the host's strongest available authentication (prefer MFA/passkeys when supported), short-lived invitations/download links where available, immediate revocation, and access/download audit logs.
- Limit testers to synthetic accounts and disposable worlds. Never require a tester to upload Microsoft tokens, passwords, world saves, or full unredacted logs.
- Distribute only a build from a reviewed commit after Stage 1 passes. Show version, commit, build date, SHA-256, signature information, known issues, and a clear feedback channel.
- Separate test data and test credentials from any future production service. Apply rate limits and monitor unexpected access.
- If a test build is compromised or causes data loss, stop downloads, revoke access, notify testers, and provide recovery/rollback instructions.

### Private-beta release gate

1. Owner reviews and approves the exact commit and test report.
2. A clean CI build passes automated tests and APK signature/checksum verification.
3. The owner publishes the APK to a genuinely private channel configured to require approved accounts.
4. Each tester verifies build identity/checksum, accepts the test scope, and uses only disposable profiles/worlds.
5. Support submits reports through an approved channel; diagnostic attachments are opt-in, previewed, size-limited, and sanitized.
6. The owner reviews reports, fixes issues, repeats Stage 1, and explicitly approves or rejects the next candidate.

### Important public-repository limitation

The GitHub repository is public. GitHub's artifact API documents that public resources can be accessed without authentication. Consequently, uploading an APK as an Actions artifact in this repository cannot provide the owner-and-support-only access you requested. For private beta distribution, use a genuinely private repository or authenticated storage/site with access controls. Do not try to secure a public artifact using a hidden app setting or an unlisted URL.

## Stage 3 — Future VoidCraft web portal

The website should be a separate service from the launcher and is not part of the current implementation. Before accepting testers or uploaded features, plan and test:

- Authentication and verified accounts, secure sessions, MFA/passkeys where available, logout/revocation, and recovery.
- Server-enforced owner/support/public roles and per-resource authorization on every download and administrative operation.
- No public feature upload or executable package publishing until moderation, provenance/signing, scanning, permission review, abuse reporting, and rollback are ready.
- Rate limits, upload size limits, content-type validation, storage isolation, malware scanning, dependency alerts, and audit logs.
- HTTPS, secure cookies, CSRF protection for state-changing browser actions, output escaping, input validation, security headers/content security policy, and secret management.
- Data-minimized privacy notices, retention/deletion rules, and no Minecraft/Microsoft credentials in the web service unless a separately reviewed, officially supported authentication design requires them.
- Separate staging and production environments, protected deployments, reviewed releases, backups, and an incident-response procedure.
- A private beta first, then a deliberately approved public launch. Do not silently convert private testers to public users.

## Feature and tester restrictions

Use [SECURITY_POLICY.md](SECURITY_POLICY.md) as the detailed policy. Among other rules, prohibit credential theft, silent world/log uploads, arbitrary downloaded executable code, shell/root access, security or licensing bypass, unsafe file deletion/overwrite, unverified updates, undisclosed tracking, abusive network behavior, and unreviewed community submissions.

A developer menu is a user-interface convenience, not a security boundary. Actual authorization must be enforced by the private hosting service/backend. The launcher must not claim that online access is protected until the actual host's access controls have been configured and verified.
