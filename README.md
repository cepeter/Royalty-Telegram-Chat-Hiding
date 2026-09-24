<div align="center">

# Royalty

**Hide selected Telegram chats without changing or deleting them.**

[![Latest release](https://img.shields.io/github/v/release/cepeter/Royalty-Telegram-Chat-Hiding?display_name=tag&style=flat-square)](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/latest)
![Android 8.1+](https://img.shields.io/badge/Android-8.1%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Telegram 12.10.4](https://img.shields.io/badge/Telegram-12.10.4-26A5E4?style=flat-square&logo=telegram&logoColor=white)
[![GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)

An Xposed module for the official Telegram Android app.

[Download](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/latest) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md)

</div>

Royalty started as a personal replacement for [Loyalty](https://github.com/Xposed-Modules-Repo/ru.mike.loyalty) after it stopped working on my setup. It keeps chosen chats out of Telegram’s dialog lists, search results, share targets, and people pickers, with optional notification suppression.

> [!IMPORTANT]
> Royalty is tested with official Telegram **12.10.4** (`org.telegram.messenger`). Telegram changes internal classes often, so other versions may not work.

## What it does

- Hides selected chats from the main dialog list and folders.
- Removes them from local/global search, recent results, messages, forums, and public-post results.
- Removes them from share targets, contact pickers, new-group, add-member, and invite flows.
- Can suppress new-message notifications for hidden chats.
- Lets you reveal hidden chats temporarily with a three-second press and hold.
- Shows per-surface hook health in the app, so failures are visible instead of silent.

Royalty does not delete chats, modify messages, or change Telegram’s stored dialog list.

## Before you install

| Requirement | Supported version |
|---|---|
| Android | 8.1 or newer |
| Hook framework | Vector/LSPosed with Modern Xposed API 101 support |
| Telegram | Official app, version 12.10.4 |
| Telegram package | `org.telegram.messenger` |

> [!WARNING]
> Version 2.1.0 uses the new package name `io.github.cepeter.royalty`. It installs separately from older builds using `io.github.cepeter.telegramhider`, and saved selections cannot migrate automatically. Disable and uninstall the old package first to avoid loading two copies of the hook.

## Install

1. Download the latest APK from [GitHub Releases](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/latest).
2. Install the APK, then enable **Royalty** in Vector or LSPosed.
3. Scope the module to `org.telegram.messenger`.
4. Restart your device so the hook framework can activate Royalty inside Telegram.
5. Open Telegram and leave it running for a few seconds.
6. Open Royalty, tap **Refresh**, choose the chats to hide, and tap **Save**.

Notification suppression is optional and stays off until you enable it.

## Everyday use

Configuration changes are synchronized through the framework's Modern Xposed remote-preferences service. Use the search field to filter the chat picker by title or dialog ID. Switch Telegram folders or restart Telegram if the visible list has not redrawn yet.

To reveal hidden chats temporarily:

1. Open Telegram’s main chat list.
2. Press and hold the top ActionBar for three seconds.
3. Release when the reveal message appears.

Repeat the gesture to conceal them again. Reveal mode resets when Telegram restarts and does not turn notification suppression off.

## Support matrix

| Telegram surface | Status |
|---|:---:|
| Main dialog list | ✅ Supported |
| Chat folders | ✅ Supported |
| New-message notifications | ✅ Supported |
| Search and global search | ✅ Telegram 12.10.4 |
| Share and contact pickers | ✅ Telegram 12.10.4 |
| New group, add member, and contact invite | ✅ Telegram 12.10.4 |

The 2.2.0 surfaces above passed repository, JVM, lint, APK-build, and exact-DEX compatibility checks. The two-account physical-device matrix could not run before publication because neither configured ADB host had a connected device; see [`docs/device-acceptance-2.2.0.md`](docs/device-acceptance-2.2.0.md).

## If something is not working

1. Confirm that Royalty is enabled and scoped only to `org.telegram.messenger`.
2. Confirm that Telegram is version **12.10.4**.
3. Restart the device after enabling or updating the module.
4. Open Telegram before tapping **Refresh** in Royalty.
5. Check the hook-status cards for `missing` or `runtime_error`.

Hook failures fail open: Telegram keeps showing its normal, unfiltered content instead of crashing or hiding the wrong chats.

<details>
<summary><strong>Safety and privacy details</strong></summary>

- Vector or LSPosed supplies the ART hook engine; Royalty does not patch ART structures itself.
- Dialog and notification filtering use copied inputs rather than mutating Telegram-owned collections.
- Search surfaces remap visible adapter positions while preserving aligned names and metadata.
- Share, group, and contact adapters receive replacement copies with fail-open handling for unknown Telegram rows.
- Configuration uses Modern Xposed remote preferences; it never requests world-readable app files.
- Catalog requests require a signature-level permission and return through an exact-component `PendingIntent`.
- Every request uses an active 128-bit nonce.
- Catalog responses contain only the account, dialog ID, and display title, capped at 1,024 entries per account and 256 UTF-16 code units per title.

See [SECURITY.md](SECURITY.md) for the threat model and reporting process.

</details>

<details>
<summary><strong>Build from source</strong></summary>

You need JDK 17 and Android SDK 36.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release builds use the four `TCH_*` signing variables configured in GitHub Actions. The tag workflow builds the APK twice, compares deterministic payload entries, verifies the RSA-PSS signature and Xposed metadata, and publishes checksum manifests.

Dependencies are checksum-pinned in `gradle/verification-metadata.xml`. Modern Xposed API 101 is compile-only and is not bundled in the APK; its API-101 service client is packaged for framework-backed remote preferences.

### Project layout

```text
app/src/main/java/.../core/      Filtering and validation logic
app/src/main/java/.../xposed/    Vector/LSPosed hooks and request bridge
app/src/main/java/.../catalog/   Nonce-validated callback transport and private storage
app/src/main/java/.../config/    Safe preference writer
app/src/test/                    JVM contract tests
```

</details>

## Compatibility notes

The runtime aliases are checked directly against the official Telegram **12.10.4** APK (`versionCode 70992`, SHA-256 `146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d`). Telegram source commit [`9552e554`](https://github.com/DrKLO/Telegram/commit/9552e5541e1274b9557c9832b204dbfcaf44b3dc) is used only as a readable reference. Check Royalty’s hook-status panel after every Telegram update.

## License

Royalty is licensed under [GPL-3.0](LICENSE). Third-party notices are listed in [NOTICE.md](NOTICE.md).
