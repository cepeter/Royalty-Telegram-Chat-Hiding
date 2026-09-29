# Release integrity and rollback

`version.properties` is the single source of Royalty's `versionName` and
`versionCode`. Both the Android build and signed APK verifier read it. The
version remains `3.0.5` / code `17` until the planned feature release.

The verifier checks the actual APK package and version, its production signing
certificate, modern Xposed resources, manifest, and dex package list. Failed
inspection produces neither a release metadata file nor a SHA-256 manifest.
On a tag build, the tag must be exactly `v<versionName>`. A manual main-branch
run produces a signed acceptance artifact without publishing it.

The publication job serializes release creation and, inside that serialized
section, compares the staged version code with every applicable published
release. A release with missing or malformed metadata, or a failed GitHub API
lookup, blocks publication. Prior releases and tags remain available for
rollback. The public v3.0.5 release is the verified legacy bootstrap; later
releases carry `royalty-v<version>.apk.release.json` beside the APK and checksum.
Older pre-bootstrap versions are retained, but their metadata is not used as
the comparison baseline.

## Production signer evidence

The pinned Royalty certificate came from the actual published v3.0.5 APK,
not from Telegram. The release asset URL is
<https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/download/v3.0.5/royalty-v3.0.5.apk>.
The GitHub Actions tag run `36254556155`, artifact `10910094621`, contained a
byte-identical APK. The verified APK SHA-256 was
`7f17e0694751f4a2440cf715054b3815ca669255fa9a7e2c703807551204acde`.
`apksigner verify --verbose --print-certs` exited 0, reported one signer,
verified APK Signature Scheme v2 and v3, and reported signer #1 certificate
SHA-256 `1b2045af07e41823df288b75355314cd18ccf4926daa13ac84b8b38493bd573f`.
The published APK's `aapt dump badging` reported package
`io.github.cepeter.royalty`, version `3.0.5`, code `17`.

Full retrieval and command output were recorded in the controller's
`royalty-v3.0.5-signing-evidence.txt` on 2026-09-29. Local mock-script tests
cover release decisions; full Android Gradle and production environment checks
remain CI gates. Physical-device acceptance remains pending.
