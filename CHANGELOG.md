# Changelog

All notable changes are documented here. The project follows [Keep a Changelog](https://keepachangelog.com/) and semantic versioning.

## [Unreleased]

## [2.2.0] - 2026-09-24

### Added

- Search filtering for recent, local, global, local-server, phone, group, message, forum, sponsored-peer, and public-post result surfaces.
- Share-sheet filtering for the main dialog grid, recent targets, and local/global search results.
- New-group, add-member, contact-invite, and contact-search filtering with account-aware user and chat keys.
- An APK-bound Telegram 12.10.4 compatibility map and install-time reflection probe for every supported runtime surface.

### Changed

- Search rows are hidden through adapter position remapping, preserving Telegram-owned collections and aligned metadata.
- Share, group, and contact adapters receive replacement copies instead of in-place list mutations.
- Reveal mode restores captured adapter data; re-concealing drops stale hidden share selections.
- Manual signed acceptance builds are restricted to the protected `main` branch.

### Security

- Reveal gesture state now detaches with its ActionBar view, preventing temporary view-tree retention.
- Removed obsolete AIDL ProGuard rules left from the pre-2.0 catalog bridge.

### Verification

- Repository contracts, JVM tests, Android lint, debug APK assembly, exact Telegram 12.10.4 DEX surface checks, and the signed release workflow are required to pass.
- The two-account physical-device matrix was not executed before publication because neither configured ADB host had a connected device; this limitation is recorded in `docs/device-acceptance-2.2.0.md`.

## [2.1.1] - 2026-09-24

### Fixed

- The three-second reveal gesture now fires when its hold deadline is reached instead of requiring a later `ACTION_UP`, and resolves Telegram 12.10.4’s owning dialog fragment directly from the touched ActionBar.

### Changed

- Pull-request CI now retains the debug APK for three days, and manual workflow runs produce a verified release-signed acceptance APK that can update an installed Royalty build without publishing a release.

## 2.1.0 - 2026-09-24

### Changed

- Renamed the Android application ID and Java namespace to `io.github.cepeter.royalty`. Android treats this as a new app, so legacy selections do not migrate automatically.
- Repository contracts now run through pinned `uv` before Java and Android setup, providing faster failure feedback in CI.
- Dependabot version updates are disabled; dependency updates are managed manually.

## 2.0.0 - 2026-09-24

### Added

- Android Xposed module APK compatible with JingMatrix Vector and legacy-compatible LSPosed.
- Account-aware dialog keys and bounded catalog collection.
- Package-visibility-safe catalog refresh using a module request and nonce-validated `PendingIntent` callback.
- Configuration app with hook status, dialog selection, and notification controls.
- Three-second press-and-hold reveal on Telegram’s main ActionBar.
- Unit and packaging/security contract tests.
- Gradle dependency verification and complete security/licensing documents.
- SHA-pinned CI, secret-backed release signing, APK inspection, payload reproducibility checks, and Dependabot configuration.
- Sanitized Vector device-acceptance record.

### Changed

- Main dialog filtering now returns a copy instead of mutating Telegram’s internal list.
- Notification suppression hooks `NotificationsController.processNewMessages` while preserving empty-list countdown behavior.
- Configuration moved from a root JSON file to Vector/LSPosed XSharedPreferences safe-zone storage.
- Supported surfaces are now documented accurately: dialog lists and new-message notifications only.
- Notification suppression is opt-in; catalog responses follow the documented 1,024-entry and 256-code-unit bounds.
- The configuration app is labeled `Royalty`.
- Royalty now uses a responsive card-based dashboard with light/dark palettes, modern dialog rows, accessible controls, and system-bar insets.
- The reveal gesture uses a three-second ActionBar press-and-hold through `dispatchTouchEvent`, so story-header children cannot consume it before the hook.
- The reveal gesture supports Telegram 12.8.3’s APK-verified obfuscated `ActionBar` and `DialogsActivity` aliases and verifies the active fragment through `ActionBarLayout`.
- Telegram hook classes are resolved only after `ApplicationLoader.onCreate`, preventing early static initialization from crashing Telegram under Vector.
- Catalog requests now rely on the signature-level sender permission instead of package-visibility-sensitive `PendingIntent` identity metadata.

### Removed

- Custom ART entry-point offsets and Quick-ABI callbacks.
- APatch/MeowZygisk packaging, native C library, root WebUI, and Unix catalog socket.
- Exported Binder/AIDL catalog service that depended on Telegram being able to discover the module package.
- Unsupported search, share-picker, and new-group claims.

### Security

- Version 1.x is unsupported because its native hook engine was not ABI-safe and its module lifecycle prevented reliable activation.

## 1.3.1 — 2026-09-23

Legacy APatch release. Superseded by the version 2 architecture and no longer supported.

[Unreleased]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/compare/v2.2.0...HEAD
[2.2.0]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v2.2.0
[2.1.1]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v2.1.1
