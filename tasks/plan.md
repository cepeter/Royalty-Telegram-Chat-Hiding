# Implementation Plan: Multi-Surface Hidden Chats

Status: **Approved on 2026-09-24 — implementation may proceed through the planned PR sequence**

## Overview

Extend Royalty so the same account-aware hidden-dialog configuration also controls Telegram 12.10.4 search/global search, share/contact pickers, and new-group/member-invite screens. Implement each surface as a separate, reviewable pull request. Preserve Telegram-owned collections, XSharedPreferences hot reload, process-memory reveal state, notification behavior, and fail-open runtime safety.

## Assumptions

1. One hidden-dialog selection applies to every supported visual surface; there are no per-surface toggles.
2. Temporary reveal bypasses all visual filtering, while notification suppression remains independent.
3. Search scope includes recent/local/global peer results and message results belonging to hidden dialogs.
4. Contact/group screens hide users corresponding to hidden private dialogs; group/channel keys are ignored where the screen is user-only.
5. Official Telegram 12.10.4 (`org.telegram.messenger`) is the only release target for this initiative.
6. Unknown rows, unresolved classes, and extractor errors fail open and report per-surface status instead of crashing Telegram.

## Capability Map

| Module id | Responsibility | Depends on |
|---|---|---|
| `compat-foundation` | Exact APK mapping, shared filtering contracts, reflection boundaries, status names | — |
| `search-hiding` | Recent/local/global/phone/group/message/public-post search filtering | `compat-foundation` |
| `share-hiding` | Share grid, recent targets, local/global share search, stale-selection guard | `compat-foundation`, `search-hiding` helper contract |
| `contact-hiding` | New-group, add-member, invite-contact, local/global user search | `compat-foundation` |
| `release-acceptance` | Cross-surface reveal, account isolation, documentation and release gates | All modules |

Build order:

```text
compat-foundation → search-hiding → share-hiding → contact-hiding → release-acceptance
```

`share-hiding` follows search because both use Telegram's `SearchAdapterHelper`. `contact-hiding` may be implemented in parallel after the helper contract is frozen, but it merges after search to avoid conflicting reflection mappings.

## Architecture Decisions

### 1. Surface modules, not a larger `TelegramHook`

Keep `TelegramHook` as bootstrap/orchestration. Place new behavior in delete-friendly classes:

```text
xposed/SearchHook.java
xposed/ShareHook.java
xposed/ContactHook.java
xposed/TelegramObjectKey.java
```

Each installer reports one independent status (`search`, `share`, `contacts`) through the existing `CatalogSnapshotStore` path.

### 2. Filter at presentation boundaries

Never mutate Telegram controller/storage collections. For method results, return filtered copies. For adapters that require several private lists during one method call:

1. snapshot original references;
2. construct filtered copies, including parallel metadata lists;
3. swap only for the duration of the hooked presentation method;
4. restore every reference in `afterHookedMethod`, including exception paths.

Do not call `clear`, `remove`, or `removeIf` on Telegram-owned lists.

### 3. Shared key extraction

`TelegramObjectKey` converts known runtime objects into `DialogKey` without compile-time Telegram dependencies:

- dialog: field `id`;
- message: `getDialogId()`;
- user: positive `id`;
- chat/channel: negative `id`;
- recent/search wrapper: nested `did`, `dialog.id`, or wrapped object;
- unknown/synthetic row: no key, therefore visible.

Every conversion receives the adapter/controller account explicitly. Never use `UserConfig.selectedAccount` as a substitute for the owning adapter account.

### 4. Parallel-list integrity

Search result names and related metadata must remain index-aligned with item lists. Extend the pure core filtering contract to filter item/metadata pairs together and reject mismatched source sizes by returning untouched copies (fail open).

### 5. Reveal and configuration semantics

Every rendering/filter hook reads `CONFIG.current()` and `REVEALED.get()` at invocation time. No new persistent state is introduced. The existing three-second ActionBar press-and-hold remains unchanged.

### 6. Exact-version compatibility

The documented source commit `9552e554…` identifies itself as Telegram 12.10.3, not 12.10.4. It may guide surface discovery only. Release aliases, fields, signatures, and inner-class ownership must be derived from the exact official Telegram 12.10.4 APK. Do not commit the APK or decompiled proprietary artifacts.

## Commands

Repository contracts:

```bash
python3 -m unittest discover -s tests -v
```

Android tests and lint:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Release validation remains tag-only through GitHub Actions:

```bash
git push origin <feature-branch>
gh pr checks <pr-number> --watch
```

Device evidence uses ADB from the connected workstation; exact commands belong in the compatibility map and device-acceptance document. No local SDK/JDK is required on the VPS.

## Pull Request Sequence

### PR 1 — Compatibility foundation

