# Royalty Waves 1–4 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: use superpowers:subagent-driven-development to implement this plan with focused task reviews. The user has approved implementation, PR creation, and merging eligible PRs.

**Goal:** Complete audit Waves 1–4 and all seven approved features in four reviewed PRs.

**Architecture:** Native Java Android UI, pure Java state/policy models, bounded signature-protected IPC, transactional Xposed hooks, and on-device encrypted backups. Each wave builds on the preceding reviewed wave; task 4–6 share one feature PR.

**Tech Stack:** Java 17, Android API 27–36, Gradle 8.11.1, Android Gradle Plugin 8.10.1, libxposed API 101.0.1/service 101.0.0, JUnit 4.13.2, Python unittest, platform cryptography.

**Spec:** `docs/superpowers/specs/2026-09-29-royalty-stabilization-features.md`.

## Global Constraints

- Keep support restricted to `org.telegram.messenger` version `12.10.4`, code `70992`, main process only. Do not add speculative hook aliases or version profiles.
- Preserve Java 17, minSdk 27, Xposed API 101, signature-protected catalog requests, explicit non-exported catalog callbacks, nonce validation, bounded input, dependency verification, and existing optional Premium behavior.
- New background concealment, screen-lock concealment, reveal timeout, and authentication default to disabled. Notification suppression remains independent of reveal.
- Runtime concealment must use the row's owning account. Persisted selections are bound to a stable owner identity by Wave 4. A missing refresh is not a logout.
- Never transmit Telegram messages, change signing keys, create release tags, or publish APKs. User approval covers isolated changes, PRs, and merges after review/CI.
- Never claim JVM/static checks establish physical-device behavior. Record live-device acceptance as pending where it was not performed.
- Keep Android adapters thin and test real behavior in pure Java where practical; do not add tests that merely search implementation text as a substitute for behavioral regression tests.

## Review Focus

Draft survival across lifecycle and service events; current-nonce atomic publication; truthful health; transactional hook and field rollback; identity ordering and complete recipient preservation; fail-closed release inspection; stable owner migration; expiring one-use authentication; opt-in lifecycle policies; authenticated bounded backup parsing. Review changed interfaces against consumers without unrequested restructuring.

## Validation commands

Use focused JUnit tests while iterating. A workspace-local runner is available at `/workspace/scratch/41b1a4e4929e/royalty-toolchain/run-jvm-tests.sh`; pass this checkout's absolute path when the runner supports it, and verify the source path rather than accidentally testing the original clone. It may be extended under the toolchain directory for new pure-Java tests. Use the real Android API 36 jar. Record exact commands and output.

At task completion run `python3 -m unittest discover -s tests` once and the changed behavior's JUnit tests; run all applicable JVM tests once before the task commit. Full Android gate is `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` in GitHub CI. A local javac check is additional evidence, not Android resource/APK verification. Do not repeatedly attempt unavailable Gradle downloads.

### Task 1: Wave 1 — durable draft, atomic refresh, and truthful health

**Files:** Modify `MainActivity.java`, `core/HiddenConfig.java` if value equality is needed, catalog protocol/repository/client/result receiver/pending store, `xposed/CatalogRequestBridge.java`, and `xposed/CatalogSnapshotStore.java`. Create focused `core/ConfigurationDraft.java`, `catalog/CatalogBatch.java`, `catalog/CatalogUpdates.java`, and `core/ProtectionStatus.java` (or equivalently focused names). Add behavioral JUnit tests in matching packages. Update existing contract tests only when the contract intentionally changes; retain their coverage. All Java paths are below `app/src/main/java/io/github/cepeter/royalty/` and tests below `app/src/test/java/io/github/cepeter/royalty/`.

**Interfaces:** ConfigurationDraft owns saved baseline, current edit, initialized/dirty state, Save/Discard and serializable restoration; later tasks extend it with accounts and policies. The catalog request exposes its nonce and monotonic expiry. CatalogBatch stages one nonce's begin/accounts/status/complete and produces a snapshot only when complete and current. CatalogUpdates emits in-process completion events containing nonce and success/failure. Repository commits a whole validated snapshot in one checked operation. Snapshot metadata includes process session and observation time and can be extended with owners in Task 4. ProtectionStatus aggregates the eight required surfaces separately from optional Premium.

**Binding behavior:** Keep 15,000ms nonce lifetime, slots 0–15, 1,024 entries/account, title length 256, and max 16 status entries. Guard editing before initial saved configuration loads. Preserve dirty edits across refresh, status events, service reconnect, resume, and saved-instance recreation. Save updates baseline only after successful persistence; Discard resets it explicitly. A new request supersedes old requests; only matching completion cancels its watchdog. Remove the dynamic ACTION_UPDATED receiver and package-specific completion broadcast; do not relax cross-process receiver protection. Last-good cache survives malformed/expired/partial batches. Required health keys: bridge, compatibility, dialogs, search, contacts, share, notifications, reveal. Missing/stale/unknown-row results cannot be green; Premium is independent.

