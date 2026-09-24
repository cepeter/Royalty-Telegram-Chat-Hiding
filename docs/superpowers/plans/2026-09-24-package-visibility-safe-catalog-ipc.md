# Package-Visibility-Safe Catalog IPC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Telegram-to-module service binding with an authenticated module-to-Telegram request and `PendingIntent` callback flow that works under Android package visibility.

**Architecture:** Telegram keeps bounded catalog/status snapshots in memory and exposes a package-targeted runtime request receiver. The module app sends a request containing an exact-component mutable callback token and persists validated per-account responses through the existing `CatalogRepository`.

**Tech Stack:** Java 17, Android SDK 35/minSdk 27, legacy Xposed API 82, Android broadcasts and `PendingIntent`, JUnit 4, Python contract tests, Gradle 8.9/AGP 8.7.3.

**Spec:** `docs/superpowers/specs/2026-09-24-package-visibility-safe-catalog-ipc.md`

## Global Constraints

- Support Android 8.1 through Android 15.
- Target only `org.telegram.messenger` and its main process.
- Catalog input is limited to 1,024 dialogs per account and 256 UTF-16 code units per title.
- Transmit no message text and no data beyond account, dialog ID, display title, and bounded hook status.
- Use no root-owned file, Unix socket, world-writable path, or exported persistent Binder service.
- Fail open: transport or hook failure must not crash or block Telegram.
- Preserve existing catalog preferences and hidden-dialog configuration.

## Review Focus

- A request from any package other than the module must not receive a catalog; test callback creator-package rejection in Task 2.
- A replayed or expired callback must not modify catalog storage; test nonce expiry and completion invalidation in Task 3.
- Multiple Telegram accounts must arrive as separate bounded results without replacing each other; test per-account persistence in Task 3.
- Android 8.1 and Android 13+ must use compatible receiver and PendingIntent flags; pin both branches in Task 2 and Task 3 contract tests.
- Telegram closed or killed during refresh must retain the previous catalog and return a visible timeout state; test UI behavior in Task 4.

---

### Task 1: Finalize runtime compatibility fixes already discovered

**Files:**
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramHook.java`
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/core/CatalogSubmission.java`
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/config/ConfigStore.java`
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/xposed/XposedConfigRepository.java`
- Test: `app/src/test/java/io/github/cepeter/telegramhider/core/CatalogSubmissionTest.java`
- Test: `tests/test_android_security_contract.py`
- Test: `tests/test_xposed_hook_contract.py`

**Interfaces:**
- Consumes: `CatalogSubmission.MAX_ENTRIES`, `PressAndHoldGesture`.
- Produces: a Telegram 12.8.3-compatible `ActionBar.dispatchTouchEvent(MotionEvent)` hook and one canonical catalog limit.

- [ ] **Step 1: Verify the failing runtime contracts are represented**

Confirm the tests require:

```python
self.assertIn('"dispatchTouchEvent"', hook_source)
self.assertNotIn('"onInterceptTouchEvent"', hook_source)
self.assertIn("CatalogSubmission.MAX_ENTRIES", hook_source)
```

and Java requires exact limits:

```java
assertEquals(1024, CatalogSubmission.MAX_ENTRIES);
assertEquals(256, CatalogSubmission.MAX_TITLE_LENGTH);
```

- [ ] **Step 2: Run the focused tests**

Run:

```bash
python3 -m unittest tests.test_android_security_contract tests.test_xposed_hook_contract -v
./gradlew --no-daemon testDebugUnitTest --tests io.github.cepeter.royalty.core.CatalogSubmissionTest
```

Expected: PASS after the already-applied fixes; any failure must be corrected before proceeding.

- [ ] **Step 3: Build the runtime-compatible debug APK**

Run:

```bash
./gradlew --no-daemon lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`, no lint warnings, and no reference to an exact `dispatchTouchEvent` hook.

- [ ] **Step 4: Commit the bounded runtime fixes**

```bash
git add app/src/main app/src/test tests/test_android_security_contract.py tests/test_xposed_hook_contract.py
git commit -m "fix: align Telegram runtime hooks and catalog bounds"
```

### Task 2: Add the pure snapshot model and authenticated Telegram request bridge

