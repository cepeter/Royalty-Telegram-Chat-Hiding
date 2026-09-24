# Package-Visibility-Safe Catalog IPC Design

**Date:** 2026-09-24  
**Status:** Approved for implementation

## Problem

The injected Telegram process currently binds to an exported service in `io.github.cepeter.royalty`. On Android 11 and newer, package-visibility filtering can make that component undiscoverable to Telegram even when the service is exported. Android then returns `false` from `bindService` and reports the component as not found.

The production design must work without changing Telegram's manifest, requiring privacy-tool exceptions, restoring root-owned files/sockets, or weakening catalog validation.

## Goals

- Preserve automatic dialog discovery and hook-status reporting.
- Support Android 8.1 through Android 15.
- Work when Telegram cannot discover the module package.
- Preserve account-aware catalog storage and the existing configuration UI.
- Transmit no message text and no data beyond account, dialog ID, bounded display title, and bounded hook status.
- Fail open without affecting Telegram when IPC is unavailable.

## Non-goals

- Background catalog synchronization while Telegram is not running.
- Search, share picker, contact picker, or new-group filtering.
- A general-purpose cross-application RPC framework.

## Architecture

Communication is reversed. The module app can address Telegram because its manifest declares Telegram in `<queries>`. Telegram no longer resolves any component in the module package.

### Telegram process

The Xposed entrypoint creates a process-lifetime `CatalogSnapshotStore`. Dialog and status hooks update immutable, bounded snapshots in this store.

During `ApplicationLoader.onCreate`, injected code dynamically registers an exported broadcast receiver for a private request action. On Android 13 and newer it explicitly uses `Context.RECEIVER_EXPORTED`; older Android versions use the compatible registration overload.

A request is accepted only when the sender holds the module app's signature-level request permission and supplies a callback. Invalid or malformed requests are ignored and logged without data.

Telegram responds through the supplied token. It sends one catalog result per account and one status result. This keeps each transaction below Binder's size limit. Every response copies arrays and applies the same catalog/status limits used by persistence.

### Module process

`MainActivity` owns a `CatalogRequestClient`. On resume and when Refresh is pressed, it:

1. generates a cryptographically random 128-bit nonce;
2. stores that nonce with a short expiry;
3. creates an exact-component mutable broadcast `PendingIntent` targeting a non-exported result receiver in the module package;
4. sends a package-targeted request broadcast to `org.telegram.messenger`;
5. refreshes the UI as bounded results arrive.

The base callback intent contains the nonce. Telegram can add result extras but cannot redirect the exact callback component. The result receiver rejects expired/unknown nonces, malformed result types, invalid accounts, mismatched arrays, overlong status tokens, and oversized input before writing through `CatalogRepository`.

The callback token is canceled after the response window. Repeated account results for the same valid nonce are allowed so multi-account Telegram installations can respond without combining all catalogs into one transaction.

## Security properties

- A signature-level request permission authenticates the sender without Telegram querying PackageManager for the module.
- The exact callback component and unpredictable nonce limit callback use to the active request.
- The request broadcast is package-targeted to Telegram, preventing unrelated receivers from obtaining the callback token.
- The module's result receiver is not directly exported.
- All payloads are sanitized again in the module process; transport validation is not trusted as persistence validation.
- No root file, Unix socket, world-writable path, or persistent exported Binder service remains.

## Lifecycle and failure behavior

If Telegram is not running, no receiver handles the request. The app retains the last catalog and displays guidance to open Telegram and refresh. Timeouts do not clear existing selections.

Telegram keeps only the latest catalog per account and latest status per hook. Process restart resets this memory; the next dialog-list access repopulates it. Hook and receiver failures are caught, logged without identifiers/titles, and reported as unavailable when possible.

## Reveal compatibility fix

Telegram 12.8.3's official build obfuscates the ActionBar and DialogsActivity class names. The reveal hook supports source-build names plus aliases verified directly from the installed 12.8.3 APK, hooks `dispatchTouchEvent` so story-header children cannot consume the gesture first, verifies the active fragment through `LaunchActivity`'s `ActionBarLayout`, and toggles reveal when the user releases after a three-second hold.

## Removal and migration

Remove `ICatalogService.aidl`, `CatalogService`, and the old binding `CatalogPublisher`. Keep `CatalogRepository` as the persistence boundary. Existing catalog preferences and hidden-dialog configuration remain compatible.

## Verification

Automated tests must cover:

- immutable snapshot copies and per-account replacement;
- request authentication by callback creator package;
- nonce generation, expiry, replay behavior, and cancellation;
- malformed/oversized account, title, and status payloads;
- Android-version-specific receiver and PendingIntent flags;
- exact Telegram request targeting and exact module callback targeting;
- absence of the exported catalog service and AIDL surface;
- the `onInterceptTouchEvent` runtime contract;
- full debug/release tests, lint, signed APK inspection, and payload reproducibility.

Real-device acceptance must verify catalog refresh without a package-visibility exception, all hook statuses, main/archive/custom-folder filtering, notification suppression, five-tap reveal/conceal, two-account isolation, restart reset, and disabled-module restoration before stable release.
