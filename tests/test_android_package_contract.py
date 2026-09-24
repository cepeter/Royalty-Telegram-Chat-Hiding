import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class AndroidPackageContractTests(unittest.TestCase):
    def test_gradle_project_is_pinned_for_android(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        app_gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('com.android.application") version "8.7.3"', settings)
        self.assertIn("compileSdk = 35", app_gradle)
        self.assertIn("minSdk = 27", app_gradle)
        self.assertIn("targetSdk = 35", app_gradle)
        self.assertIn('versionName = "2.2.0"', app_gradle)
        self.assertIn("versionCode = 10", app_gradle)

    def test_android_identity_is_royalty(self):
        app_gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('namespace = "io.github.cepeter.royalty"', app_gradle)
        self.assertIn('applicationId = "io.github.cepeter.royalty"', app_gradle)
        self.assertNotIn("io.github.cepeter.telegramhider", app_gradle)

    def test_legacy_identity_is_absent_from_build_inputs(self):
        roots = (
            ROOT / "app/src/main",
            ROOT / "app/src/test",
            ROOT / "app/build.gradle.kts",
            ROOT / "app/proguard-rules.pro",
            ROOT / "scripts/verify-release-apk.sh",
        )
        files = []
        for root in roots:
            files.extend(root.rglob("*") if root.is_dir() else (root,))
        legacy = "io.github.cepeter.telegramhider"
        for path in files:
            if path.is_file():
                self.assertNotIn(legacy, path.read_text(), path)

    def test_readme_targets_verified_telegram_12_10_4(self):
        readme = (ROOT / "README.md").read_text()
        self.assertIn("Telegram **12.10.4**", readme)
        self.assertIn("versionCode 70992", readme)
        self.assertIn("146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d", readme)
        self.assertNotIn("12.8.3", readme)

    def test_release_220_documents_multi_surface_scope_honestly(self):
        readme = (ROOT / "README.md").read_text()
        changelog = (ROOT / "CHANGELOG.md").read_text()
        security = (ROOT / "SECURITY.md").read_text()
        acceptance = (ROOT / "docs/device-acceptance-2.2.0.md").read_text()

        self.assertIn("## [2.2.0] - 2026-09-24", changelog)
        self.assertIn("| 2.2.x | Yes |", security)
        for surface in ("Search and global search", "Share and contact pickers", "New group, add member, and contact invite"):
            self.assertIn(surface, readme)
        self.assertIn("Not executed before publication", acceptance)
        self.assertIn("empty device list", acceptance)

    def test_app_label_is_royalty(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn('<string name="app_name">Royalty</string>', strings)

    def test_xposed_api_is_compile_only(self):
        app_gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('compileOnly("de.robv.android.xposed:api:82")', app_gradle)
        self.assertNotIn('implementation("de.robv.android.xposed:api:', app_gradle)

    def test_manifest_declares_vector_compatible_legacy_module(self):
        manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
        root = ET.parse(manifest_path).getroot()
        application = root.find("application")
        if application is None:
            self.fail("manifest must contain an application element")
        metadata = {
            node.attrib[ANDROID_NS + "name"]: node.attrib.get(ANDROID_NS + "value", "")
            for node in application.findall("meta-data")
        }
        self.assertEqual("true", metadata["xposedmodule"])
        self.assertEqual("93", metadata["xposedminversion"])
        self.assertEqual("true", metadata["xposedsharedprefs"])
        self.assertEqual("org.telegram.messenger", metadata["xposedscope"])

    def test_entrypoint_asset_is_exact(self):
        entrypoint = (ROOT / "app/src/main/assets/xposed_init").read_text()
        self.assertEqual(
            "io.github.cepeter.royalty.xposed.TelegramHook\n",
            entrypoint,
        )

    def test_aidl_and_visibility_sensitive_bridge_are_absent(self):
        app_gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertNotIn("aidl = true", app_gradle)
        obsolete = (
            ROOT / "app/src/main/aidl/io/github/cepeter/royalty/ICatalogService.aidl",
            ROOT / "app/src/main/java/io/github/cepeter/royalty/catalog/CatalogService.java",
            ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/CatalogPublisher.java",
        )
        for path in obsolete:
            self.assertFalse(path.exists(), path)
        sources = "\n".join(
            path.read_text() for path in (ROOT / "app/src/main/java").rglob("*.java")
        )
        for token in ("ICatalogService", "bindService", "Binder.getCallingUid"):
            self.assertNotIn(token, sources)


if __name__ == "__main__":
    unittest.main()