Goal: freeze exact Telegram 12.10.4 runtime contracts before feature code.

#### Task 1.1: Map exact APK surfaces

**Description:** Extract the installed official Telegram 12.10.4 APK through ADB, record package/version/hash, and map source names to obfuscated runtime aliases for search, share, contacts, group creation, member invite, and relevant inner adapters.

**Acceptance criteria:**
- [ ] Mapping identifies class aliases, method signatures, owning account fields, item fields, and parallel lists.
- [ ] Mapping distinguishes required hooks from optional/degraded hooks.
- [ ] No APK or decompiled Telegram code is committed.

**Verification:**
- [ ] Reflection probe resolves every required class/signature on the device without installing hooks.
- [ ] `docs/telegram-12.10.4-surface-map.md` records APK SHA-256 and evidence.

**Files likely touched:**
- `docs/telegram-12.10.4-surface-map.md`
- `tests/test_xposed_hook_contract.py`

**Estimated scope:** Small

#### Task 1.2: Add paired-copy core filtering

**Description:** Extend pure filtering logic for item lists with index-aligned metadata lists. Keep existing `DialogFilter.filteredCopy` behavior unchanged.

**Acceptance criteria:**
- [ ] Hidden items and matching metadata are removed together.
- [ ] Reveal bypass returns equivalent copies without filtering.
- [ ] Unknown keys and mismatched parallel-list lengths fail open.

**Verification:**
- [ ] JVM unit tests cover account isolation, order, duplicates, unknown rows, mismatch, and reveal.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/core/DialogFilter.java`
- `app/src/test/java/io/github/cepeter/telegramhider/core/DialogFilterTest.java`

**Estimated scope:** Small

#### Task 1.3: Isolate runtime key extraction

**Description:** Add reflection-only conversion from Telegram runtime objects/wrappers to `DialogKey`, with explicit account input and no state mutation.

**Acceptance criteria:**
- [ ] Dialog, message, user, chat/channel, recent, and share-search wrappers are supported from the compatibility map.
- [ ] Synthetic/unknown rows return no key and remain visible.
- [ ] Extractor failures do not escape into Telegram.

**Verification:**
- [ ] Contract tests enforce explicit account use and reject `selectedAccount` fallback.
- [ ] Device reflection probe validates mapped extractors.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramObjectKey.java`
- `tests/test_xposed_hook_contract.py`
- `docs/telegram-12.10.4-surface-map.md`

**Estimated scope:** Medium

### Checkpoint A — Foundation

- [ ] Repository contracts pass.
- [ ] JVM tests pass.
- [ ] GitHub Android CI passes.
- [ ] Device reflection probe passes on exact Telegram 12.10.4.
- [ ] Human reviews the surface map before PR 2.

### PR 2 — Search and global-search hiding

Goal: prevent hidden dialogs from appearing in every mapped search section without damaging Telegram caches.

#### Task 2.1: Filter peer search sections

**Description:** Add `SearchHook` for recent/local results and `SearchAdapterHelper` global, local-server, phone, and group results. Use filtered copies and preserve order.

**Acceptance criteria:**
- [ ] Hidden peers do not appear in recent, local, global, phone, or group search sections.
- [ ] Visible and synthetic rows retain their original order and type.
- [ ] Multi-account results use the owning adapter account.

**Verification:**
- [ ] Source contract proves no destructive list calls.
- [ ] Device tests cover private chats, groups/channels, two accounts, and reveal bypass.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/SearchHook.java`
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

#### Task 2.2: Filter message and public-post results

**Description:** Filter search messages, forum messages, and public posts by `getDialogId()`, preserving paired display-name/highlight metadata.

**Acceptance criteria:**
- [ ] Message results from hidden dialogs are absent.
- [ ] Visible message results, pagination rows, and section headers remain correct.
- [ ] Async result replacement cannot reintroduce hidden entries.

**Verification:**
- [ ] Paired-list unit tests cover repeated async refreshes.
- [ ] Device test searches exact message text from hidden and visible dialogs.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/SearchHook.java`
- `app/src/test/java/io/github/cepeter/telegramhider/core/DialogFilterTest.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

#### Task 2.3: Handle search hints conservatively

**Description:** Hide the hints/category carousel while concealed if safe click-position remapping cannot be proven. Restore normal hints when revealed or when no configured hidden dialog is active for the account.

**Acceptance criteria:**
- [ ] No hidden peer appears in hints.
- [ ] Hint click positions cannot select a different peer than displayed.
- [ ] Optional hint-hook failure marks search degraded, not Telegram failed.

**Verification:**
- [ ] Contract tests classify required versus optional search hooks.
- [ ] Device tests exercise hints before/after conceal and reveal.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/SearchHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Small

### Checkpoint B — Search

- [ ] Search hook reports installed or an honest degraded status.
- [ ] Local/global/recent/message/hints acceptance rows pass.
- [ ] Restart resets reveal to concealed.
- [ ] No Telegram crash during repeated async searches.
- [ ] PR 2 merges before share implementation begins.

### PR 3 — Share and contact-picker hiding

Goal: remove hidden destinations from Telegram's share UI and prevent stale hidden targets from being selected.

#### Task 3.1: Filter share dialog grid and recent targets

**Description:** Add `ShareHook` around mapped share-adapter refresh/fetch methods. Filter adapter-owned dialogs and rebuild any parallel ID map from the filtered copy.

**Acceptance criteria:**
- [ ] Hidden private/group/channel dialogs are absent from the share grid and recents.
- [ ] Dialog map and list contain the same IDs.
- [ ] Telegram controller lists remain untouched.

**Verification:**
- [ ] Source contract rejects mutation of source lists/maps.
- [ ] Device tests share text, media, and forwarded messages.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/ShareHook.java`
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

