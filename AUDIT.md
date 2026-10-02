# Repository Audit — Royalty (Telegram Chat Hiding)

- **Audit date:** 2026-10-02
- **Commit:** `19f479e` (v3.1.7, `versionCode` 24), branch `arena/01a0face-royalty-telegram-chat-hiding`
- **Scope:** Full repository — application code (≈10.9k lines Java), Xposed hooks, IPC, crypto, build system, CI/CD, supply chain, tests, docs.
- **Method:** Manual line-by-line review of all production Java, build/CI configuration, and scripts; execution of the Python contract suite; upstream checksum verification of Gradle pins; spot-verification of pinned GitHub Action SHAs; secret sweep. JVM unit tests could not be executed in the audit sandbox (no JDK/Android SDK); they are gated in CI.

## 1. Executive summary

**Overall: well above average for a solo-maintained Xposed module.** The codebase shows deliberate security engineering: authenticated AES-GCM backups, a nonce-protected IPC channel, fail-open hook semantics with atomic install/rollback, strict input bounds everywhere, SHA-pinned supply chain, and an honest, explicit threat model. No critical or high-severity vulnerabilities were found.

Findings are concentrated in three areas:

1. **Documentation drifting from code** after the 3.1.5/3.1.6 behavior changes (security-relevant claims in SECURITY.md and README that no longer match the implementation).
2. **Dead/unused authentication surface** (`AuthenticationActivity` exported but never launched by the module; `RevealSession` challenge API unused in production).
3. **Small robustness deviations** (one hardcoded obfuscated method call; wrapper JAR from a different Gradle release than the pinned distribution).

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 2 |
| Low | 5 |
| Informational | 8 |

## 2. Architecture recap (attack surface)

| Surface | Notes |
|---|---|
| Xposed hooks inside `org.telegram.messenger` | Dialogs, notifications, search, share, contacts/group pickers, Premium, reveal gesture. All fail open; atomic installation with rollback (`ModernHookBridge.installAtomically`). |
| Catalog IPC (Telegram → module app) | Runtime receiver in Telegram guarded by signature permission `io.github.cepeter.royalty.permission.CATALOG_REQUEST`; results return through an explicit-component, non-exported `CatalogResultReceiver` via a `PendingIntent`; 128-bit single-use nonce, 15 s lifetime. |
| Config storage | Modern Xposed API 101 remote preferences (framework-managed; no world-readable files). Local caches (catalog, nonces, update state) in `MODE_PRIVATE` app prefs. |
| Backup import/export | SAF document picker; AES-256-GCM envelope, PBKDF2-HMAC-SHA256 @ 600k iterations, header as AAD. |
| Network | One unauthenticated GitHub Releases API poll per 24 h, bounded response, no downloads. |
| UI | `MainActivity` (dashboard), exported `AuthenticationActivity` (see L1). |

## 3. Findings

### Medium

#### M1 — SECURITY.md contradicts the implemented version guard
`SECURITY.md` states: *"Royalty refuses to install filtering hooks unless Telegram reports exactly version 12.10.4 (`versionCode 70992`)."* The code does not do this. Since 3.1.6, `TelegramHook.installRuntimeHooks` accepts **any** Telegram build for which `TelegramCompatibilityProbe`/`TelegramSemanticResolver` validate every required surface; only a *failed* resolution on an untested build reports `unsupported_version`. The helper that would express the documented behavior, `TelegramHook.reportUnsupportedVersion()` (TelegramHook.java:701), is **dead code** — never called.

This matters because SECURITY.md is the contract users rely on; the real behavior (semantic validation of other builds) is defensible but must be documented accurately. **Fix:** rewrite the SECURITY.md bullet to match the resolver-gated behavior, and either wire `reportUnsupportedVersion` into the failed-resolution path for untested builds or delete it.

#### M2 — Hardcoded obfuscated method call bypasses the semantic resolver
`TelegramSearchHook` (line 79) hooks `search.async.refresh` and then executes:

```java
ModernHookBridge.callMethod(param.thisObject, "l");
```

This invokes an arbitrary zero-argument method named `l` on the search adapter by raw obfuscated name — exactly the pattern the resolver/cache design exists to avoid. On the tested 12.10.4 profile it presumably hits the intended method; on any other semantically-validated build, `findCompatibleMethod` will bind *whatever* 0-arg `l` exists on that class, with unknown side effects, or throw (fail-open via protective exception mode). **Fix:** resolve this target through `TelegramSemanticResolver` (signature-based, like `search.view.invalidate`) and pin it in `TelegramCompatibilityProbe`.

