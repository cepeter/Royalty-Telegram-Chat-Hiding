import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"
SEARCH = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramSearchHook.java"


class SearchHookContractTests(unittest.TestCase):
    def setUp(self):
        self.hook = HOOK.read_text()
        self.search = SEARCH.read_text()

    def test_search_hook_is_installed_with_reveal_and_config_state(self):
        self.assertIn('install("search"', self.hook)
        self.assertIn("CONFIG::current", self.hook)
        self.assertIn("REVEALED::get", self.hook)

    def test_search_rows_are_position_mapped_without_mutating_telegram_lists(self):
        self.assertIn('Class.forName("we.b0"', self.search)
        self.assertIn('getDeclaredMethod("h")', self.search)
        self.assertIn('getDeclaredMethod("J", int.class)', self.search)
        self.assertIn("DialogFilter.visiblePositions", self.search)
        self.assertIn("XposedBridge.invokeOriginalMethod", self.search)
        self.assertNotIn("setObjectField", self.search)
        self.assertNotIn("getObjectField", self.search)

    def test_position_sensitive_methods_and_async_refresh_are_covered(self):
        for method in ('"J"', '"j"', '"i"', '"v"'):
            self.assertIn(method, self.search)
        self.assertIn('getDeclaredMethod("U", int.class, String.class)', self.search)
        self.assertIn('hookAllMethods(view, "l"', self.search)
        self.assertIn('getDeclaredMethod("T")', self.search)
        self.assertIn('status.report("runtime_error"', self.search)
        self.assertIn('"unknown_rows_visible"', self.search)


if __name__ == "__main__":
    unittest.main()
