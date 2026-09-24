# Multi-Surface Hidden Chats — Task Checklist

Status: **Approved on 2026-09-24; implementation may begin with PR 1**

## PR 1 — Compatibility foundation

- [ ] 1.1 Map exact official Telegram 12.10.4 APK classes, aliases, fields, signatures, ownership, and APK SHA-256.
- [ ] 1.2 Add paired item/metadata filtered-copy behavior with JVM tests.
- [ ] 1.3 Add explicit-account runtime object-to-`DialogKey` extraction and reflection probe.

### Checkpoint A

- [ ] Repository contracts pass.
- [ ] JVM tests and GitHub Android CI pass.
- [ ] Exact-device reflection probe passes.
- [ ] Human approves compatibility map.

## PR 2 — Search and global search

- [ ] 2.1 Filter recent/local/global/local-server/phone/group peer results.
- [ ] 2.2 Filter message/forum/public-post results with aligned metadata.
- [ ] 2.3 Apply conservative hints/category policy and degraded health reporting.

### Checkpoint B

- [ ] Local/global/recent/message/hints device matrix passes.
- [ ] Two-account and reveal/restart behavior passes.
- [ ] Repeated async searches do not crash or leak hidden results.

## PR 3 — Share and contact picker

- [ ] 3.1 Filter share dialog grid, recent targets, and rebuilt ID map.
- [ ] 3.2 Filter share search and add reveal-aware stale-selection guard.

### Checkpoint C

- [ ] Text, media, and forwarded-message share flows pass.
- [ ] Visible targets remain selectable and ordering is preserved.
- [ ] Search regression matrix remains green.

## PR 4 — New-group and contact invite

- [ ] 4.1 Map every contact/group/add-member/invite entry point on Telegram 12.10.4.
- [ ] 4.2 Filter normal user/contact/member adapter copies.
- [ ] 4.3 Filter local/global contact search without globally removing Telegram users.

### Checkpoint D

- [ ] New group, add member, invite contact, and username/name search pass.
- [ ] Hidden private users are absent; unrelated users remain visible.
- [ ] Group/channel semantics and exclusions are documented.

## PR 5 — Release acceptance

- [ ] 5.1 Run concealed/revealed/re-concealed/restart matrix across two accounts.
- [ ] 5.2 Bump package and release contracts to version 2.1.0 with a monotonic version code.
- [ ] 5.3 Update README, SECURITY, CHANGELOG, and 2.1.0 acceptance evidence only for passing surfaces.

### Final checkpoint

- [ ] All repository/JVM/lint/build checks pass in GitHub Actions.
- [ ] Exact signed APK passes full physical-device acceptance.
- [ ] Per-surface status is honest (`installed`, `missing`, `runtime_error`, or degraded detail).
- [ ] No Telegram-owned collection is mutated in place.
- [ ] Release compatibility claims match recorded evidence.
