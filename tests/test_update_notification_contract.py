import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
ACTIVITY = ROOT / "app/src/main/java/io/github/cepeter/royalty/MainActivity.java"
CHECKER = ROOT / "app/src/main/java/io/github/cepeter/royalty/update/UpdateChecker.java"
RELEASE = ROOT / "app/src/main/java/io/github/cepeter/royalty/update/UpdateRelease.java"


class UpdateNotificationContractTests(unittest.TestCase):
    def test_manifest_grants_only_network_access_needed_for_update_check(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {
            node.attrib[ANDROID_NS + "name"]
            for node in manifest.findall("uses-permission")
        }
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertNotIn("android.permission.POST_NOTIFICATIONS", permissions)
        self.assertNotIn("android.permission.REQUEST_INSTALL_PACKAGES", permissions)

    def test_checker_is_bounded_https_only_and_unauthenticated(self):
        source = CHECKER.read_text()
        self.assertIn("https://api.github.com/repos/cepeter/Royalty-Telegram-Chat-Hiding/releases/latest", source)
        self.assertIn("HttpsURLConnection", source)
        self.assertIn("setConnectTimeout", source)
        self.assertIn("setReadTimeout", source)
        self.assertIn("MAX_RESPONSE_BYTES", source)
        self.assertIn("setInstanceFollowRedirects(false)", source)
        self.assertNotIn("Authorization", source)
        self.assertNotIn("startActivity", source)

    def test_release_metadata_is_validated_before_use(self):
        source = RELEASE.read_text()
        self.assertIn("TRUSTED_RELEASE_PREFIX", source)
        self.assertIn("draft", source)
        self.assertIn("prerelease", source)
        self.assertIn("VERSION_PATTERN", source)

    def test_activity_checks_at_most_daily_and_shows_dismissible_card(self):
        source = ACTIVITY.read_text()
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("UPDATE_CHECK_INTERVAL_MILLIS", source)
        self.assertIn("maybeCheckForUpdate", source)
        self.assertIn("updateCard.setVisibility(View.VISIBLE)", source)
        self.assertIn("dismissed_update_tag", source)
        self.assertIn("cached_update_tag", source)
        self.assertIn("UpdateRelease.fromStored", source)
        self.assertIn("BuildConfig.VERSION_NAME", source)
        self.assertIn("Update available", strings)
        self.assertIn("View release", strings)
        self.assertIn("Dismiss", strings)


if __name__ == "__main__":
    unittest.main()
