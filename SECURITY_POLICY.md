# VoidCraft security policy

**Status: policy and design requirements; not a claim that every control is implemented.**

VoidCraft Launcher currently customizes a pinned Amethyst-Android engine. The Developer & Testing screen is an informational policy page. It does not grant online tester access, execute a security scan, sandbox third-party code, or enable the future Feature Store. Those controls must be implemented and tested before community extensions or private online beta distribution are enabled.

## Security goals

- Preserve the existing launcher engine, native libraries, Java runtimes, renderer, version downloader, loaders, and user data.
- Apply least privilege. A feature must request only the capabilities it needs, and the user must understand each requested capability.
- Keep Microsoft/Xbox credentials, refresh tokens, session tokens, passwords, private keys, and signing credentials out of extension packages, logs, bug reports, and source control.
- Treat downloaded packages, modpacks, logs, crash reports, configuration imports, URLs, and user-uploaded feature manifests as untrusted input.
- Validate paths, sizes, hashes, signatures, data types, and compatibility before acting on files.
- Back up important data before destructive operations; refuse overwrites unless the user explicitly approves them.
- Separate local testing, private online beta testing, and public releases. Passing a build test is not proof of physical-device or gameplay compatibility.

## Restricted and prohibited capabilities

The following are prohibited for built-in features, community features, uploaded extensions, and test tooling:

1. **Credential theft or account impersonation.** No password, Microsoft/Xbox token, session cookie, refresh token, or account-secret harvesting; no impersonating another player or accessing another person's account.
2. **Silent data collection or exfiltration.** No uploading worlds, private logs, screenshots, account data, files, device identifiers, or telemetry without a clear purpose, disclosure, and explicit opt-in. Bug-report attachments must be previewable, size-limited, redacted where possible, and cancellable.
3. **Unrestricted executable code.** Do not execute user-uploaded scripts, shell commands, downloaded DEX/JAR/native libraries, self-modifying modules, remote code, or dynamic code that bypasses review. The future Feature Store should start with declarative manifests and a narrow allowlist of launcher APIs; any later executable extension model needs a separate security review and sandbox design.
4. **Privilege or security bypass.** No root/exploit attempts, Android permission bypass, integrity-check bypass, unauthorized filesystem access, or attempts to disable the operating system's security controls.
5. **Licensing and service-rule bypass.** No bypassing Minecraft ownership checks, Microsoft/Xbox authentication, DRM/licensing, provider access controls, documented API terms, or rate limits.
6. **Unsafe file operations.** No silent deletion or overwrite of worlds, profiles, backups, or configurations; no path traversal, symlink escape, archive bombs, zip-slip extraction, or access outside the user's selected scope.
7. **Untrusted updates and downloads.** No silent replacement of code, unsigned or unverified updates, downgrade to a known-vulnerable package without warning, hidden persistence, or unexplained background network activity.
8. **Undisclosed tracking or advertising.** No hidden analytics, fingerprinting, ad injection, or background tracking. Any future optional diagnostics must be disclosed and consent-based.
9. **Malicious or deceptive store listings.** No impersonating VoidCraft or another publisher, misleading permissions, concealed payloads, review manipulation, malware, exploit tools, or packages whose origin cannot be verified.
10. **Provider-specific direct-load bypasses.** Do not use undocumented endpoints, circumvent provider access controls, or ignore rate limits or terms to fetch content directly. Any future content-provider integration must have a separately reviewed, documented, permitted design.
11. **Destructive or abusive behavior.** No ransomware, data wiping, resource abuse, denial-of-service behavior, unauthorized scanning, or code that damages devices, worlds, servers, or accounts.

## Feature Store package requirements

Before community feature installation is enabled, the store design must include:

- A schema-validated manifest with a unique identifier, publisher, version, description, minimum launcher version, compatible game/loader/runtime range, requested permissions, and update history.
- Verified provenance, cryptographic package signatures, and a strong digest such as SHA-256. A digest alone does not prove who published a package.
- A review and moderation process, vulnerability reporting, abuse reporting, publisher revocation, and a way to disable or roll back a feature.
- Least-privilege capabilities enforced by the host, not merely promises in the manifest.
- Strict file, archive, network, CPU, memory, and storage limits where applicable.
- No access to account tokens, private worlds, or unrestricted filesystem paths by default.
- Explicit approval before a feature changes gameplay settings, deletes data, shares diagnostics, or requests a new sensitive capability.

A UI permission declaration does not provide a sandbox. Until enforceable isolation exists, do not execute arbitrary third-party code inside the launcher process.

## Development and build security

- Run the computer-based regression/security tests before creating a test APK.
- Review source changes and dependency changes. Keep the pinned upstream engine revision deliberate and documented.
- Use least-privilege GitHub Actions token permissions; avoid exposing secrets to untrusted pull-request code; do not print secrets to logs.
- Keep release signing keys and deployment credentials out of source control and out of tester builds.
- Verify the APK signature and SHA-256 before installation or distribution. Check that the hash came from a trusted channel.
- Build test and release artifacts from reviewed commits. Treat debug APKs as testing artifacts, not production releases.
- Inspect test logs and screenshots before sharing. Use synthetic test accounts and worlds, not private user data.
- Keep the public repository's artifacts public-safe. Do not use public repository Actions artifacts as a private beta distribution mechanism.

## Tester roles

- **Owner:** may approve beta participants, approve releases, configure hosting, revoke access, and decide whether a build advances to the next stage.
- **Support tester:** may download approved builds from the authorized test channel, run the test plan, and submit bug reports. Support testers do not need source-control write access, release-signing keys, production credentials, or access to other users' private data.
- **Public tester:** no access to private test builds before an explicitly announced public test. Future public submissions still require moderation and restricted capabilities.

Access must be individually granted, limited to the minimum needed, auditable, and promptly revoked when no longer required. Never rely on a hidden UI toggle as an authorization boundary.

## Incident response

If a suspected malicious extension, credential leak, unsafe build, or destructive defect is found:

1. Stop distributing the affected build or extension and disable its listing where supported.
2. Revoke affected tester links, credentials, signing material, or tokens; rotate exposed secrets.
3. Preserve relevant sanitized logs and hashes without redistributing private data.
4. Assess whether user worlds or configuration files could have been affected and provide a safe recovery path.
5. Publish a clear advisory and a verified fixed build after tests pass.

## Status and references

The security policy is a required target for implementation, not a certification. The current test pipeline builds a debug APK and smoke-tests an Android emulator; that does not prove all runtime behavior, community-extension isolation, real account sign-in, or physical-device/gameplay compatibility.

Relevant platform guidance: [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use) and [GitHub Actions artifact access](https://docs.github.com/en/rest/actions/artifacts).
