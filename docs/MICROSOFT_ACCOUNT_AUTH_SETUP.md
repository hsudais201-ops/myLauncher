# Microsoft account and Minecraft entitlement integration

Flame Launcher must not enable Play merely because Minecraft files are present. A user must sign in with a Microsoft account, complete the Xbox identity exchange, and have a valid Minecraft: Java Edition entitlement.

## Required publisher-owned configuration

Before interactive sign-in can be implemented and tested, the project owner must create or designate a Microsoft Entra application registration for Flame Launcher and supply its **application (client) ID**.

Flame Launcher is a native Android app and therefore a **public client**. A client secret must never be created for, checked into, or embedded in this application. Public-client binaries cannot keep a shared secret confidential. Use Microsoft's supported MSAL Android library rather than hand-crafting OAuth redirects and token storage.

Register the Android application using the final package name:

- Application ID: `com.mflegend.flamelauncher`
- Platform: Android / mobile application
- Client ID: publisher-provided application ID
- Redirect URI: the MSAL Android redirect URI generated for that client ID and the registered package/signing-certificate fingerprint
- Account audience and authority: must support the consumer Microsoft accounts used for Xbox/Minecraft sign-in; confirm the exact tenant/authority configuration against Microsoft's current registration guidance and the Xbox/Minecraft service requirements.

The production signing certificate fingerprint is not yet fixed in this repository. Register debug and release signing fingerprints separately; do not assume the debug fingerprint will be used for release builds.

## Sign-in design

Use MSAL Android's interactive native sign-in with authorization code and PKCE. Microsoft documents authorization code with PKCE for native mobile apps and recommends supported authentication libraries instead of manually crafting the protocol. Device-code flow is principally for input-constrained devices and command-line scenarios, so it is not the preferred phone/tablet sign-in experience.

After MSAL obtains the user token with the required Xbox sign-in scope, the Minecraft identity exchange is a separate step. The expected high-level chain is:

1. Microsoft consumer account sign-in and OAuth token acquisition.
2. Xbox Live user authentication.
3. Xbox Secure Token Service (XSTS) authorization.
4. Minecraft Services `login_with_xbox` token exchange.
5. Minecraft Store entitlement verification.
6. Minecraft Java profile retrieval.

The exact Xbox/Minecraft request bodies, relying-party values, error mapping, and token lifetimes must be validated against current service behavior during implementation. Do not substitute a Microsoft Graph access token for a Minecraft Services token.

## Token and privacy requirements

- Store refresh/credential material only in Android-protected storage; never in SharedPreferences as plaintext.
- Never write access tokens, refresh tokens, XSTS tokens, authorization codes, or device/account secrets to logs, crash reports, analytics, or UI.
- Keep authentication state separate from the player's display name and profile UUID.
- Treat missing, expired, malformed, or unverifiable entitlements as not owned.
- Support sign-out and revoke the local session; clear cached entitlement/profile state at sign-out.
- Do not ship service credentials or use another launcher's client ID.
- Do not attempt to bypass ownership, regional restrictions, or publisher access controls.

## Flame Launcher launch gate

`RuntimeLaunchReadiness` intentionally defaults to blocked. Its `microsoftAccountAuthenticated` and `minecraftEntitlementVerified` flags must be set only from a completed, successful account flow and a fresh successful entitlement response. They must not be set from a cached username, a local version folder, a downloaded client JAR, or an unverified API response.

The remaining launch requirements still include a compatible Android JVM, resolved Android native libraries, graphics surface, input/audio integration, lifecycle handling, and a successful physical-device Minecraft session.

## Official references

- Microsoft MSAL authentication-flow guidance: https://learn.microsoft.com/en-us/entra/identity-platform/msal-authentication-flows
- Microsoft public vs confidential client applications: https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-applications
- Microsoft mobile app configuration: https://learn.microsoft.com/en-us/entra/identity-platform/scenario-mobile-app-configuration
- Microsoft Android MSAL sign-in quickstart: https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-mobile-app-sign-in
- Xbox service authentication background: https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-xbox-live-authentication?view=gdk-2604
- Minecraft community-maintained protocol notes (not a Microsoft API contract): https://minecraft.wiki/w/Microsoft_authentication

## Current status

No client ID or publisher-controlled registration has been supplied for this repository. Microsoft sign-in and Minecraft entitlement verification are **not implemented**. The Play gate stays closed until the publisher configuration, API access, full token exchange, entitlement check, token handling, and device tests are complete.
