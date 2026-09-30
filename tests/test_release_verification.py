import json
import os
import pathlib
import subprocess
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SIGNER = "1b2045af07e41823df288b75355314cd18ccf4926daa13ac84b8b38493bd573f"


class ReleaseVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.sdk = self.root / "sdk"
        tools = self.sdk / "build-tools/36.0.0"
        analyzer = self.sdk / "cmdline-tools/latest/bin"
        tools.mkdir(parents=True)
        analyzer.mkdir(parents=True)
        self.apk = self.root / "app-release.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("META-INF/xposed/java_init.list", "io.github.cepeter.royalty.xposed.TelegramHook\n")
            archive.writestr("META-INF/xposed/scope.list", "org.telegram.messenger\n")
            archive.writestr("META-INF/xposed/module.prop", "minApiVersion=101\ntargetApiVersion=101\nstaticScope=true\n")
        self.command(tools / "apksigner", f"printf '%s\\n' 'Number of signers: 1' 'Signer #1 certificate SHA-256 digest: {SIGNER}'")
        self.command(tools / "aapt", "if [[ $1 == dump && $2 == badging ]]; then echo \"package: name='io.github.cepeter.royalty' versionCode='24' versionName='3.1.7'\"; else echo 'manifest'; fi")
        self.command(analyzer / "apkanalyzer", "echo 'P d io.github.cepeter.royalty'")

    def command(self, path, body):
        path.write_text("#!/usr/bin/env bash\nset -euo pipefail\n" + body + "\n")
        path.chmod(0o755)

    def verify(self, tag=None):
        env = dict(os.environ, ANDROID_HOME=str(self.sdk))
        if tag is not None:
            env["GITHUB_REF_TYPE"] = "tag"
            env["GITHUB_REF_NAME"] = tag
        return subprocess.run([str(ROOT / "scripts/verify-release-apk.sh"), str(self.apk)],
                              cwd=ROOT, env=env, text=True, capture_output=True)

    def assert_rejected_without_manifest(self, result):
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(pathlib.Path(str(self.apk) + ".sha256").exists())
        self.assertFalse(pathlib.Path(str(self.apk) + ".release.json").exists())

    def test_failed_analyzer_cannot_emit_success_checksum(self):
        self.command(self.sdk / "cmdline-tools/latest/bin/apkanalyzer", "exit 7")
        self.assert_rejected_without_manifest(self.verify())

    def test_wrong_version_separators_are_rejected(self):
        self.command(self.sdk / "build-tools/36.0.0/aapt", "if [[ $2 == badging ]]; then echo \"package: name='io.github.cepeter.royalty' versionCode='24' versionName='3x1y7'\"; else echo manifest; fi")
        self.assert_rejected_without_manifest(self.verify("v3.1.7"))

    def test_explicit_inspection_command_failures(self):
        for tool, body in (("apksigner", "exit 9"),
                           ("aapt", "if [[ $2 == badging ]]; then exit 8; fi"),
                           ("aapt", "if [[ $2 == xmltree ]]; then exit 7; else echo \"package: name='io.github.cepeter.royalty' versionCode='24' versionName='3.1.7'\"; fi")):
            with self.subTest(tool=tool, body=body):
                path = self.sdk / "build-tools/36.0.0" / tool
                original = path.read_text()
                self.command(path, body)
                self.assert_rejected_without_manifest(self.verify())
                path.write_text(original)

    def test_tag_must_match_inspected_apk_version(self):
        self.assert_rejected_without_manifest(self.verify("v99.0.0"))

    def test_rejects_malformed_apk_metadata(self):
        self.command(self.sdk / "build-tools/36.0.0/aapt", "echo 'malformed metadata'")
        self.assert_rejected_without_manifest(self.verify())

    def test_rejects_wrong_or_multiple_signers(self):
        signer = self.sdk / "build-tools/36.0.0/apksigner"
        for body in (
            "echo 'Number of signers: 1'; echo 'Signer #1 certificate SHA-256 digest: '" + "0" * 64,
            f"printf '%s\\n' 'Number of signers: 2' 'Signer #1 certificate SHA-256 digest: {SIGNER}' 'Signer #2 certificate SHA-256 digest: {'0' * 64}'",
        ):
            with self.subTest(body=body):
                self.command(signer, body)
                self.assert_rejected_without_manifest(self.verify())

    def test_rejects_forbidden_framework_classes(self):
        self.command(self.sdk / "cmdline-tools/latest/bin/apkanalyzer", "echo 'P d io.github.libxposed.api'")
        self.assert_rejected_without_manifest(self.verify())

    def test_valid_tag_produces_verified_metadata_and_checksum(self):
        result = self.verify("v3.1.7")
        self.assertEqual(0, result.returncode, result.stderr)
        metadata = json.loads(pathlib.Path(str(self.apk) + ".release.json").read_text())
        self.assertEqual({"versionName": "3.1.7", "versionCode": 24, "tag": "v3.1.7", "signerSha256": SIGNER},
                         {key: metadata[key] for key in ("versionName", "versionCode", "tag", "signerSha256")})
        self.assertTrue(pathlib.Path(str(self.apk) + ".sha256").exists())


if __name__ == "__main__":
    unittest.main()
