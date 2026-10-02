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
        self.assertIn("TelegramHook::revealState", self.hook)

    def test_refresh_resolution_does_not_gate_surface_installation(self):
        self.assertNotIn("verifyRefreshMethod", self.search)

    def test_search_rows_are_position_mapped_without_mutating_telegram_lists(self):
        self.assertIn("TelegramSemanticResolver.Target.DIALOG_SEARCH", self.search)
        self.assertIn('"search.count"', self.search)
        self.assertIn('"search.item"', self.search)
        self.assertIn("DialogFilter.visiblePositions", self.search)
        self.assertIn("ModernHookBridge.invokeOriginalMethod", self.search)
        self.assertNotIn("setObjectField", self.search)
        self.assertNotIn("getObjectField", self.search)

    def test_position_sensitive_methods_and_async_refresh_are_covered(self):
        for method in ('"J"', '"j"', '"i"', '"v"'):
            self.assertIn(method, self.search)
        self.assertIn('"search.invalidate"', self.search)
        self.assertIn('"search.view.invalidate"', self.search)
        self.assertIn('"search.async.refresh"', self.search)
        self.assertIn('status.report("runtime_error"', self.search)
        self.assertIn('"unknown_rows_visible"', self.search)

    def test_async_reload_resolves_through_semantic_resolver(self):
        self.assertIn('"search.async.reload"', self.search)
        self.assertIn('resolveMethodByArity("search.async.reload"', self.search)
        self.assertNotIn('callMethod(param.thisObject, "l")', self.search)
        probe = (ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramCompatibilityProbe.java").read_text()
        self.assertIn('requireMethod(dialogsSearch, "l")', probe)


if __name__ == "__main__":
    unittest.main()
