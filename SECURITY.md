# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 3.0.x | Yes |
| 2.x and older | No — upgrade to the latest release |

Version 1.x is additionally unsafe because its custom ART entry-point hook was not ABI-safe.

## Reporting

Use GitHub’s **Security → Report a vulnerability** workflow. Do not publish hidden-chat identifiers, Telegram data, device logs containing personal information, or proof-of-concept exploits in a public issue.

Include the module version, Vector/LSPosed version, Android version, Telegram version, hook-status panel, reproduction steps, and sanitized logs.

## Security boundaries

- Hook failures fail open so Telegram remains usable.
- Configuration is stored in the hook framework's Modern Xposed remote-preferences database. The app writes through the API-101 service, while Telegram receives a read-only `SharedPreferences` view; no world-readable app file is requested.
- Catalog requests use an exported runtime receiver inside Telegram guarded by the module app's signature-level request permission. This authenticates the sender independently of Android package visibility; malformed requests without a callback are rejected.
- The callback targets a non-exported module receiver. Each result must carry an active 128-bit nonce that expires after 15 seconds and is invalidated on completion.
- Build dependencies are verified by SHA-256 for every resolved artifact. Gradle PGP signature verification remains disabled because the Android/Xposed repositories do not consistently publish signatures; adding partial signature trust would not strengthen the complete checksum allowlist.
- The module does not provide secrecy against root, framework compromise, malicious Telegram builds, or physical compromise of an unlocked device.
