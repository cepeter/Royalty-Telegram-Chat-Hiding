# Royalty audit remediation and feature specification

Date: 2026-09-29. Baseline: `32d5ef001c6b7c1db1d087d39776a970e47a4d57`.

The repository owner approved implementation of audit Waves 1–4, all seven proposed features, pull requests, and merging when possible. This document makes that approved roadmap concrete. It does not authorize a release, tag, signing-key change, or deployment to a device.

## Goal and scope

Make configuration edits durable, runtime concealment internally consistent, and release verification trustworthy. Add account-aware management, understandable diagnostics, optional reveal protection, and encrypted configuration backup while retaining Royalty's small native Android application and modern Xposed integration.

Supported Telegram remains exactly `org.telegram.messenger` version `12.10.4`, version code `70992`, main process only. Other versions must receive clear unsupported guidance and must not acquire speculative hook profiles. Java 17, minimum Android API 27, Xposed API 101, existing dependency verification and signature-protected IPC remain the baseline. Local Premium remains an independent optional setting and does not determine privacy health.

## Delivery and acceptance

Deliver four separately described PRs. Wave 1 repairs app state, refresh IPC, and health reporting. Wave 2 repairs hook installation, filtered-list reconciliation, and share selection. Wave 3 hardens release validation and retains recovery releases. Wave 4 completes all seven features, account ownership, documentation, and version `3.1.0` / code `18`. Wave 4 may contain multiple independently reviewed commits.

Each task needs behavior tests, a focused code review, and local checks appropriate to the changed code. Every PR must pass the repository's full Android CI before merge; respect enforced branch rules. Never claim a physical-device test from JVM or static evidence. Device-only acceptance is recorded explicitly and remains a release gate, even when a reviewed PR can merge. Keep public release creation separate from merging.

## Wave 1: app state and catalog refresh

The settings screen owns a saved baseline and an editable draft. Refreshes, status changes, service reconnects, activity resumes, and configuration recreation must not erase an initialized dirty draft. Before the first saved configuration is available, editing is disabled or otherwise guarded against overwriting unseen settings. Save commits the draft and resets the baseline only after success. Discard restores the last saved baseline. Failed saves retain the draft and explain the failure.

Only the current catalog request may finish the current refresh. Superseding a request invalidates its predecessor; stale callbacks and duplicate completions cannot change the cache, cancel the new watchdog, or announce success. Replace the spoofable app-internal completion broadcast with an in-process observer carrying the request identity. Observers are registered and removed with the activity lifecycle. The actual application package must work for both release and `.debug` installations.

Stage a bounded snapshot under a request nonce. A begin frame declares the expected accounts and process session. Account and status frames populate the staging area; a complete frame publishes the entire valid snapshot with one checked persistence operation. Missing, duplicate, expired, malformed, or obsolete frames preserve the previous cache and surface a failure or timeout. Use existing nonce lifetime (15 seconds), account bounds (0–15), per-account catalog bound (1,024), title bound (256 UTF-16 code units), and status bound (16). Do not relax the protected Telegram request receiver, explicit non-exported result receiver, or callback nonce validation.

Health distinguishes checking, current healthy, current degraded/unsupported, stale last-known results, and unavailable/error. Show per-surface state, detail, and response age. Required privacy surfaces are bridge, compatibility, dialogs, search, contacts, share, notifications, and reveal. A nonempty subset is not complete success. A reported unknown-row fallback is degraded even if hook installation succeeded. Optional Premium is shown separately. Health must describe observations, not certify untested visual coverage.

## Wave 2: runtime consistency

Install each surface's related hooks transactionally: resolve prerequisites, collect every returned hook handle, and remove already-installed hooks in reverse order if any later step fails. A missing surface must not retain a partial count/position contract. Roll back partially assigned paired adapter fields; report failures without corrupting Telegram-owned data.

When Telegram mutates an applied filtered list in place, preserve the current visible identity order. Preserve concealed source objects for later reveal, including duplicate object identities and inserted, removed, replaced, or reordered visible objects. Hidden objects retain a deterministic relative anchor to surviving visible neighbors; new visible items keep their visible positions. Nulls and independent adapter instances must remain safe. Existing paired-list size mismatch behavior stays conservative.

Selection cleanup must inspect the complete selected-recipient collection, including recipients chosen through search. Reconcile on relevant configuration changes as well as reveal changes. Remove only recipients now hidden for the owning account, preserve every visible recipient, and update the reconciliation marker only after success. Resolve the pinned collection's traversal contract from verified API/bytecode or unambiguous validated structural descriptors; do not invent obfuscated aliases. On failure preserve or restore the original selection and report degraded health. Never send a message during testing.

## Wave 3: release integrity

Inspection command failure is fatal. Capture and validate analyzer output before searching it, and do not emit successful checksum artifacts after a failed prerequisite. Derive version metadata from one checked-in source used by Gradle and verification. At publication, the exact semantic tag, APK version name, and configured version must agree, and version code must exceed the last published known code. The APK must be signed by the expected existing production certificate; establish that certificate from the existing published artifact, never invent or regenerate a signing identity.