### Low

#### L1 — Exported, unused `AuthenticationActivity`
The activity is `android:exported="true"`, validates only the *shape* of a nonce (32 lowercase hex chars), then presents the system device-credential prompt titled "Reveal hidden chats". No production code path ever launches it: the Telegram-side reveal gesture intentionally skips authentication (3.1.5), `RevealSession.beginChallenge()`/`authorize()` have no production callers, and nothing in Telegram listens for the resulting `AUTH_RESULT` broadcast. Any installed app can therefore force a misleading "Reveal hidden chats" credential prompt (social-engineering/annoyance vector). Impact is contained — the result broadcast is package-scoped to Telegram and there is no listener — but the component is pure dead attack surface. **Fix:** delete the activity + `AuthenticationProtocol`/`AuthenticationRoute` (+ tests), or implement the reveal-challenge flow it was built for.

#### L2 — Gradle wrapper JAR is from a different Gradle release than the pinned distribution
- `gradle-wrapper.properties` pins distribution `gradle-8.11.1-bin.zip` with `distributionSha256Sum=f397b287…eee151c6` — **verified correct** against gradle.org's official 8.11.1 checksum.
- `gradle/wrapper/gradle-wrapper.jar` hashes to `498495120a03…134484f17`, which is the official **Gradle 8.9** wrapper JAR checksum (per gradle.org release-checksums), not the 8.11.1 jar (`2db75c40…88448046`).

The jar is a genuine Gradle artifact (correct `org.gradle.wrapper` contents, `GradleWrapperMain`) and the distribution itself is checksum-verified with `validateDistributionUrl=true`, so this is not a compromise — but it breaks the "everything pinned and matching" story and the custom minimal `gradlew`/`gradlew.bat` pair adds drift. **Fix:** regenerate the wrapper from Gradle 8.11.1 (or bump distribution to match the jar) and keep `gradlew*` canonical.

#### L3 — Nonce expiry uses persisted `elapsedRealtime()` across reboots
`PendingRequestStore` persists nonce→expiry (`SystemClock.elapsedRealtime() + 15s`) in `catalog_requests` prefs. `elapsedRealtime` resets to 0 at boot, so after a reboot a stale persisted nonce can appear unexpired until the next `create()` clears the store. Exploitability is negligible in practice (nonces are 128-bit random, one-shot, and the process-local `CatalogUpdates`/`CatalogResultReceiver` statics reset with the process), but the invariant "expired after 15 s" is technically violated. **Fix:** store a boot marker (e.g., `SystemProperties` boot id / boot count) and drop persisted entries on boot change.

#### L4 — Chat titles cached unencrypted on disk
The catalog snapshot (dialog IDs + titles, ≤256 code units each) is persisted in plaintext in the module app's `catalog` SharedPreferences. This is the intended UX (picker must render titles without Telegram running) and is mitigated by the app sandbox, `allowBackup=false`, and `data_extraction_rules` exclusions, but it should be disclosed alongside the backup section: on rooted/forensically-compromised devices, chat titles of hidden dialogs are readable. SECURITY.md already excludes root compromise from the threat model — add one explicit sentence about the local title cache.

#### L5 — Hidden-dialog identifiers in saved instance state
`MainActivity.onSaveInstanceState` serializes the full `ConfigurationDraft` (including `account:dialogId` selections) into the instance-state bundle, which Android may persist to disk on process death. Minor plaintext data-at-rest beyond the preferences store; titles are not included. Consider excluding selection details from instance state or keeping only UI state.

### Informational

