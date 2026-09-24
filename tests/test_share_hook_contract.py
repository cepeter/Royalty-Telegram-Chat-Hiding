import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"
SHARE = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramShareHook.java"


class ShareHookContractTests(unittest.TestCase):
    def setUp(self):
        self.hook = HOOK.read_text()
        self.share = SHARE.read_text()

    def test_share_hook_covers_main_search_and_recent_surfaces(self):
        self.assertIn('install("share"', self.hook)
        self.assertIn('Class.forName("org.telegram.ui.Components.oq0"', self.share)
        self.assertIn('Class.forName("org.telegram.ui.Components.sq0"', self.share)
        for field in ('"d", SEARCH', '"d", HELPER', '"D0", RECENT'):
            self.assertIn(field, self.share)

    def test_share_lists_are_replaced_with_filtered_copies_and_rebuilt_map(self):
        self.assertIn("DialogFilter.filteredCopy", self.share)
        self.assertIn("ModernHookBridge.setObjectField", self.share)
        self.assertIn('callMethod(result, "k"', self.share)
        self.assertNotIn(".clear(", self.share)
        self.assertNotIn(".remove(", self.share)

    def test_reveal_restore_and_stale_selection_guard_are_present(self):
        self.assertIn("state.raw", self.share)
        self.assertIn("LAST_REVEAL", self.share)
        self.assertIn("guardSelection", self.share)
        self.assertIn("config.isHidden", self.share)
        self.assertIn('setObjectField(outer, "T"', self.share)
        self.assertIn('status.report("runtime_error"', self.share)


if __name__ == "__main__":
    unittest.main()
