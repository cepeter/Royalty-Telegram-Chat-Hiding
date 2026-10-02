# Changelog

All notable changes are documented here. The project follows [Keep a Changelog](https://keepachangelog.com/) and semantic versioning.

## [Unreleased]

## [3.1.9] - 2026-10-02

### Added

- Add a bounded in-memory diagnostic event log for refreshes, saved settings, and hook-status transitions, with a text export from the Status screen.
- Exclude chat titles, IDs, messages, and raw exceptions from the unencrypted diagnostic report; require settings access when returning from the document picker.
- Show a green bullet for each healthy diagnostic surface and a red bullet for every non-working state while retaining the textual state label.

### Changed

- Advance Android release metadata to version 3.1.9 (code 26).

## [3.1.8] - 2026-10-02

### Changed

- Resolve the search adapter's post-refresh redraw target through the semantic resolver (`search.async.reload`) instead of a hardcoded obfuscated call: the tested 12.10.4 alias is verified by the pinned compatibility probe, and builds where the target is ambiguous or missing skip only the optional redraw while keeping concealment filtering.
- Make pending catalog nonces boot-aware: entries persisted before a reboot are dropped because their `elapsedRealtime`-based expiries restart at zero across boots.

### Removed

- Remove the unused, exported `AuthenticationActivity` and its `AuthenticationProtocol`/`AuthenticationRoute` helpers. Device credential authentication protects Royalty settings only, and the exported component let any app present a misleading credential prompt.

### Fixed

- Align SECURITY.md with the implemented version guard (tested 12.10.4 profile or builds fully validated by semantic resolution), remove the dead unsupported-version reporter it described, and document the plaintext local catalog cache.
- Keep persisted catalog nonces rejected when a boot-marker cleanup cannot be committed.
- Preserve the optional search-redraw diagnostic after hook installation completes.
- Correct the README device-lock bullet to settings-only authentication.
- Advance Android release metadata to version 3.1.8 (code 25).

## [3.1.7] - 2026-09-30

### Fixed

- Allow signed releases to continue their monotonic version-code chain from retained metadata after latest-only retention prunes the historical v3.0.5 bootstrap release.
- Advance Android release metadata to version 3.1.7 (code 24) for the signed release.

## [3.1.6] - 2026-09-30

### Added

- Add DexKit-backed semantic discovery for obfuscated Telegram hook targets, with stable string fingerprints and a versioned persistent descriptor cache.

### Changed

- Keep the verified Telegram 12.10.4 aliases as the fast path while allowing other builds only when semantic validation resolves every required surface safely.
- Preserve the Modern Xposed API 101 `ModernHookBridge`, protective interception, atomic rollback, tracked cleanup, and per-surface isolation around the new resolver.
- Show Telegram as waiting or last-verified/inactive when its process is not running instead of reporting healthy hooks as failed.

### Fixed

- Keep known hook failures degraded while preventing unopened or stopped Telegram from being misclassified as a hook failure.
- Advance Android release metadata to version 3.1.6 (code 23) for the signed release.

## [3.1.5] - 2026-09-30

### Changed

- Make the Telegram header reveal/conceal gesture immediate and local: device credential authentication now protects opening Royalty settings only.
- Replace the single long dashboard with four native tabs: Chats, Privacy, Status, and Backup, while preserving the selected tab across activity recreation.
- Clarify settings copy so automatic concealment remains independent from the Royalty settings lock.
- Advance Android release metadata to version 3.1.5 (code 22) for the signed release.

## [3.1.4] - 2026-09-29

### Fixed

- Keep optional Telegram adapter redraw failures from degrading reveal health when reveal itself remains functional.

### Changed

- Redesign the Royalty settings dashboard with clearer Connection, Privacy controls, Backup & recovery, and Hidden chats sections.
- Collapse technical diagnostics by default, group reveal-protection settings, and consolidate hidden-chat editing with side-by-side Save and Discard actions.
- Advance Android release metadata to version 3.1.4 (code 21) for the signed release.

## [3.1.3] - 2026-09-29

### Fixed

- Keep Telegram 12.10.4 search, share, and contact hook installation independent from optional adapter-refresh resolution, preventing refresh lookup failures from degrading concealment surfaces.
- Prefer the stable adapter refresh API when available, retain the structural fallback, and resolve position-sensitive search methods through superclasses.
- Advance Android release metadata to version 3.1.3 (code 20) for the signed release.

## [3.1.2] - 2026-09-29

### Fixed

- Resolve Telegram 12.10.4 search, share, and contact hook methods through superclasses while preserving fail-open behavior on unsupported surfaces.
- Advance Android release metadata to version 3.1.2 (code 19) so signed APK inspection matches the immutable release tag.

## [3.1.0] - 2026-09-29

### Added

- Stable owner-bound selections with migration review, account filters and labels, actionable diagnostics, and draft selection controls.
- Optional background, screen-off, and timed re-concealment with device credential protection for settings and reveal.
- On-device encrypted configuration export and import with owner-aware preview and explicit Save.
- A dismissible in-app update card backed by a bounded daily check of the official GitHub release.

### Changed

- Made catalog refreshes preserve unsaved drafts and publish only complete, nonce-matched snapshots.
- Made hook installation and reflective field updates transactional, with rollback on partial failure.
- Hardened signed-release inspection and publication while retaining previous releases for rollback.
- Centralized release metadata at version 3.1.0 (code 18) and clarified the required Telegram `versionCode 70992`.

### Fixed

- Preserved visible row order and concealed-row anchors across Telegram adapter insertions, removals, and reorders.
- Kept visible share recipients selected through search while filtering concealed recipients.

### Release acceptance

- Repository, JVM, Android lint, and debug APK gates passed. Physical-device acceptance remains pending and is documented in `docs/device-acceptance-3.1.0.md`.

## [3.0.5] - 2026-09-26

### Added

- Added an opt-in Local Premium control that overrides Telegram's client-side Premium check while leaving server-side entitlements unchanged.

### Changed

- Added install and runtime status reporting for the Local Premium hook.
- Added GPL-3.0 attribution for the TeleVip-LSPosed hook source.

## [3.0.4] - 2026-09-26

### Fixed

- Reworked adapter baseline reconciliation to use linear-time identity counts, avoiding quadratic scans and preserving distinct value-equal Telegram rows.
- Added Telegram 12.10.4 share-map `b()` and `k(Object,long)` methods to the compatibility probe and mapping document.

## [3.0.3] - 2026-09-26

### Added

- Added an accessible one-tap × action that clears the Hidden Chats search and restores the full catalog without changing selections.

### Changed

- Removed completed and obsolete implementation plans from the development repository.

## [3.0.2] - 2026-09-25

### Fixed

- Added an exact Telegram version guard so unsupported builds report `unsupported_version` instead of silently degrading into missing hooks.
- Tightened search/share row classification to verified fully-qualified runtime types and exact mapped fields.
- Preserved Telegram share-map identity by filtering its verified map instance in place instead of reconstructing it through private reflection.
- Retained full adapter baselines across in-place Telegram list updates so reveal mode does not permanently lose hidden rows.

### Security

- Moved release signing and all signing secrets into the approval-gated `production` environment job.
- Documented the private-field replacement required by Telegram 12.10.4 contact/share adapters.

## [3.0.1] - 2026-09-25

### Fixed

- Made the dashboard vertically scrollable and expanded the hidden-chat picker to a stable 360dp height.
- Added chat filtering by title or dialog ID while preserving hidden selections across filter changes.
- Removed the redundant account label from chat rows.
- Replaced the multiline hook dump with compact Framework and Telegram connection rows using accessible working/not-working text and green/red indicators.

## [3.0.0] - 2026-09-25

### Changed

- Migrated the module entrypoint, package lifecycle, hooks, logging, reflection bridge, and metadata to Modern Xposed API 101.
- Replaced world-readable/XSharedPreferences configuration with API-101 framework remote preferences.
- Upgraded the build to compile SDK 36, Android Gradle Plugin 8.10.1, and Gradle 8.11.1 as required by the API-101 service client.

### Security

- Removed legacy Xposed manifest metadata, `assets/xposed_init`, and all `de.robv.android.xposed` APIs.
- Release verification now requires exact `META-INF/xposed` resources and rejects bundled Modern Xposed API classes.

## [2.2.1] - 2026-09-24

### Fixed

- Updated the dashboard scope text and Xposed-manager description to include search, share targets, group/member flows, and contact pickers added in 2.2.0.

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

[Unreleased]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/compare/v3.1.8...HEAD
[3.1.8]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.8
[3.1.7]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.7
[3.1.6]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.6
[3.1.5]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.5
[3.1.4]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.4
[3.1.3]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.3
[3.1.2]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.2
[3.1.0]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.0
[3.0.5]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.5
[3.0.4]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.4
[3.0.3]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.3
[3.0.2]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.2
[3.0.1]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.1
[3.0.0]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.0.0
[2.2.1]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v2.2.1
[2.2.0]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v2.2.0
[2.1.1]: https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v2.1.1
