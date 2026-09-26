import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class CiReleaseContractTests(unittest.TestCase):
    def setUp(self):
        self.workflow = (ROOT / ".github/workflows/build.yml").read_text()
        self.gradle = (ROOT / "app/build.gradle.kts").read_text()

    def test_actions_are_sha_pinned(self):
        shas = (
            "11d5960a326750d5838078e36cf38b85af677262",
            "cf277c60eb25467037889841efdb72551f06f6c3",
            "9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407",
            "c18668ad3cf93ea998bef934396af7bb5c839dc7",
            "ea165f8d65b6e75b540449e92b4886f43607fa02",
            "d3f86a106a0bac45b974a628896c90dbdf5c8093",
            "3bb12739c298aeb8a4eeaf626c5b8d85266b0e65",
        )
        for sha in shas:
            self.assertIn(sha, self.workflow)

    def test_ci_uses_pinned_uv_before_android_setup(self):
        uv_setup = self.workflow.index("astral-sh/setup-uv@")
        contracts = self.workflow.index("uv run --no-project python -m unittest discover")
        android_setup = self.workflow.index("android-actions/setup-android@")

        self.assertLess(uv_setup, contracts)
        self.assertLess(contracts, android_setup)
        self.assertIn('version: "0.12.18"', self.workflow)
        self.assertNotIn("run: python3 -m unittest discover", self.workflow)

    def test_android_setup_does_not_request_removed_tools_package(self):
        self.assertIn("packages: platform-tools", self.workflow)
        self.assertNotIn("packages: tools", self.workflow)

    def test_ci_runs_all_gates(self):
        for gate in ("unittest discover", "testDebugUnitTest", "lintDebug", "assembleDebug", "assembleRelease"):
            self.assertIn(gate, self.workflow)
        self.assertIn("scripts/verify-release-apk.sh", self.workflow)
        self.assertIn("scripts/verify-reproducible-build.sh", self.workflow)
        self.assertIn("environment: production", self.workflow)
        verifier = (ROOT / "scripts/verify-release-apk.sh").read_text()
        self.assertIn("versionCode='17' versionName='3.0.5'", verifier)
        self.assertIn("contents: read", self.workflow)
        self.assertIn("contents: write", self.workflow)

    def test_pull_requests_upload_debug_acceptance_apk(self):
        self.assertIn("Upload debug acceptance APK", self.workflow)
        self.assertIn("github.event_name == 'pull_request'", self.workflow)
        self.assertIn("app/build/outputs/apk/debug/app-debug.apk", self.workflow)
        self.assertIn("retention-days: 3", self.workflow)

    def test_manual_runs_build_signed_acceptance_apk_only_from_main(self):
        self.assertIn("Upload signed acceptance APK", self.workflow)
        self.assertIn("name: acceptance-apk", self.workflow)
        self.assertIn("app/build/outputs/apk/release/app-release.apk", self.workflow)
        trusted_condition = (
            "startsWith(github.ref, 'refs/tags/v') || "
            "(github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main')"
        )
        self.assertIn(trusted_condition, self.workflow)
        self.assertNotIn(
            "startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch'",
            self.workflow,
        )

    def test_signing_secrets_are_gated_by_production_environment(self):
        build_job = self.workflow[
            self.workflow.index("  build:") : self.workflow.index("  sign:")
        ]
        sign_job = self.workflow[
            self.workflow.index("  sign:") : self.workflow.index("  release:")
        ]
        self.assertNotIn("TCH_KEYSTORE_B64", build_job)
        self.assertIn("environment: production", sign_job)
        self.assertIn("TCH_KEYSTORE_B64", sign_job)
        self.assertIn("needs: build", sign_job)
        self.assertIn("needs: sign", self.workflow)

    def test_release_assets_use_royalty_brand(self):
        self.assertIn('"release-apk/royalty-${GITHUB_REF_NAME}.apk"', self.workflow)
        self.assertNotIn("release-apk/telegram-chat-hider-", self.workflow)

    def test_release_verification_checks_signature_and_contents(self):
        verification = (ROOT / "scripts/verify-release-apk.sh").read_text()
        for check in (
            "apksigner",
            "verify --verbose",
            "aapt",
            "dump badging",
            "META-INF/xposed/java_init.list",
            "META-INF/xposed/scope.list",
            "META-INF/xposed/module.prop",
            "io\\.github\\.libxposed\\.api",
            "sha256sum",
        ):
            self.assertIn(check, verification)

    def test_release_keeps_only_current_release_and_tag(self):
        publish = self.workflow.index("Publish GitHub release")
        cleanup = self.workflow.index("Keep only current release and tag")
        self.assertLess(publish, cleanup)
        self.assertIn("gh release delete", self.workflow)
        self.assertIn("git/matching-refs/tags/", self.workflow)
        self.assertIn("git/refs/tags/$tag", self.workflow)

    def test_dependabot_is_disabled(self):
        self.assertFalse((ROOT / ".github/dependabot.yml").exists())
        self.assertFalse((ROOT / ".github/dependabot.yaml").exists())

    def test_release_uses_secret_backed_signing(self):
        workflow_secrets = ("TCH_KEYSTORE_B64", "TCH_STORE_PASSWORD", "TCH_KEY_ALIAS", "TCH_KEY_PASSWORD")
        for secret in workflow_secrets:
            self.assertIn(secret, self.workflow)
        for variable in ("TCH_KEYSTORE_FILE", "TCH_STORE_PASSWORD", "TCH_KEY_ALIAS", "TCH_KEY_PASSWORD"):
            self.assertIn(variable, self.gradle)
        self.assertNotIn("debug.signingConfig", self.gradle)
        self.assertIn("startsWith(github.ref, 'refs/tags/v')", self.workflow)


if __name__ == "__main__":
    unittest.main()
