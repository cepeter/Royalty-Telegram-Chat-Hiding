#!/usr/bin/env bash
set -euo pipefail
apk=${1:-app/build/outputs/apk/release/app-release.apk}
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
: "${ANDROID_HOME:?ANDROID_HOME must point to the pinned Android SDK}"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
aapt="$ANDROID_HOME/build-tools/36.0.0/aapt"
apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
"$apksigner" verify --verbose "$apk"
"$aapt" dump badging "$apk" | grep -F "package: name='io.github.cepeter.royalty' versionCode='17' versionName='3.0.5'"
python3 - "$apk" <<'PY'
import sys
import zipfile

expected = {
    "META-INF/xposed/java_init.list": b"io.github.cepeter.royalty.xposed.TelegramHook\n",
    "META-INF/xposed/scope.list": b"org.telegram.messenger\n",
    "META-INF/xposed/module.prop": b"minApiVersion=101\ntargetApiVersion=101\nstaticScope=true\n",
}
with zipfile.ZipFile(sys.argv[1]) as apk_file:
    for path, content in expected.items():
        if apk_file.read(path) != content:
            raise SystemExit(f"Unexpected modern Xposed resource: {path}")
    if "assets/xposed_init" in apk_file.namelist():
        raise SystemExit("Legacy Xposed entrypoint is packaged")
PY
manifest=$("$aapt" dump xmltree "$apk" AndroidManifest.xml)
if grep -Eq 'xposedmodule|xposedminversion|xposedsharedprefs|xposedscope' <<<"$manifest"; then
  echo 'Legacy Xposed manifest metadata is packaged' >&2
  exit 1
fi
if "$apkanalyzer" dex packages "$apk" | grep -Eq '^[PC] d[[:space:]].*io\.github\.libxposed\.api'; then
  echo 'Modern Xposed compile-only API classes were packaged in the APK' >&2
  exit 1
fi
sha256sum "$apk" | tee "$apk.sha256"
