# Device acceptance — Royalty 3.1.0 (pending)

## Release identity and status

- Current source version: `3.1.0` (`versionCode 18`); this is **not** a publication or device acceptance claim.
- Required Telegram: official `org.telegram.messenger` `12.10.4` (`versionCode 70992`), main process only.
- Verified reference Telegram APK SHA-256: `146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d`.
- Production signer pin and historical v3.0.5 bootstrap evidence: [release integrity](release-integrity.md). Do not reinterpret that historical APK as a 3.1.0 artifact.
- Physical-device result: **pending** for every item below. No 3.1.0 device session is recorded here.

## Seven approved features and physical checks

| Feature | Expected behavior to verify on device | 3.1.0 result |
|---|---|---|
| 1. Automatic re-concealment | Default background and screen-off switches disabled; verify each opt-in, timeout Off/30 s/1 min/5 min, re-reveal, app configuration changes, and independent notification suppression. | Pending |
| 2. Account management | Two accounts have correct owner labels and account filter; saved selections retain their original slot and owner across logout, incomplete refresh, and replacement. A move to another slot requires explicit rebinding or owner-matched backup import; selections do not automatically migrate. Legacy unbound selections remain visible for explicit review. | Pending |
| 3. Actionable diagnostics | Refresh while Telegram active, closed, unsupported, or bridge disabled; check cache age, installed/supported versions, last-known versus fresh observations, per-surface status/details, and manual guidance. | Pending |
| 4. Optional authentication | Enable only with secure device lock; verify settings relock after background, draft retention, credential cancellation, reveal challenge expiry/replay, and OEM credential activity behavior. | Pending |
| 5. Selection usability | Combine search, account and hidden-only filters; check selected count, scoped Select matching, Undo, Save, Discard, and persistence after restart. | Pending |
| 6. Encrypted backup | Export saved baseline (not unsaved edits) to local and optional cloud document providers; import with wrong/correct passphrase; review unknown, ambiguous, replaced, conflicting and legacy owners; Apply, Save, Discard, picker cancellation and recreation. Confirm no cleartext document, title, authentication policy or credentials in backup. | Pending |
| 7. Compatibility guidance | Confirm installed Telegram identity and exact supported target; unsupported builds must reject hooks and explain recovery. A fresh check must not claim physical tests passed. | Pending |

## Cross-surface and release gates

Perform the historical [2.2.0 surface matrix](device-acceptance-2.2.0.md) again on the exact target: concealed, revealed and re-concealed dialog lists/folders, search variants, share targets, group/member/contact flows, and notifications across two accounts. Never transmit a test Telegram message as part of automated validation; a live acceptance operator must control any real interaction. Confirm fail-open health when a hook cannot install and that runtime filtering uses each row's owning account. Physical checks remain **pending**.

Repository/JVM/Python checks and the Android CI gates (unit tests, lint, debug/release APK assembly, signer/metadata verification, reproducibility) are distinct from the physical tests. A release also requires the protected signing and publication workflow, matching tag, version-code progression, and explicit approval. No release, tag, production signing, deployment, or rollback was performed for this checklist.

For rollback, keep the previous release assets and the encrypted backup. An older installed APK may not understand this backup format or newer settings; choose a supported build and review Android downgrade restrictions before changing an installation. The backup stores owner IDs, so restoring onto a different account does not automatically bind selections.

## Final integration acceptance notes

A final ordinary activity stop re-conceals immediately when the background policy is enabled, including when another activity was rotating. A final configuration stop receives a 700 ms scheduled replacement grace. Activity identities and generation checks prevent stale callbacks from affecting a new foreground session; if recreation is interrupted, deferred reconciliation recognizes the empty started set. An unusually slow recreation may re-conceal after this grace and require a new reveal. Android scheduling, device sleep, overlapping activities and credential handoff remain physical acceptance checks.

Settings screenshots are blocked while the saved authentication policy is unknown or enabled. Confirmed authentication-disabled settings permit screenshots; editing the draft alone does not change that confirmed policy. Device credential confirmation and modal revocation remain separate from this settings-window screenshot policy.

Import preview shows current owner/account labels and per-owner selection outcomes. It displays at most 20 owner groups, with recognized current accounts first, then reports omitted unknown/ambiguous group and selection totals. Existing-owner conflicts and unbound selections remain explicit skips. Test a moved account whose retained and current slots contain the same owner/dialog: export should include one canonical pair.

The adapter-refresh prerequisite uses the source-derived structural contract described in the [compatibility map](telegram-12.10.4-compatibility.md), with runtime uniqueness validation. This change does not add compiled-APK or physical-redraw evidence: verify conceal/reveal/re-conceal on every listed surface on the exact supported APK before release.