#### Task 3.2: Filter share search and guard stale selection

**Description:** Filter local/global share search using the shared search-helper contract. Before the final selection callback, reject a target that became hidden after rendering unless temporary reveal is active.

**Acceptance criteria:**
- [ ] Hidden targets remain absent after async share searches.
- [ ] A stale concealed target cannot be selected or sent to.
- [ ] Reveal mode permits the same target normally.

**Verification:**
- [ ] Device test changes configuration while share UI is open.
- [ ] Runtime errors fail open only when no safe identity can be resolved and are reported as `runtime_error`.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/ShareHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

### Checkpoint C — Share

- [ ] Search and share tests pass together.
- [ ] Share grid/search/recents and stale-selection rows pass on device.
- [ ] Visible destinations remain selectable.
- [ ] No mutation or ordering regression is observed.

### PR 4 — New-group and contact-invite hiding

Goal: hide users corresponding to hidden private dialogs from user-centric creation/invite screens.

#### Task 4.1: Inventory and freeze contact screen coverage

**Description:** Confirm exact 12.10.4 adapters and entry points for `ContactsActivity`, `GroupCreateActivity`, add-member, and invite-member flows. Record which screens list only users versus chats.

**Acceptance criteria:**
- [ ] Every user-facing entry point has a mapped adapter and account source.
- [ ] Unsupported/synthetic rows are documented and remain visible.
- [ ] Group/channel keys are explicitly ignored on user-only screens.

**Verification:**
- [ ] Device navigation checklist reaches every mapped entry point.

**Files likely touched:**
- `docs/telegram-12.10.4-surface-map.md`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Small

#### Task 4.2: Filter normal contact/member lists

**Description:** Add `ContactHook` that filters adapter-owned user/contact copies using positive user IDs and the owning account.

**Acceptance criteria:**
- [ ] Users with hidden private dialogs are absent from normal contact/member lists.
- [ ] Self, bots, service users, headers, and non-user rows retain Telegram's existing behavior.
- [ ] Existing ignore/selected-user semantics remain intact.