**Files:**
- Create: `app/src/main/java/io/github/cepeter/telegramhider/catalog/CatalogProtocol.java`
- Create: `app/src/main/java/io/github/cepeter/telegramhider/xposed/CatalogSnapshotStore.java`
- Create: `app/src/main/java/io/github/cepeter/telegramhider/xposed/CatalogRequestBridge.java`
- Create: `app/src/test/java/io/github/cepeter/telegramhider/xposed/CatalogSnapshotStoreTest.java`
- Create: `tests/test_catalog_request_bridge_contract.py`
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramHook.java`

**Interfaces:**
- Produces: `CatalogSnapshotStore.replaceAccount(int,long[],String[])`, `recordStatus(String,String,String)`, and `snapshot()`.
- Produces: `CatalogRequestBridge.register(Context,CatalogSnapshotStore)`.
- Produces shared constants in `CatalogProtocol`: request/result actions, extras, result types, package names, status limits, and nonce lifetime.

- [ ] **Step 1: Write failing snapshot tests**

Test that `replaceAccount` copies arrays, caps entries/titles through `CatalogSubmission.sanitize`, replaces only one account, preserves another account, and returns an immutable snapshot. Test that status names/details enforce the existing 32/256 limits.

Representative test:

```java
store.replaceAccount(0, new long[] {1}, new String[] {"one"});
CatalogSnapshotStore.Snapshot first = store.snapshot();
store.replaceAccount(0, new long[] {2}, new String[] {"two"});
assertEquals(1L, first.accounts().get(0).ids()[0]);
```

- [ ] **Step 2: Run the new test and verify RED**

```bash
./gradlew --no-daemon testDebugUnitTest --tests io.github.cepeter.royalty.xposed.CatalogSnapshotStoreTest
```

Expected: FAIL because `CatalogSnapshotStore` does not exist.

- [ ] **Step 3: Implement the snapshot store and protocol constants**

Use synchronized replacement and deep copies. Define exact protocol constants:

```java
public static final String ACTION_REQUEST =
        "io.github.cepeter.royalty.action.REQUEST_CATALOG";
public static final String ACTION_RESULT =
        "io.github.cepeter.royalty.action.CATALOG_RESULT";
public static final String EXTRA_CALLBACK = "callback";
public static final String EXTRA_NONCE = "nonce";
public static final String EXTRA_TYPE = "type";
public static final String EXTRA_ACCOUNT = "account";
public static final String EXTRA_IDS = "ids";
public static final String EXTRA_TITLES = "titles";
public static final String EXTRA_STATUS_HOOKS = "status_hooks";
public static final String EXTRA_STATUS_VALUES = "status_values";
public static final String EXTRA_STATUS_DETAILS = "status_details";
public static final String TYPE_ACCOUNT = "account";
public static final String TYPE_STATUS = "status";
public static final String TYPE_COMPLETE = "complete";
public static final long NONCE_LIFETIME_MS = 15_000L;
```

- [ ] **Step 4: Write failing bridge contract tests**

Require the bridge to:

```python
self.assertIn("getCreatorPackage()", source)
self.assertIn('CatalogProtocol.MODULE_PACKAGE.equals', source)
self.assertIn("Context.RECEIVER_EXPORTED", source)
self.assertIn("PendingIntent.CanceledException", source)
self.assertNotIn("bindService", source)
```

Also require one result send per account, a status result, and a completion result.

- [ ] **Step 5: Implement the Telegram request bridge**

Register a process-lifetime receiver. Reject null callbacks and callbacks whose creator package is not the module package. For each snapshot account, call:

```java
Intent result = new Intent()
        .putExtra(CatalogProtocol.EXTRA_TYPE, CatalogProtocol.TYPE_ACCOUNT)
        .putExtra(CatalogProtocol.EXTRA_ACCOUNT, account)
        .putExtra(CatalogProtocol.EXTRA_IDS, ids)
        .putExtra(CatalogProtocol.EXTRA_TITLES, titles);
callback.send(context, Activity.RESULT_OK, result);
```

Send bounded status arrays, then `TYPE_COMPLETE`. Catch `PendingIntent.CanceledException` and runtime failures without exposing titles/IDs in logs.

- [ ] **Step 6: Connect Telegram hooks to the store**

Create one static store. Register the bridge after `ApplicationLoader.onCreate`. Replace `CatalogPublisher.submit/reportStatus` calls with store methods. Keep hook failures fail-open.

- [ ] **Step 7: Run focused tests and commit**

```bash
./gradlew --no-daemon testDebugUnitTest
python3 -m unittest tests.test_catalog_request_bridge_contract tests.test_xposed_hook_contract -v
git add app/src/main app/src/test tests
git commit -m "feat: expose bounded Telegram catalog snapshots"
```

### Task 3: Add nonce-validated module callback transport

**Files:**
- Create: `app/src/main/java/io/github/cepeter/telegramhider/catalog/CatalogRequestClient.java`
- Create: `app/src/main/java/io/github/cepeter/telegramhider/catalog/CatalogResultReceiver.java`
- Create: `app/src/main/java/io/github/cepeter/telegramhider/catalog/PendingRequestStore.java`
- Create: `app/src/test/java/io/github/cepeter/telegramhider/catalog/PendingRequestStoreTest.java`
- Create: `tests/test_catalog_result_contract.py`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/catalog/CatalogRepository.java`