Serialize publication. Retain prior published releases and tags for rollback; remove automatic destructive cleanup of previous or unrelated releases/tags. Keep least-privilege workflow permissions, pinned actions, dependency checksums, and protected production signing. A manual non-tag build may create artifacts but must not silently publish a release. Describe payload reproducibility accurately; do not imply identical RSA-PSS archive bytes across independent signatures.

## Wave 4: account identity and seven features

### Account ownership

Runtime row keys can remain slot plus dialog ID, but persisted concealment must also bind each slot to the stable Telegram owner ID. Resolve the current owner from that account's UserConfig, never the globally selected account. A confirmed owner mismatch must stop the old owner's settings from applying to the replacement account. An omitted/incomplete catalog frame is not proof of logout. Only a complete account inventory may reconcile confirmed logout or owner change. Legacy selections without owner evidence are retained for review and explicit rebinding, not silently assigned to whichever person occupies a slot later. Expose migration guidance and account labels in the app.

### Feature 1: automatic re-concealment

Add opt-in settings for re-concealing when Telegram goes to background, the screen locks/turns off, or a reveal timeout expires. Defaults are off, off, and disabled. Timeout options are disabled, 30 seconds, 1 minute, and 5 minutes. Use monotonic time. Activity configuration changes must not count as a genuine background transition. Conceal and refresh relevant adapters after expiration; stale timers cannot close a newer reveal session. Notification suppression retains its existing independent policy.

### Feature 2: account management

Show stable account labels, an account filter, and account context on each dialog row. Keep selection independent of search/filter rendering. Do not expose unnecessary personal data in logs or diagnostics exports. Bind saving/importing to a confirmed current owner.

### Feature 3: actionable diagnostics

Provide detailed per-surface health, cache age, installed and supported versions, and guidance for missing bridge, unsupported version, incomplete scan, degraded fallback, and expired refresh. A guided check requests fresh runtime observations and explains manual surface checks. It must not report a device acceptance test as passed automatically.

### Feature 4: optional authentication

Use Android's device-lock credential confirmation for optional settings access and reveal authentication. Authentication defaults off. Do not invent a custom PIN, store a password, or add a network service. Unsupported/unconfigured secure lock has a clear setup message; never silently bypass an enabled requirement. Settings relock after backgrounding while retaining the draft. Reveal authorization uses a fresh random, expiring, one-use challenge owned by Telegram; its response is protected by the existing module signature permission, and validates the active request and current configuration. Cancellation, wrong/expired/replayed responses, and exported-entry-point invocation cannot authorize reveal. Release and debug routing use the actual module package. Platform UI and Telegram lifecycle require device acceptance before a release.

### Feature 5: selection usability

Add hidden-only view, selected count, Undo, select matching visible results, and explicit Save/Discard. Preserve search and account filters when rendering status changes. Undo acts on draft edits, is bounded, and does not revert a successfully saved external configuration unexpectedly. Batch operations name their scope and cannot affect invisible accounts by accident.

### Feature 6: encrypted configuration backup

Use Storage Access Framework export/import with a user passphrase, entirely on-device. Backups contain stable owner IDs, dialog IDs, and non-authentication preferences; exclude chat contents, catalog titles, transient reveal state, nonces, device credentials, and authentication enablement. Use a versioned bounded binary envelope with AES-256-GCM, a random 16-byte salt, a random 12-byte nonce, a 128-bit tag, and PBKDF2-HMAC-SHA256 at 600,000 iterations. Authenticate envelope metadata as AAD. Reject unsupported versions, malformed lengths, invalid IDs, duplicate entries, trailing bytes, and input larger than 1 MiB before expensive work where possible. Minimum export passphrase length is 12 characters with confirmation; clear mutable secrets best-effort and avoid plaintext temporary files or logs. Run KDF and file I/O off the UI thread.

Preview recognized accounts, selections, skipped owners, and preference changes before applying an import. Match only stable current account IDs; never bind by slot alone. Merge approved import selections into the draft and require the normal Save action for persistence. Import must not automatically disable authentication or discard existing selections.

### Feature 7: compatibility guidance

Display installed Telegram package/version and the exact supported version, runtime process/session evidence, and steps to recover an unsupported or incomplete setup. A check may refresh observed status; it must not guess offsets, add unverified profiles, or claim full runtime protection from package metadata alone.

## Design boundaries and known limitations

Prefer small pure-Java models for state, ordering, authorization, and backup parsing, with narrow Android adapters. Avoid adding third-party dependencies where platform APIs suffice. Existing account IDs and dialog IDs are metadata, not anonymous data; encrypt backups and avoid gratuitous logging. Live Telegram adapters, OEM credential flows, Xposed preferences, notification behavior, and a supported APK on an LSPosed device need explicit device verification. These are documented limitations rather than fabricated completed tests.
