import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SIGNER = "1b2045af07e41823df288b75355314cd18ccf4926daa13ac84b8b38493bd573f"


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.staged = self.root / "release-apk"
        self.staged.mkdir()
        self.version = self.root / "version.properties"
        self.version.write_text("versionName=3.1.0\nversionCode=18\n")
        self.apk = self.staged / "royalty-v3.1.0.apk"
        self.apk.write_bytes(b"signed test fixture")
        self.sha = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        (self.staged / "royalty-v3.1.0.apk.sha256").write_text(f"{self.sha}  royalty-v3.1.0.apk\n")
        self.metadata("v3.1.0", 18, self.sha, self.staged / "royalty-v3.1.0.apk.release.json")
        self.releases = self.root / "releases.json"
        self.remote = self.root / "remote"
        self.remote.mkdir()
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk"])])
        self.bin = self.root / "bin"
        self.bin.mkdir()
        gh = self.bin / "gh"
        gh.write_text("#!/usr/bin/env python3\nimport os, pathlib, sys\n"
                      "if os.environ.get('GH_FAIL') == '1': sys.exit(7)\n"
                      "if sys.argv[1] == 'api': print(pathlib.Path(os.environ['RELEASE_FIXTURE']).read_text())\n"
                      "elif sys.argv[1:3] == ['release', 'download']:\n"
                      " tag = sys.argv[3]; name = sys.argv[sys.argv.index('--pattern') + 1]; dest = pathlib.Path(sys.argv[sys.argv.index('--dir') + 1]); dest.joinpath(name).write_bytes(pathlib.Path(os.environ['REMOTE_FIXTURE'], tag, name).read_bytes())\n"
                      "else: sys.exit(8)\n")
        gh.chmod(0o755)

    def metadata(self, tag, code, sha, path):
        path.write_text(json.dumps({"tag": tag, "versionName": tag[1:], "versionCode": code,
                                    "signerSha256": SIGNER, "apkSha256": sha}) + "\n")

    def release(self, tag, assets):
        return {"tag_name": tag, "draft": False, "prerelease": False,
                "assets": [{"name": name} for name in assets]}

    def set_releases(self, releases):
        self.releases.write_text(json.dumps(releases))

    def run_validation(self, fail_lookup=False, tag="v3.1.0"):
        env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                   GITHUB_REPOSITORY="cepeter/Royalty-Telegram-Chat-Hiding",
                   GITHUB_REF_NAME=tag, RELEASE_FIXTURE=str(self.releases),
                   REMOTE_FIXTURE=str(self.remote), GH_FAIL="1" if fail_lookup else "0")
        return subprocess.run(["python3", str(ROOT / "scripts/validate-release-publication.py"),
                               str(self.staged), str(self.version)], cwd=ROOT, env=env,
                              capture_output=True, text=True)

    def test_accepts_code_above_verified_bootstrap(self):
        result = self.run_validation()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_accepts_metadata_chain_after_bootstrap_release_is_pruned(self):
        prior = self.remote / "v3.0.6"
        prior.mkdir()
        self.metadata("v3.0.6", 17, "a" * 64, prior / "royalty-v3.0.6.apk.release.json")
        self.set_releases(
            [self.release("v3.0.6", ["royalty-v3.0.6.apk", "royalty-v3.0.6.apk.release.json"])]
        )

        result = self.run_validation()

        self.assertEqual(0, result.returncode, result.stderr)

    def test_older_legacy_release_does_not_block_verified_bootstrap(self):
        self.set_releases([self.release("v2.2.0", ["royalty-v2.2.0.apk"]),
                           self.release("v3.0.5", ["royalty-v3.0.5.apk"])])
        self.assertEqual(0, self.run_validation().returncode)

    def test_rejects_remote_lookup_failure(self):
        self.assertNotEqual(0, self.run_validation(fail_lookup=True).returncode)

    def test_rejects_code_not_above_newest_published_metadata(self):
        prior = self.remote / "v3.2.0"
        prior.mkdir()
        self.metadata("v3.2.0", 19, "a" * 64, prior / "royalty-v3.2.0.apk.release.json")
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk"]),
                           self.release("v3.2.0", ["royalty-v3.2.0.apk", "royalty-v3.2.0.apk.release.json"])])
        self.assertNotEqual(0, self.run_validation().returncode)

    def test_rejects_republishing_existing_tag_with_higher_code(self):
        self.version.write_text("versionName=3.1.0\nversionCode=19\n")
        self.metadata("v3.1.0", 19, self.sha, self.staged / "royalty-v3.1.0.apk.release.json")
        prior = self.remote / "v3.1.0"
        prior.mkdir()
        self.metadata("v3.1.0", 18, "a" * 64, prior / "royalty-v3.1.0.apk.release.json")
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk"]),
                           self.release("v3.1.0", ["royalty-v3.1.0.apk", "royalty-v3.1.0.apk.release.json"])])
        result = self.run_validation()
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("already published", result.stderr)

    def test_rejects_republishing_bootstrap_tag_with_higher_code(self):
        self.version.write_text("versionName=3.0.5\nversionCode=18\n")
        self.apk.rename(self.staged / "royalty-v3.0.5.apk")
        (self.staged / "royalty-v3.1.0.apk.sha256").rename(self.staged / "royalty-v3.0.5.apk.sha256")
        (self.staged / "royalty-v3.0.5.apk.sha256").write_text(f"{self.sha}  royalty-v3.0.5.apk\n")
        self.metadata("v3.0.5", 18, self.sha, self.staged / "royalty-v3.0.5.apk.release.json")
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk"])])
        result = self.run_validation(tag="v3.0.5")
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("already published", result.stderr)

    def test_rejects_bootstrap_metadata_that_disagrees_with_verified_code(self):
        prior = self.remote / "v3.0.5"
        prior.mkdir()
        self.metadata("v3.0.5", 19, "a" * 64, prior / "royalty-v3.0.5.apk.release.json")
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk", "royalty-v3.0.5.apk.release.json"])])
        self.assertNotEqual(0, self.run_validation().returncode)

    def test_rejects_unrecognized_published_version(self):
        self.set_releases([self.release("v3.0.5", ["royalty-v3.0.5.apk"]),
                           self.release("v3.1.1", ["royalty-v3.1.1.apk"])])
        self.assertNotEqual(0, self.run_validation().returncode)

    def test_rejects_invalid_staged_checksum(self):
        self.apk.write_bytes(b"changed")
        self.assertNotEqual(0, self.run_validation().returncode)


if __name__ == "__main__":
    unittest.main()