**Interfaces:**
- Produces: `CatalogRequestClient.request(Context)` returning `long expiresAtElapsedRealtime`.
- Produces: `PendingRequestStore.Request create(long nowElapsedRealtime)`, `isActive(String nonce,long nowElapsedRealtime)`, and `complete(String nonce)`; `Request` exposes `nonce()` and `expiresAtElapsedRealtime()`.
- Consumes: `CatalogProtocol` and `CatalogRepository.replaceAccount/recordStatus`.

- [ ] **Step 1: Write failing nonce-store tests**

Cover unique 128-bit nonces, active-before-expiry, rejected-after-expiry, accepted multiple times before completion, and rejected after `complete`.

- [ ] **Step 2: Run the nonce tests and verify RED**

```bash
./gradlew --no-daemon testDebugUnitTest --tests io.github.cepeter.royalty.catalog.PendingRequestStoreTest
```

Expected: FAIL because `PendingRequestStore` does not exist.

- [ ] **Step 3: Implement nonce storage**

Use `SecureRandom` with 16 bytes encoded as lowercase hex. Persist nonce expiry in private SharedPreferences so a result can survive Activity recreation. Remove expired entries during `create` and `isActive`.

- [ ] **Step 4: Write failing callback contract tests**

Require:

```python
self.assertIn("PendingIntent.FLAG_MUTABLE", client)
self.assertIn("setComponent", client)
self.assertIn('setPackage("org.telegram.messenger")', client)
self.assertIn("CatalogSubmission.sanitize", receiver)
self.assertIn("isActive", receiver)
self.assertIn("TYPE_COMPLETE", receiver)
self.assertIn('android:exported="false"', manifest)
```

Require API-level branches for mutable flags and receiver registration.

- [ ] **Step 5: Implement the request client**

Create an exact callback intent for `CatalogResultReceiver` containing the nonce. Use `FLAG_UPDATE_CURRENT | FLAG_MUTABLE` on API 31+ and `FLAG_UPDATE_CURRENT` below API 31. Send `ACTION_REQUEST` with `setPackage(CatalogProtocol.TELEGRAM_PACKAGE)` and the callback token.

- [ ] **Step 6: Implement the result receiver**

Reject inactive nonces before parsing. Handle:

- `TYPE_ACCOUNT`: call `CatalogRepository.replaceAccount` after array/account validation;
- `TYPE_STATUS`: validate parallel bounded arrays and call `recordStatus`;
- `TYPE_COMPLETE`: invalidate the nonce and broadcast an app-local catalog-updated action.

Unknown types and malformed payloads must leave existing storage unchanged.

- [ ] **Step 7: Replace the service manifest entry**

Remove `.catalog.CatalogService`. Add:

```xml
<receiver
    android:name=".catalog.CatalogResultReceiver"
    android:exported="false" />
```

- [ ] **Step 8: Run focused tests and commit**

```bash
./gradlew --no-daemon testDebugUnitTest
python3 -m unittest tests.test_catalog_result_contract tests.test_android_security_contract -v
git add app/src/main app/src/test tests
git commit -m "feat: receive authenticated catalog snapshots"
```

### Task 4: Integrate refresh lifecycle and user-visible timeout state

**Files:**
- Modify: `app/src/main/java/io/github/cepeter/telegramhider/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `tests/test_configuration_ui_contract.py`

**Interfaces:**
- Consumes: `CatalogRequestClient.request(Context)` and the catalog-updated action.
- Produces: refresh-on-resume, refresh-button request, immediate cached rendering, and timeout guidance.

- [ ] **Step 1: Write failing UI contract tests**

Require `onResume` and the Refresh button to call `CatalogRequestClient.request(this)`, require a receiver for the catalog-updated action, and require text equivalent to “Open Telegram, then refresh” when no result arrives.

- [ ] **Step 2: Run the UI contract and verify RED**

```bash
python3 -m unittest tests.test_configuration_ui_contract -v
```

Expected: FAIL because the request client is not integrated.

- [ ] **Step 3: Implement Activity lifecycle integration**

Render cached data immediately, send one request on resume, register an update receiver while resumed, unregister it on pause, and rerender after each accepted result. Refresh must request a new nonce rather than only rereading preferences.

Use a main-thread delayed check at `NONCE_LIFETIME_MS`; if no completion arrived, retain cached rows and show the guidance string without clearing selected dialogs.

- [ ] **Step 4: Run UI tests and commit**

```bash
python3 -m unittest tests.test_configuration_ui_contract -v
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
git add app/src/main tests/test_configuration_ui_contract.py
git commit -m "feat: refresh catalog through Telegram callback"
```

### Task 5: Remove obsolete Binder transport and harden release contracts

**Files:**
- Delete: `app/src/main/aidl/io/github/cepeter/telegramhider/ICatalogService.aidl`
- Delete: `app/src/main/java/io/github/cepeter/telegramhider/catalog/CatalogService.java`
- Delete: `app/src/main/java/io/github/cepeter/telegramhider/xposed/CatalogPublisher.java`
- Modify: `app/build.gradle.kts`
- Modify: `tests/test_android_package_contract.py`
- Modify: `tests/test_android_security_contract.py`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/device-acceptance-2.0.0.md`

