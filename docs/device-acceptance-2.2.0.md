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
