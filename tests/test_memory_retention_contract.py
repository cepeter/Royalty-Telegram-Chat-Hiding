import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/io/github/cepeter/royalty"


class MemoryRetentionContractTests(unittest.TestCase):
    """Static state that outlives its owner must be weak, cleared, or explicitly dropped."""

    @staticmethod
    def _read(relative):
        return (MAIN / relative).read_text()

    def setUp(self):
        self.hook = self._read("xposed/TelegramHook.java")
        self.receiver = self._read("catalog/CatalogResultReceiver.java")
        self.batch = self._read("catalog/CatalogBatch.java")
        self.activity = self._read("MainActivity.java")

    def test_dialogs_fragment_reference_is_weak(self):
        """A destroyed DialogsActivity must stay collectable while its screen is off the stack."""
        self.assertIn("WeakReference<Object> lastDialogsFragment", self.hook)
        self.assertIn("lastDialogsFragment = new WeakReference<>(fragment)", self.hook)
        self.assertIn("lastDialogsFragment.get()", self.hook)
        self.assertNotIn("lastDialogsFragment = fragment;", self.hook)

    def test_expired_catalog_batch_is_released(self):
        """A timed-out request must not keep its staged catalog snapshot alive."""
        self.assertIn("public static synchronized void clear(String nonce)", self.receiver)
        self.assertIn("batch.owns(nonce)", self.receiver)
        self.assertIn("boolean owns(String candidate)", self.batch)
        timeout = self.activity.split("private final Runnable catalogTimeout", 1)[1]
        self.assertIn("CatalogResultReceiver.clear(requestNonce)", timeout)

    def test_activity_destroy_drops_queued_posts(self):
        """No post queued before unsubscribe may outlive the Activity."""
        on_destroy = self.activity.split("protected void onDestroy()", 1)[1]
        self.assertIn("mainHandler.removeCallbacksAndMessages(null)", on_destroy)


if __name__ == "__main__":
    unittest.main()
