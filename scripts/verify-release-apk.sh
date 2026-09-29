#!/usr/bin/env bash
set -euo pipefail
apk=${1:-app/build/outputs/apk/release/app-release.apk}
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
: "${ANDROID_HOME:?ANDROID_HOME must point to the pinned Android SDK}"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
aapt="$ANDROID_HOME/build-tools/36.0.0/aapt"
apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
metadata_file="$(dirname "$0")/../version.properties"
rm -f "$apk.sha256" "$apk.release.json"
version_values=$(python3 - "$metadata_file" <<'PY'
import pathlib
import re
import sys
lines = pathlib.Path(sys.argv[1]).read_text().splitlines()
values = {}
for line in lines:
    match = re.fullmatch(r'(versionName|versionCode)=([^=]+)', line)
    if not match or match.group(1) in values:
        raise SystemExit('Invalid version.properties')
    values[match.group(1)] = match.group(2)
if set(values) != {'versionName', 'versionCode'} or not re.fullmatch(r'\d+\.\d+\.\d+', values['versionName']) or not re.fullmatch(r'[1-9]\d*', values['versionCode']):
    raise SystemExit('Invalid version.properties')
print(values['versionName'])
print(values['versionCode'])
PY
) || exit 1
readarray -t versions <<< "$version_values"
version_name=${versions[0]}
version_code=${versions[1]}
if ! signature=$("$apksigner" verify --verbose --print-certs "$apk"); then
  echo 'APK signature verification failed' >&2
  exit 1
fi
expected_signer=$(<"$(dirname "$0")/../release-signing-cert.sha256")
if [[ $(grep -Ec '^Number of signers: 1$' <<< "$signature") != 1 ]] ||
   [[ $(grep -Ec '^Signer #[0-9]+ certificate SHA-256 digest:' <<< "$signature") != 1 ]] ||
   ! grep -Fqx "Signer #1 certificate SHA-256 digest: $expected_signer" <<< "$signature"; then
  echo 'Unexpected APK signer identity' >&2
  exit 1
fi
if ! badging=$("$aapt" dump badging "$apk"); then
  echo 'APK badging inspection failed' >&2
  exit 1
fi
if ! python3 - "$version_code" "$version_name" "$badging" <<'PYVERSION'
import re
import sys
lines = [line for line in sys.argv[3].splitlines() if line.startswith("package:")]
if len(lines) != 1:
    raise SystemExit(1)
match = re.match(r"^package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'(?: |$)", lines[0])
raise SystemExit(0 if match and match.groups() == ("io.github.cepeter.royalty", sys.argv[1], sys.argv[2]) else 1)
PYVERSION
then
  echo 'APK package or version does not match version.properties' >&2
  exit 1
fi
if [[ ${GITHUB_REF_TYPE:-} == tag && ${GITHUB_REF_NAME:-} != "v$version_name" ]]; then
  echo "Release tag does not match inspected APK version: ${GITHUB_REF_NAME:-}" >&2
  exit 1
fi
python3 - "$apk" <<'PY'
import sys
import zipfile
expected = {
    'META-INF/xposed/java_init.list': b'io.github.cepeter.royalty.xposed.TelegramHook\n',
    'META-INF/xposed/scope.list': b'org.telegram.messenger\n',
    'META-INF/xposed/module.prop': b'minApiVersion=101\ntargetApiVersion=101\nstaticScope=true\n',
}
with zipfile.ZipFile(sys.argv[1]) as apk_file:
    for path, content in expected.items():
        if apk_file.read(path) != content:
            raise SystemExit(f'Unexpected modern Xposed resource: {path}')
    if 'assets/xposed_init' in apk_file.namelist():
        raise SystemExit('Legacy Xposed entrypoint is packaged')
PY
if ! manifest=$("$aapt" dump xmltree "$apk" AndroidManifest.xml); then
  echo 'APK manifest inspection failed' >&2
  exit 1
fi
if grep -Eq 'xposedmodule|xposedminversion|xposedsharedprefs|xposedscope' <<< "$manifest"; then
  echo 'Legacy Xposed manifest metadata is packaged' >&2
  exit 1
fi
if ! packages=$("$apkanalyzer" dex packages "$apk"); then
  echo 'APK dex package inspection failed' >&2
  exit 1
fi
if grep -Eq '^[PC] d[[:space:]].*io\.github\.libxposed\.api' <<< "$packages"; then
  echo 'Modern Xposed compile-only API classes were packaged in the APK' >&2
  exit 1
fi
checksum=$(sha256sum "$apk")
sha=${checksum%% *}
python3 - "$apk.release.json" "$version_name" "$version_code" "$expected_signer" "$sha" <<'PY'
import json
import sys
path, name, code, signer, checksum = sys.argv[1:]
with open(path, 'w') as output:
    json.dump({'tag': 'v' + name, 'versionName': name, 'versionCode': int(code),
               'signerSha256': signer, 'apkSha256': checksum}, output, sort_keys=True)
    output.write('\n')
PY
printf '%s\n' "$checksum" > "$apk.sha256"
