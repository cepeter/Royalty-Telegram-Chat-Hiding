import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class CatalogSecurityContractTests(unittest.TestCase):
    def test_catalog_callback_receiver_is_not_exported(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        application = manifest.find("application")
        if application is None:
            self.fail("application missing")
        receivers = application.findall("receiver")
        receiver = next(
            node
            for node in receivers
            if node.attrib.get(ANDROID_NS + "name") == ".catalog.CatalogResultReceiver"
        )
        self.assertEqual("false", receiver.attrib[ANDROID_NS + "exported"])
        self.assertFalse(application.findall("service"))

    def test_config_uses_framework_remote_preferences_without_world_readable_files(self):
        source = (ROOT / "app/src/main/java/io/github/cepeter/royalty/config/ConfigStore.java").read_text()
        self.assertNotIn("MODE_WORLD_READABLE", source)
        self.assertIn("SharedPreferences preferences", source)
        self.assertIn("putStringSet", source)
        self.assertIn("commit()", source)

    def test_stale_catalog_aidl_keep_rules_are_absent(self):
        rules = (ROOT / "app/proguard-rules.pro").read_text()
        self.assertNotIn("ICatalogService", rules)

    def test_notification_suppression_is_opt_in(self):
        config_store = (ROOT / "app/src/main/java/io/github/cepeter/royalty/config/ConfigStore.java").read_text()
        xposed_store = (ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/XposedConfigRepository.java").read_text()
        self.assertIn("getBoolean(SUPPRESS_NOTIFICATIONS, false)", config_store)
        self.assertIn("return ConfigStore.load(preferences)", xposed_store)

    def test_callback_surface_is_bounded(self):
        protocol = (ROOT / "app/src/main/java/io/github/cepeter/royalty/catalog/CatalogProtocol.java").read_text()
        submission = (ROOT / "app/src/main/java/io/github/cepeter/royalty/core/CatalogSubmission.java").read_text()
        self.assertIn("MAX_STATUS_COUNT = 16", protocol)
        self.assertIn("MAX_STATUS_DETAIL_LENGTH = 256", protocol)
        self.assertIn("MAX_ENTRIES = 1024", submission)
        self.assertIn("MAX_TITLE_LENGTH = 256", submission)


if __name__ == "__main__":
    unittest.main()
