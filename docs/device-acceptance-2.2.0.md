# Device acceptance — Royalty 2.2.0

## Release identity

- Royalty version: `2.2.0` (`versionCode 10`)
- Telegram package: `org.telegram.messenger`
- Telegram version: `12.10.4` (`versionCode 70992`)
- Telegram APK SHA-256: `146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d`
- Telegram signing certificate SHA-256: `49c1522548ebacd46ce322b6fd47f6092bb745d0f88082145caf35e14dcc38e1`

## Automated acceptance

| Gate | Result |
|---|---|
| Repository contracts | Pass |
| JVM unit tests | Pass |
| Android lint | Pass |
| Debug APK assembly | Pass |
| Telegram 12.10.4 DEX surface probe | Pass: 13 mapped classes |
| Release signing, reproducibility, and APK-content checks | Required by the tag workflow |
| Per-surface fail-open status reporting | Covered by contract tests |
| Telegram-owned list mutation | No in-place mutation in supported filtering paths |

Merged implementation pull requests:

- Compatibility foundation: [#17](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/pull/17)
- Search and global search: [#18](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/pull/18)
- Share targets: [#19](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/pull/19)
- Group and contact pickers: [#20](https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/pull/20)

## Physical-device acceptance

Not executed before publication. On 2026-09-24, both configured Windows ADB hosts returned an empty device list. No concealed/revealed/re-concealed, two-account, restart, share-send, or group/contact interaction result is claimed here.

The hooks therefore retain fail-open behavior: unresolved classes, unknown row types, or runtime reflection failures leave Telegram content visible and publish `missing`, `runtime_error`, or degraded status details instead of crashing Telegram.

Group/contact semantics: hidden private users and explicitly hidden signed chat IDs are removed from mapped picker rows; synthetic letter headers, invite-by-link actions, phone-book-only contacts without a Telegram user ID, and unknown future row types remain visible. Royalty does not globally delete users or chats from Telegram controllers.

## Required follow-up matrix

Run on the exact Telegram build above when a device is connected:

1. Concealed, revealed, re-concealed, and Telegram-restart behavior across two accounts.
2. Local/global/recent/message/forum/public-post search, including repeated async queries.
3. Text, media, and forwarded-message share targets and stale-selection behavior.
4. New group, add member, contact invite, username search, and name search.
5. Signed APK upgrade, Vector activation, catalog refresh, and per-surface status cards.

## Wave 4A account binding and diagnostics acceptance (pending device)

The following checks remain **pending physical-device execution** on the exact supported Telegram build. JVM and static checks do not establish that the installed Telegram APK exposes the expected runtime members or that every UI surface redraws correctly.

1. Sign into two accounts with distinguishable chats. Refresh Royalty; confirm both account labels, account filter, per-row account/owner context, and selected count. Hide one chat in each account, save, restart Telegram, and verify list, search, share, contacts and notification behavior for each owner.
2. On a fresh Royalty upgrade with legacy hidden keys, verify they are shown as needing review and are not concealed until explicitly rebound to the displayed owner. Check Remove selection and Discard, then rebind and save.
3. Log out of one account and refresh after Telegram finishes loading configuration. Confirm an empty or absent slot is distinguished from an incomplete inventory. The old owner's hidden keys must remain recoverable but have no runtime effect on the replacement account.
4. Replace an account in the same slot while Royalty has an edited draft. Before refreshing Royalty, verify old keys do not conceal the new owner's content. Refresh, review binding, and confirm a stale draft cannot silently bind old keys to the new owner on Save.
5. Use an active account with no current dialog rows. Refresh and verify the account still appears in the account filter. Test search plus account plus hidden-only views, scoped Select matching, Undo, Save, and Discard; check count across filter changes.
6. Trigger a fresh check while Telegram is closed and while the module is disabled. Inspect checking/last-known age, the exact response error, per-surface status/detail, installed and supported versions, and the manual-check guidance. A missing owner inventory must degrade the overall privacy-health indication. A fresh check must not imply physical-device validation of untested hooks.
