import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class CatalogResultContractTests(unittest.TestCase):
    def setUp(self):
        catalog = ROOT / "app/src/main/java/io/github/cepeter/royalty/catalog"
        self.client = (catalog / "CatalogRequestClient.java").read_text()
        self.receiver = (catalog / "CatalogResultReceiver.java").read_text()

    def test_request_uses_exact_mutable_callback_and_targets_telegram(self):
        self.assertIn("PendingIntent.FLAG_MUTABLE", self.client)
        self.assertIn("Build.VERSION.SDK_INT >= 31", self.client)
        self.assertIn("setComponent", self.client)
        self.assertIn("setPackage(CatalogProtocol.TELEGRAM_PACKAGE)", self.client)
        self.assertIn("CatalogProtocol.EXTRA_CALLBACK", self.client)

    def test_receiver_validates_nonce_and_payload_before_storage(self):
        self.assertIn("isActive", self.receiver)
        self.assertIn("CatalogSubmission.sanitize", self.receiver)
        self.assertIn("CatalogProtocol.TYPE_COMPLETE", self.receiver)
        self.assertIn("CatalogProtocol.ACTION_UPDATED", self.receiver)
        self.assertIn("SystemClock.elapsedRealtime()", self.receiver)

    def test_result_receiver_is_not_exported(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        application = manifest.find("application")
        receivers = application.findall("receiver") if application is not None else []
        result = next(node for node in receivers
                      if node.attrib.get(ANDROID_NS + "name") == ".catalog.CatalogResultReceiver")
        self.assertEqual("false", result.attrib.get(ANDROID_NS + "exported"))


if __name__ == "__main__":
    unittest.main()