- [ ] Read the relevant source and the TDD skill plus `writing-good-tests.md`; write failing behavior tests before production changes.
- [ ] RED tests: edit then baseline/status refresh preserves edit; initial unavailable settings cannot save; failed save retains edit; discard restores baseline; restored dirty draft remains dirty.
- [ ] RED tests: request A followed by B then completion A leaves B active; expiration and replay rejected; incomplete/duplicate/oversized batch cannot publish; a valid empty snapshot can publish; persistence failure reports failure; all required healthy vs partial/stale/degraded/unsupported health states.
- [ ] Implement pure state and IPC staging first, then wire MainActivity with lifecycle-safe observer/watchdog and distinct settings/catalog rendering. Avoid growing MainActivity with new parsing/business logic.
- [ ] Validate debug/release callback routing in source and tests of the platform-independent request/completion state. Check the existing Android permission declarations remain intact.
- [ ] Run focused tests during edits, complete relevant JVM/Python checks once, and compile changed app sources with the prepared Android toolchain. Record pending full CI/device gates.
- [ ] Self-review, commit as `fix: preserve drafts and publish catalog refreshes atomically`, and write the task report with RED/GREEN evidence, files, exact test output, and limitations. Do not create a PR yourself; the controller handles GitHub.

### Task 2: Wave 2 — atomic hooks, identity order, and safe share selection

**Files:** Modify `xposed/ModernHookBridge.java`, `TelegramHook.java`, `TelegramSearchHook.java`, `TelegramContactHook.java`, `TelegramShareHook.java`, `FilteredListState.java`, and matching tests. Create narrowly focused hook/field transaction and selected-recipient reconciliation helpers as needed. Inspect real libxposed HookHandle API before using its removal method. Do not modify task 1 state/IPC beyond an integration need documented in the report.

**Interfaces:** Existing hook registration returns real handles; an installation transaction owns and reverses registrations when its surface fails. Field transaction captures original values and rolls back partial writes. FilteredListState keeps raw concealed objects and the latest visible identity ordering. Selection reconciliation consumes full selected values, current owner, configuration/revision, and reveal state, and advances its success marker only after successful mutation.

**Binding behavior:** Exact Telegram 12.10.4/70992 only. Resolve all surface prerequisites or unhook every partial registration. Reconcile front/middle insertions and arbitrary visible reorders without moving them to the end; preserve hidden objects for reveal. For ambiguous hidden positioning, anchor each hidden occurrence before its next surviving old-visible occurrence, preserving new visible order; append orphaned hidden occurrences in old order. Preserve duplicate object identities by occurrence counts. Paired list size mismatch returns the existing conservative unchanged copies. Share cleanup removes only newly hidden recipients, retains visible recipients selected via search, and retries after failure. It runs for configuration changes even while reveal remains false. Inspect verified aliases or unique validated structural collection descriptors; do not guess an obfuscated traversal name.

- [ ] Write failing JUnit tests for partial hook registration failure/unhook rollback and paired-field second-write failure/restore.
- [ ] Write failing tests for front insertion, middle insertion, reorder, removal, replacement, duplicate identities, null items, repeated capture/apply, and reveal restoration. Verify the current audit repro fails before fixing.
- [ ] Write failing tests for selected visible search-only rows surviving cleanup; newly hidden selection removed without reveal transition; unrelated account retained; failure leaves original selection and does not advance marker; successful retry works.
- [ ] Implement transactional surface installation and field writes, order-preserving reconciliation, and full selected-map filtering; surface failures through Task 1 diagnostics without corrupting Telegram state.
- [ ] Run focused tests, full relevant JVM/Python checks once, and whole-source javac if available; document that real obfuscated adapters require device testing.
- [ ] Self-review, commit as `fix: make runtime filtering and hook installation consistent`, and report RED/GREEN evidence and any verified reflection assumptions. Controller opens and merges Wave 2 PR after review/CI.

### Task 3: Wave 3 — release validation and rollback preservation

**Files:** Modify `.github/workflows/build.yml`, `scripts/verify-release-apk.sh`, relevant release scripts and Python tests, `app/build.gradle.kts`, and release documentation. Add a single small version metadata file consumed by both build and verifier, and a checked-in expected production certificate fingerprint obtained from the published v3.0.5 APK. Keep version 3.0.5/code17 until Task 6's feature-version bump.

**Interfaces:** One source yields versionName/versionCode. Tag publication checks exact `v<versionName>`, inspected APK metadata, and a monotonic code above the previous published code. The verifier explicitly validates exit status of each prerequisite, expected signer identity, and absence of forbidden bundled framework classes before emitting manifests. CI serializes publication and retains prior releases and tags.

