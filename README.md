# Royalty – Telegram Chat Hiding

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
![Tested Telegram: 12.8.3](https://img.shields.io/badge/Telegram-12.8.3-26A5E4.svg)

just because https://github.com/Xposed-Modules-Repo/ru.mike.loyalty stopped working for me, so I build it myself.
**Royalty** is an Android Xposed module that hides selected dialogs from Telegram’s main dialog lists and suppresses their new-message notifications.

> It requires [Vector](https://github.com/JingMatrix/Vector) , its requirement or a compatible LSPosed installation.
> The current hook mappings are tested against official Telegram **12.8.3** (`org.telegram.messenger`). Other Telegram versions are not guaranteed to work.

## Requirements

- Android 8.1 or newer
- Vector 2.x or compatible LSPosed
- Official Telegram **12.8.3**, package `org.telegram.messenger`

## Install

1. Install the Royalty release APK.
2. Enable **Royalty** in Vector or LSPosed.
3. Scope it to `org.telegram.messenger`.
4. Restart the device so Vector or LSPosed can activate the module in Telegram.
5. Open Telegram and leave its main process running so it can prepare the bounded catalog snapshot.
6. Open Royalty, tap Refresh, select dialogs, optionally enable notification suppression, and save.

Notification suppression is off by default.

Configuration reloads within one second. Switch Telegram folders or restart Telegram to redraw the list.

## Supported behavior

| Surface | Status |
|---|---|
| Main dialog list and Telegram folders | Supported |
| New-message notifications | Supported |
| Search and global search | Not supported |
| Share/contact picker | Not supported |
| New-group/contact invite | Not supported |

Press and hold Telegram’s main dialog-list ActionBar for three seconds, then release, to toggle temporary reveal mode. Reveal resets when Telegram restarts and does not disable notification suppression.

## Safety model

- Vector/LSPosed supplies the ART hook engine; this project does not patch ART structures.
- `getDialogs(int)` returns a filtered copy. Telegram’s internal list is never mutated.
- Notification filtering replaces only the incoming `processNewMessages` list and preserves countdown handling.
- Configuration uses XSharedPreferences safe-zone redirection.
- Catalog refresh is initiated by the module app through a signature-permission-protected request and returned through an exact-component `PendingIntent`; the module accepts only active 128-bit request nonces.
- Catalog responses contain only account, dialog ID, and a display title, with at most 1,024 entries per account and 256 UTF-16 code units per title.
- Hook failures fail open and appear in the module app as `missing` or `runtime_error`.

See [SECURITY.md](SECURITY.md) for reporting and threat-model details.

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release builds require the four `TCH_*` signing variables used by GitHub Actions. The tag workflow builds twice, compares deterministic APK payload entries, verifies the RSA-PSS-signed APK and Xposed metadata, and publishes checksum manifests.

Dependencies are checksum-pinned through `gradle/verification-metadata.xml`. Xposed API 82 is compile-only and is not bundled into the APK.

## Project layout

```text
app/src/main/java/.../core/      Pure filtering and validation logic
app/src/main/java/.../xposed/    Vector/LSPosed hooks and request bridge
app/src/main/java/.../catalog/   Nonce-validated callback transport and private storage
app/src/main/java/.../config/    Safe preference writer
app/src/test/                    JVM contract tests
```

## Compatibility

The current Telegram hook contract is validated against Telegram source commit `9552e5541e1274b9557c9832b204dbfcaf44b3dc` and Telegram 12.8.3. Telegram updates can rename internal methods; check the hook-status panel after every Telegram upgrade.

## License

GPL-3.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
