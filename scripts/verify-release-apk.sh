#!/usr/bin/env bash
set -euo pipefail
apk=${1:-app/build/outputs/apk/release/app-release.apk}
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
: "${ANDROID_HOME:?ANDROID_HOME must point to the pinned Android SDK}"
apksigner="$ANDROID_HOME/build-tools/35.0.0/apksigner"
aapt="$ANDROID_HOME/build-tools/35.0.0/aapt"
apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
"$apksigner" verify --verbose "$apk"
"$aapt" dump badging "$apk" | grep -F "package: name='io.github.cepeter.royalty' versionCode='8' versionName='2.1.0'"
[[ "$(python3 -c 'import sys, zipfile; print(zipfile.ZipFile(sys.argv[1]).read("assets/xposed_init").decode().strip())' "$apk")" == "io.github.cepeter.royalty.xposed.TelegramHook" ]]
manifest=$("$aapt" dump xmltree "$apk" AndroidManifest.xml)
grep -Fq 'xposedmodule' <<<"$manifest"
grep -Fq 'xposedminversion' <<<"$manifest"
grep -Fq 'xposedsharedprefs' <<<"$manifest"
grep -Fq 'xposedscope' <<<"$manifest"
grep -Fq 'org.telegram.messenger' <<<"$manifest"
if "$apkanalyzer" dex packages "$apk" | grep -Eq '^[PC] d[[:space:]].*de\.robv\.android\.xposed'; then
  echo 'Xposed API classes were packaged in the APK' >&2
  exit 1
fi
sha256sum "$apk" | tee "$apk.sha256"