- **I1 — Dead code:** `TelegramHook.reportUnsupportedVersion`; `RevealSession.beginChallenge/authorize/cancelChallenge` (no production callers); `AuthenticationRoute` (tests only); `TelegramSearchHook.findMethod`; `TelegramContactHook.isClass`; legacy `CatalogBatch.begin(String,int[],String)` overload (tests only). Harmless, but invites the kind of doc/code drift seen in M1/L1.
- **I2 — README claim stale:** "Offers optional device screen-lock confirmation for settings **and reveal**" (README.md:32). Since 3.1.5 authentication protects only opening Royalty settings; the reveal gesture is deliberately immediate. Update the bullet.
- **I3 — Hot-path reflection:** `XposedConfigRepository.current()` re-reads remote preferences and re-runs the full `UserConfig` reflection inventory on every hooked call (e.g., each `MessagesController.getDialogs`); `publishCatalogIfDue` additionally invokes Telegram `UserConfig` getters from the hook thread, which Telegram itself treats as main-thread state. Errors are caught and fail open, but a small TTL cache with change notification would reduce both cost and cross-thread races.
- **I4 — Screenshots:** `FLAG_SECURE` is cleared when settings-lock is disabled, so the chat-title list is screenshot-able by default. Intentional UX trade-off; worth a docs sentence.
- **I5 — Local Premium:** forcing `UserConfig.isPremium()` true client-side is a ToS-grey-area feature (properly attributed to TeleVip-LSPosed in NOTICE.md). Non-security; flagging as policy risk only.
- **I6 — After a failed `ConfigStore.save`, `ConfigurationDraft.requireVerifiedBaseline` locks baseline updates for the activity's lifetime** — safe, but users must restart the app to re-sync; consider surfacing this in the error text.
- **I7 — Test executability:** 109/109 Python contract tests pass locally. JVM unit tests (41 files) are well-targeted at the core classes but could not be run in this audit environment; CI covers them.
- **I8 — `MainActivity` ignores launch-intent extras** (no injection surface) — verified positive.

## 4. What the repo gets right (verified)

- **Backup crypto (`BackupCodec`):** AES-256-GCM with the entire envelope header bound as AAD; fresh 16-byte salt + 12-byte nonce per export; PBKDF2-HMAC-SHA256 at 600k iterations (OWASP current); 12-char passphrase minimum; strict 1 MiB bound; exact-length/trailing-byte checks; key and plaintext zeroed in `finally`; passphrase `char[]` zeroed after use (`BackupOperation`). Import is preview-only and re-validates ownership at Apply and Save. This is textbook correct.
- **Catalog IPC:** request broadcast is package-scoped and the in-Telegram receiver requires the module's **signature-level** permission, so third-party apps can neither request nor spoof catalogs. The callback is an explicit-component `PendingIntent` to a non-exported receiver, data-URI namespaced per nonce, with single-use 128-bit nonces (15 s) and full sequence validation (`CatalogBatch` rejects out-of-order/duplicate/oversized frames; statuses token-validated `[a-z_]`, bounded counts/lengths). `PendingIntent` mutability is correctly limited to what fill-in requires.
- **Update checker:** HTTPS-only, redirects disabled, 64 KiB response cap, 8 s timeouts, release URL must equal `https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/<tag>` parsed and re-validated as https/github.com/no-port/no-userinfo; draft/prerelease rejected; no APK download or install; clock-rollback guard on the 24 h throttle; UA leaks only app version.
- **Hook safety:** protective exception mode, fail-open on any filtering error (unknown rows stay visible), atomic multi-hook install with reverse-order rollback, per-surface status reporting, filtered *copies* (never mutation of Telegram-owned lists) except the one structurally-validated selected-dialog map with snapshot/restore.
- **Supply chain:** 715 SHA-256 pins in `verification-metadata.xml` (signature verification disabled with a documented, coherent rationale); all 7 GitHub Actions pinned to full SHAs (4 spot-checked against the GitHub API → checkout v4, setup-java v4, action-gh-release 2.6.2, upload-artifact v4); `distributionSha256Sum` correct; release pipeline gates on reproducible double-build, pinned-signer `apksigner` verification (`release-signing-cert.sha256`), metadata/tag consistency, and serialized publication with monotonic version-code enforcement. No secrets in the tree; signing material only via CI environment secrets.
- **Manifest hygiene:** `allowBackup=false`, cloud-backup/device-transfer exclusions, no services, `INTERNET` used solely for the update check, catalog receiver non-exported, `<queries>` limited to Telegram.

## 5. Recommended actions (priority order)

1. Rewrite the SECURITY.md version-guard bullet to match resolver-gated behavior; delete or use `reportUnsupportedVersion` (M1).
2. Resolve the hardcoded `"l"` call through the semantic resolver + probe (M2).
3. Remove `AuthenticationActivity` and the unused challenge API, or finish the reveal-authentication feature (L1, I1).
4. Regenerate the Gradle wrapper so jar/distribution/scripts agree (L2).
5. Fix README line 32 ("settings and reveal") (I2).
6. Minor: boot-aware nonce expiry (L3), docs note for the title cache (L4), instance-state minimization (L5).