**Verification:**
- [ ] Unit/contract tests cover private-user mapping and non-user pass-through.
- [ ] Device tests cover new group, add member, and invite contact.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/ContactHook.java`
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/TelegramHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

#### Task 4.3: Filter local/global contact search

**Description:** Filter contact/group-create search results and shared server helper results without affecting unrelated search contexts.

**Acceptance criteria:**
- [ ] Hidden private-chat users are absent from local and global contact search.
- [ ] The filter is scoped to mapped contact/group adapters and does not globally remove users from Telegram.
- [ ] Reveal mode restores those users in supported screens.

**Verification:**
- [ ] Device tests cover name and username search in each entry point.
- [ ] Account-two user remains visible when only account one is hidden.

**Files likely touched:**
- `app/src/main/java/io/github/cepeter/telegramhider/xposed/ContactHook.java`
- `tests/test_xposed_hook_contract.py`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Medium

### Checkpoint D — Contacts

- [ ] All contact/group/member flows pass on device.
- [ ] Search/share regression matrix remains green.
- [ ] Group/channel semantics are documented accurately.
- [ ] `contacts` status is independent from dialogs/search/share.

### PR 5 — Release acceptance and documentation

#### Task 5.1: Cross-surface privacy regression

**Description:** Run the full device matrix for concealed, revealed, re-concealed, and restarted states across two accounts.

**Acceptance criteria:**
- [ ] A hidden chat/user is absent from every supported surface while concealed.
- [ ] Temporary reveal restores supported visual surfaces and restart conceals again.
- [ ] Visible chats, notifications, selection, ordering, and pagination remain correct.

**Verification:**
- [ ] Evidence records APK hash, Telegram version, framework version, device/API, and hook statuses.
- [ ] Telegram starts repeatedly without Java/native crash.

**Files likely touched:**
- `docs/device-acceptance-2.1.0.md`
- `CHANGELOG.md`

**Estimated scope:** Small

#### Task 5.2: Prepare version 2.1.0

**Description:** Bump the application to version 2.1.0 with the next monotonic version code and update packaging contracts before creating the release tag.

**Acceptance criteria:**
- [ ] `versionName` is `2.1.0` and `versionCode` is greater than 7.
- [ ] Release verification expects the same package, version name, and version code.
- [ ] Existing 2.0.0 release evidence remains unchanged.

**Verification:**
- [ ] Package contracts and signed release workflow pass from the exact release commit.

**Files likely touched:**
- `app/build.gradle.kts`
- `tests/test_android_package_contract.py`
- `scripts/verify-release-apk.sh`

**Estimated scope:** Small

#### Task 5.3: Update public compatibility claims

**Description:** Update README and security documentation only after device evidence exists for version 2.1.0.

**Acceptance criteria:**
- [ ] Search/share/contact rows change from unsupported to supported only when their required tests pass.
- [ ] Telegram source/APK version claims are accurate and distinguish source guidance from APK verification.
- [ ] Known exclusions remain explicit.

**Verification:**
- [ ] Documentation contract tests pass.
- [ ] Release notes match actual supported behavior.

**Files likely touched:**
- `README.md`
- `SECURITY.md`
- `CHANGELOG.md`
- `docs/device-acceptance-2.1.0.md`

**Estimated scope:** Small

## Global Acceptance Matrix

Each supported surface must pass all applicable states:

| Dimension | Required cases |
|---|---|
| Visibility | concealed, revealed, re-concealed, process restart |
| Account | account 1 hidden, account 2 unaffected |
| Peer | private user, basic group, channel/supergroup, synthetic row |
| Result source | local, recent, global/server, message, async refresh |
| Configuration | empty set, one hidden key, multiple keys, config change while UI is open |
| Failure | optional class missing, extractor failure, malformed row, hook runtime error |

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Obfuscated 12.10.4 aliases differ from source | High | Exact APK map and reflection probe before each feature hook |
| Search/share parallel lists lose alignment | High | Paired-copy core contract and mismatch fail-open tests |
| Async server results reintroduce hidden rows | High | Filter every presentation invocation, not only search completion callbacks |
| Telegram-owned cache mutation breaks reveal | High | Copy-only policy plus source contract rejecting destructive list operations |
| Account leakage | High | Explicit owner account in every extractor; two-account device tests |
| Broad helper hook affects unrelated screens | High | Scope by owning adapter/context; contact slice verifies no global removal |
| Stale share selection sends to hidden target | High | Final selection guard honoring reveal state |
| Optional hook failure gives false confidence | Medium | Per-surface required/optional health and honest degraded status |
| `TelegramHook` becomes monolithic | Medium | One class per surface with bootstrap-only integration |
| Local VPS lacks Android toolchains | Low | Build through required GitHub Actions checks; use workstation ADB for device probes |

## Boundaries

### Always

- Work on feature branches and merge only through protected `main` pull requests.
- Add failing tests before each behavior change.
- Preserve Telegram-owned state and fail open on unknown objects.
- Verify exact PR head through GitHub Actions and physical-device acceptance.
- Record per-surface hook status and runtime errors.

### Ask first

- Add a new dependency.
- Add per-surface user settings.
- Change hidden-dialog persistence format.
- Expand support beyond official Telegram 12.10.4.
- Suppress hints or entire sections beyond the policies above.

### Never

- Commit Telegram APK/decompiled artifacts, signing secrets, or user chat data.
- Mutate Telegram controller/storage lists in place.
- Use `UserConfig.selectedAccount` when an owning account is available.
- Claim a surface supported before device acceptance passes.
- Reintroduce five-tap reveal or disable notification suppression during reveal.

## Parallelization

After Checkpoint A:

- Search contract tests and device test scripting may proceed in parallel.
- Contact entry-point inventory may proceed while share code is implemented.
- Search and share production hooks should remain sequential until the shared helper contract is stable.
- Documentation can be drafted in parallel but support claims merge only after acceptance evidence.

## Rollback Strategy

Each surface is isolated behind its installer. If a new Telegram alias fails or a surface regresses:

1. remove that installer call or revert its PR;
2. retain dialogs, notifications, reveal, and previously accepted surfaces;
3. report the removed surface as `missing`/unsupported;
4. publish no broadened compatibility claim.

No migration rollback is required because this initiative adds no persistent schema.

## Approval Gate

Implementation must not start until the human approves:

- capability boundaries and build order;
- all-surface hidden-selection semantics;
- conservative hints policy;
- stale share-selection guard;
- user-only semantics for group/contact screens.