**Binding behavior:** Preserve existing signing key and secrets, SHA-pinned actions, dependency verification, and protected production environment. A failed apkanalyzer (including exit 7 with no output) must fail and must not write a success checksum. A v99.0.0 tag with a 3.0.5 APK must fail. Invalid/multiple unexpected signer identities fail. Remove blanket cleanup of older or unrelated releases/tags. Manual non-tag runs build artifacts without publishing. No live release, tag, key, or repository secret mutations during this task.

- [ ] Read release scripts/workflow and write executable regression tests using temporary mock analyzer/signature commands for failure, malformed metadata, forbidden classes, tag mismatch, signer mismatch, monotonic code, and valid success.
- [ ] Demonstrate RED on current analyzer-failure and tag-mismatch repros; implement checked capture/validation and one version source.
- [ ] Obtain the prior release certificate by verifying the actual public v3.0.5 APK with apksigner; record URL, APK checksum, and resulting certificate evidence in documentation. Never substitute Telegram's certificate or an invented fingerprint.
- [ ] Update CI publication conditions/concurrency and remove destructive release cleanup. Update contractual tests to assert retention, ordering of checks, and manual-build behavior meaningfully.
- [ ] Run shell syntax checks, executable release regression tests, Python suite, relevant JVM/build-metadata checks once. Do not run production signing or publish a release.
- [ ] Self-review, commit as `fix: fail closed during release verification and preserve rollback releases`, and write report with exact evidence and remaining CI checks.

### Task 4: Wave 4A — stable account ownership and selection/diagnostic UI

**Files:** Extend `core/HiddenConfig.java`, `core/ConfigurationDraft.java`, `config/ConfigStore.java`, `xposed/XposedConfigRepository.java`, `xposed/TelegramHook.java`, `xposed/CatalogSnapshotStore.java`, `xposed/CatalogRequestBridge.java`, catalog models/repository/protocol, and `MainActivity.java`. Add focused account identity/inventory and UI helpers, with behavioral tests. Keep scope to features 2, 3, 5, 7 and owner-binding audit fix.

**Interfaces:** Preserve DialogKey(slot,dialogId) for runtime rows; add persisted slot-to-stable-owner binding. Resolve owner using account-specific UserConfig at runtime. Complete catalog inventory carries active owner IDs and labels, including active empty accounts, with explicit completeness. Runtime config filters keys whose bound owner is absent/mismatched; app draft makes legacy/unbound or replaced-account selections reviewable without silently rebinding. Selection operations act on the current filtered set. Diagnostics consume Task 1 status and actual PackageManager version.

**Binding behavior:** A confirmed owner change prevents applying old hidden keys to the new owner, even before the next catalog refresh. Missing/partial snapshots are not logout proof. Legacy unbound keys remain recoverable and require explicit user review/rebind. Do not expose keys to a replacement owner by automatically saving a stale draft binding. Account labels, filter, per-row account context, hidden-only, selected count, bounded Undo (maximum 50 draft snapshots), select matching, Save/Discard are functional. Batch selection affects only current search/account results. A fresh check displays observed details and manual checks without certifying an unperformed device test. Installed and supported versions are shown; exact supported profile remains unchanged. Features 1, 4, 6 come in later tasks.

- [ ] Write RED tests for owner mismatch, confirmed logout versus incomplete inventory, legacy quarantine/rebind, active empty account, account-specific lookup and save validation.
- [ ] Write RED tests for search/account/hidden-only combinations, selected count independent of filters, bounded Undo, batch scope, dirty draft after status updates, and explicit rebind rejecting stale ownership.
- [ ] Implement pure models, persistence migration and runtime owner resolver with retained/verified UserConfig APIs; connect bounded complete owner inventory to catalog atomic publication.
- [ ] Add native UI controls through focused helper components so MainActivity does not accumulate all business logic. Display migration guidance, per-surface details/age and actionable compatibility instructions.
- [ ] Run relevant JVM/Python tests and whole-source compile; update device acceptance instructions for multiple accounts, logout/replacement, empty accounts, and diagnostics.
- [ ] Self-review, commit as `feat: bind concealment to accounts and improve selection controls`, and report exact tests, migration behavior, and reflection validation. Stay on Wave 4 branch; controller coordinates later commits.

### Task 5: Wave 4B — opt-in automatic concealment and device authentication

**Files:** Extend configuration/draft/policy persistence and focused settings UI, `TelegramHook.java`/runtime reveal handling, and `AndroidManifest.xml`. Create pure `RevealSession`/authorization-policy helpers, narrow Android lifecycle/credential adapters, and an `AuthenticationActivity` if an explicit cross-process credential UI is needed. Add behavioral tests for policy/challenge/lifecycle state and source integration checks where Android execution is unavailable.

