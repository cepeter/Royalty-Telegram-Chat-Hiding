# Telegram 12.10.4 compatibility map

This map is the compatibility boundary for Royalty’s multi-surface filtering. Do not reuse these aliases for another Telegram build without repeating the probe.

## Verified APK identity

- Package: `org.telegram.messenger`
- Version: `12.10.4` (`70992`)
- Base APK SHA-256: `146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d`
- Signing certificate SHA-256: `49c1522548ebacd46ce322b6fd47f6092bb745d0f88082145caf35e14dcc38e1`
- Certificate subject: `CN=Nikolay Kudashov, OU=VK, O=VK, L=Saint-Petersburg`
- DEX files inspected: `classes.dex` through `classes5.dex`

Evidence was produced from the APK manifest, signing certificate, DEX descriptors, string ownership, and targeted JADX output. Decompiled Telegram code is not committed.

## Runtime surfaces

### Dialog search

| Source role | Runtime identity | Relevant members |
|---|---|---|
| `DialogsSearchAdapter` | `we.b0` extends `org.telegram.ui.Components.bm0`; live subclass `org.telegram.ui.Components.eo0` | account `r0:int`; primary search entry `U(int,String)`; refresh `eo0.l()`; `getItem(int)` → `J`; count → `h` |
| Search result + aligned names | fields `s:ArrayList`, `F:ArrayList` | local peer rows and display metadata |
| Message/forum/public rows | `x`, `G`, `H`, `I`, `J` | public posts, forum messages, messages, hashtags, sponsored peers |
| Recent rows | `s0`, `t0`, `u0`, `w0` | recent, filtered recent copies, and ID map |
| `RecentSearchObject` | `we.a0` | peer `a:TLObject`; date `b:int`; dialog ID `c:long` |
| `SearchAdapterHelper` | `we.n1` | account `m:int`; query methods `g` and `h` |
| Helper result collections | `d`, `e`, `g`, `j`, `k`, `l` | local-server, global, group, phone, local-search, local-recent |
| Helper result maps | `f`, `h`, `i` | global, group, and phone ID maps |

`DialogsSearchAdapter` creates the `we.u` helper subclass; `we.u` extends `we.n1`.

### Share sheet

| Source role | Runtime identity | Relevant members |
|---|---|---|
| `ShareAlert` | `org.telegram.ui.Components.wq0` | account inherited as `currentAccount`; selected-dialog map `T:z.f` |
| `ShareDialogsAdapter` | `org.telegram.ui.Components.oq0` | dialog list `d:ArrayList`; ID map `e:z.f`; refresh `E()` |
| Share dialog map | `z.f` | clear `b()`; insert `k(Object,long)` |
| `ShareSearchAdapter` | `org.telegram.ui.Components.sq0` | search list `d:ArrayList`; helper `e:qq0`; search `E(String)` |
| Share search row | `org.telegram.ui.Components.kq0` | dialog `a:TL_dialog`; peer `b:TLObject`; date `c:int`; name `d:CharSequence` |
| Topic adapter | `org.telegram.ui.Components.tq0` | topic list `f:ArrayList` |

`wq0.J`, `wq0.K`, and `wq0.L` own `oq0`, `tq0`, and `sq0` respectively.

### Contacts and invitations

| Source role | Runtime identity | Relevant members |
|---|---|---|
| `ContactsActivity` | name retained | account inherited as `currentAccount`; search adapter `r:mt`; normal adapter `d:nt` |
| Contact search adapter | `org.telegram.ui.mt` extends `we.g1` | local rows `d`; aligned names `e`; helper `f:we.n1`; phone rows `G` |
| Contact list adapter | `org.telegram.ui.nt` extends `we.d` | backing contacts in inherited structures; row access through inherited adapter methods |

The retained activity constructs `mt` and `nt` directly in `createView`. Search/global collections must be filtered in the adapter copy, not in Telegram’s shared contacts controller.

### Group creation and add-member flows

| Source role | Runtime identity | Relevant members |
|---|---|---|
| `GroupCreateActivity` | `org.telegram.ui.s70` | account inherited as `currentAccount`; adapter `v:q70` |
| `GroupCreateAdapter` | `org.telegram.ui.q70` | search rows `d`; aligned names `e`; helper `f:we.n1`; contact/member copy `r`; search `L(String)` |

`q70` owns its contact/member list, so filtering `q70.r` does not mutate Telegram’s global contacts collection.

## Installation rules

1. Resolve source names first, then only the aliases listed above.
2. Validate required fields and method descriptors before installing a hook.
3. Read the owning activity/adapter’s explicit account; never use `UserConfig.selectedAccount` for row keys.
4. Replace lists with filtered copies and preserve share-map identity through its verified API.
5. Reconcile Telegram-owned adapter rows by object identity (`==`), never value equality.
6. If any required member is missing, keep Telegram data visible and report the surface as `missing` or `runtime_error`.

## Probe status

- Static APK identity and DEX mapping: **passed**.
- JVM paired-copy and object-key contracts: implemented; GitHub Android CI is the executable gate.
- Physical-device reflection probe: required before Checkpoint A closes.

## Name-independent adapter refresh prerequisite (final integration fix)

The historical DEX table above establishes the listed surface aliases. It does **not** establish that the root RecyclerView method is literally named `notifyDataSetChanged` in the supported compiled APK. No supported APK/DEX was available during this final integration pass, and no new compiled method-name claim is made here.

The validated structural alternative is derived from the complete root `RecyclerView.Adapter` declaration and `AdapterDataObservable` in the [pinned Telegram RecyclerView source](https://github.com/DrKLO/Telegram/blob/9552e5541e1274b9557c9832b204dbfcaf44b3dc/TMessagesProj/src/main/java/androidx/recyclerview/widget/RecyclerView.java). The examined Adapter declaration begins at source line 6990; the observable begins at line 12251. These source facts were checked together:

| Property | Source evidence and runtime requirement |
|---|---|
| Declaring class | Abstract root Adapter directly extends Object; concrete host adapters inherit through it. |
| Observable field | The root declares one private final nonstatic AdapterDataObservable field. Its type directly extends the stable framework class `android.database.Observable`. |
| No-argument methods declared by root | `getItemCount():int`, `hasStableIds():boolean`, `hasObservers():boolean`, and the single public nonstatic `notifyDataSetChanged():void`. Other root void methods take arguments. Object methods and concrete-subclass methods are excluded from root uniqueness. |
| Invocation contract | The root no-argument void body calls the observable's change notification, which visits registered observers. Reflective invocation of the resolved root Method preserves normal virtual dispatch to host overrides. |
| Rejection | Missing root/field/method, multiple matching observable fields, multiple public instance no-argument void methods, or abstract/synthetic/bridge candidates reject installation. No guessed alias is attempted. |
| Cache and use | The resolved Method is cached per concrete class. The same resolver verifies install prerequisites and supplies every observed adapter's reveal/conceal refresh invocation. |

`PinnedRecyclerAdapterShape` is a source-derived descriptor fixture: it preserves the root inheritance/field shape, all root no-argument return descriptors and the parameterized overload families, while deliberately renaming methods. Its names are **not** APK aliases. Tests reject ambiguity/static-only decoys, verify caching and virtual dispatch, and exercise reveal/re-conceal refresh on observed adapters. This proves the resolver against the validated structural contract, not that any uninspected obfuscator output preserves that contract. Runtime validation therefore remains mandatory and rejects altered/ambiguous shapes safely; the exact-APK reflection and visual-redraw acceptance gates remain pending.