**Interfaces:**
- Consumes: completed broadcast/PendingIntent transport.
- Produces: no AIDL generation, no exported catalog service, and documentation matching the runtime design.

- [ ] **Step 1: Change contracts to reject the old transport**

Require that the manifest has no exported catalog service, source has no `ICatalogService`, `bindService`, or `Binder.getCallingUid`, and APK packaging has no generated AIDL interface. Preserve the separate `MODE_WORLD_READABLE` assertion for Vector/LSPosed configuration preferences.

- [ ] **Step 2: Run contracts and verify RED**

```bash
python3 -m unittest tests.test_android_package_contract tests.test_android_security_contract -v
```

Expected: FAIL while old service/AIDL files remain.

- [ ] **Step 3: Delete Binder/AIDL code and disable AIDL build feature**

Remove the three obsolete files and remove `buildFeatures.aidl = true` from Gradle. Keep XSharedPreferences configuration transport unchanged.

- [ ] **Step 4: Update documentation**

Document reverse request/callback IPC, package-visibility compatibility, nonce validation, bounded responses, and the requirement that Telegram be running for Refresh. Record the APK-verified `dispatchTouchEvent` three-second press-and-hold compatibility fix.

- [ ] **Step 5: Run contracts and commit**

```bash
python3 -m unittest discover -s tests -v
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
git add -A
git commit -m "refactor: remove visibility-sensitive Binder bridge"
```

### Task 6: Signed release build and real-device acceptance

**Files:**
- Modify: `docs/device-acceptance-2.0.0.md`
- Modify: `docs/superpowers/plans/2026-09-24-vector-xposed-migration.md`

**Interfaces:**
- Consumes: complete application and release pipeline.
- Produces: signed, inspected release APK plus sanitized device evidence.

- [ ] **Step 1: Run the complete local gate**

```bash
set -a
. /home/punzme/.hermes/credentials/telegram-chat-hider-release.env
set +a
export ANDROID_HOME=/home/punzme/.hermes/cache/scratch/toolchains/android-sdk
export JAVA_HOME=/home/punzme/.hermes/cache/scratch/toolchains/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
python3 -m unittest discover -s tests -v
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug testReleaseUnitTest lintRelease assembleRelease
./scripts/verify-reproducible-build.sh
./scripts/verify-release-apk.sh
```

Expected: every command exits 0; lint has no warnings; signature, metadata, scope, entrypoint, class packaging, and payload reproducibility pass.

- [ ] **Step 2: Install the exact signed APK**

Copy the APK to `laptop-kantor`, compare SHA-256 on both hosts, then run:

```text
adb -s ZP22224RNQ install -r telegram-chat-hider-2.0.0-rc.apk
```

Expected: `Success`.

- [ ] **Step 3: Verify sanitized runtime status**

Force-stop/reopen Telegram and verify logs contain no hook-unavailable, runtime-error, service-not-found, or crash lines. Confirm the module UI receives `bridge`, `dialogs`, `notifications`, and `reveal` statuses.

- [ ] **Step 4: Execute the manual acceptance matrix**

Verify:

1. catalog refresh works with no HMA/package-visibility exception;
2. main, archive, and custom folders hide selected dialogs;
3. hidden notifications are suppressed while visible notifications remain;
4. a three-second ActionBar hold reveals and another hold conceals;
5. two Telegram accounts use distinct keys;
6. restart resets reveal to concealed;
7. disabling module scope restores normal Telegram behavior.

- [ ] **Step 5: Record evidence and commit**

Update the sanitized device matrix and plan checkboxes without chat titles, IDs, message text, or raw logs.

```bash
git add docs/device-acceptance-2.0.0.md docs/superpowers/plans/2026-09-24-vector-xposed-migration.md
git commit -m "test: record Vector 2.0 device acceptance"
```

- [ ] **Step 6: Request whole-branch review**

Review from the pre-IPC base through `HEAD`, fix all Critical/Required findings, rerun Step 1, and commit fixes separately.

- [ ] **Step 7: Push and gate release**

Push `main`, require GitHub Actions green, then and only then mark the push/CI checklist complete. Do not create `v2.0.0` until every device scenario passes.