**Interfaces:** One reveal-session owner exposes revealed state to all filters and handles policy expiry/reload. Configuration supports background concealment, screen-off concealment, timeout, and authentication, all disabled by default. Timeout choices: disabled, 30 seconds, 60 seconds, 300 seconds. Auth challenge: 128-bit random nonce, maximum 120,000ms monotonic lifetime, one-use, invalidated on configuration change or newer challenge. The module's successful device credential result responds through signature-protected IPC to the active Telegram challenge. Use actual module package for release/debug.

**Binding behavior:** Use KeyguardManager device-lock credential confirmation with no custom secrets. Enabled authentication cannot silently bypass an unavailable credential flow. Do not trust intent extras claiming authentication; only the module's successful platform result may emit an authorization response. An exported authentication entry point alone cannot reveal anything. Settings relock on actual background but retain the draft. Background detection tolerates configuration changes; screen-off and expiry conceal and refresh views; old timers cannot conceal a newer session. Suppress-notifications policy stays independent. No new dependency is required.

- [ ] Write RED tests for disabled policies, each enabled trigger, timeout boundaries, stale timer, configuration recreation versus genuine background, cancelled/expired/wrong/replayed challenge, one-use success, config change, and debug package routing.
- [ ] Implement pure reveal/auth policy and connect existing gesture/filter suppliers to a single owner. Resolve the module's actual package from a verified framework API or saved trusted module configuration.
- [ ] Add platform credential activity and lifecycle adapters with permission-protected result receiver; gate settings and reveal and show setup/error/cancellation guidance. Keep credentials out of storage/logs and avoid background Activity launch assumptions.
- [ ] Add opt-in settings controls and preserve draft/save/discard semantics. Document that Android platform prompt and LSPosed lifecycle flows require physical validation before release.
- [ ] Run focused JVM/Python tests and compile the complete Android source/manifest resources in CI; self-review authorization boundaries carefully.
- [ ] Commit as `feat: add optional reveal timeout and device authentication`; report RED/GREEN evidence and pending device cases.

### Task 6: Wave 4C — encrypted configuration backup and feature integration

**Files:** Create focused backup data/codec/import-preview models and a native Storage Access Framework UI controller. Integrate with account/draft/policy models and MainActivity via narrow callbacks; add matching JUnit tests. Update README, device acceptance/release documentation, and the single version source to `3.1.0` / `18`.

**Interfaces:** Backup exports stable owner ID plus dialog ID and non-authentication preferences. `BackupCodec` (or equivalent) encrypts/decrypts a versioned bounded binary payload using char[] passphrases; `ImportPreview` maps recognized owner IDs to current slots and prepares an additive draft change. Import preview is explicit and normal Save persists. Export/import use ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT on worker executor; no plaintext temp files.

**Binding behavior:** AES-256-GCM, random 16-byte salt, random 12-byte nonce, 128-bit tag, PBKDF2-HMAC-SHA256 600,000 iterations; authenticate header metadata as AAD. Version 1 envelope/payload, total maximum 1 MiB, at most 16*1,024 unique owner/dialog entries. Strict lengths/IDs/booleans/duplicate/trailing-byte validation. Reject unknown format and impossible sizes before KDF. Export passphrase minimum 12 characters, confirmation required. Exclude titles, messages, credentials, nonce/reveal session, and authentication enablement. Preview recognized/skipped accounts, counts, and preference changes; match only stable owners and merge without clearing existing selections or disabling authentication. Clear mutable secrets best effort. No network or third-party crypto dependency.

- [ ] Write RED tests for real JCA encrypt/decrypt roundtrip, random output, wrong passphrase, tampered header/ciphertext, truncated/oversized/unknown version, bad IDs/duplicates/trailing bytes, and import owner matching/merge/auth exclusion. Keep expensive KDF test count bounded; cover parser validation without repeating KDF for every malformed input.
- [ ] Implement the bounded codec and pure preview, then Storage Access Framework async UI with cancellation/error handling, passphrase confirmation, preview approval, and apply-to-draft semantics.
- [ ] Verify backup export reads only the user-approved configuration; import cannot bind unknown owners or overwrite authentication policy. Confirm legacy unbound entries are described/skipped rather than silently rebound.
- [ ] Integrate all seven feature controls, update version to 3.1.0/code18, and document setup, migration, backups, rollback, compatibility, and physical acceptance matrix. Do not label physical checks completed.
- [ ] Run complete JVM/Python suites once plus available whole-source compile; controller runs full CI and a whole-change architectural review before Wave 4 merge.
- [ ] Self-review, commit as `feat: add encrypted configuration backup and document privacy controls`, and report exact tests, final feature mapping, and limitations.
