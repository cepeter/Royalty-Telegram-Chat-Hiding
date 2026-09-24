import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/io/github/cepeter/royalty/MainActivity.java"


class ConfigurationUiContractTests(unittest.TestCase):
    def setUp(self):
        self.source = ACTIVITY.read_text()

    def test_ui_shows_hook_status_and_multiple_choice_catalog(self):
        self.assertIn("loadHookStatuses", self.source)
        self.assertIn("CHOICE_MODE_MULTIPLE", self.source)
        self.assertIn("loadCatalog", self.source)
        self.assertIn("Account ", self.source)

    def test_ui_saves_hidden_keys_and_notification_setting(self):
        self.assertIn("ConfigStore.save", self.source)
        self.assertIn("setSuppressNotifications", self.source)
        self.assertIn("RuntimeException", self.source)
        self.assertIn("Changes saved", self.source)

    def test_ui_explains_supported_scope_and_refresh(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("Main lists, notifications, search, share targets", strings)
        self.assertIn("group/contact pickers are supported on Telegram 12.10.4", strings)
        self.assertNotIn("are not hidden", strings)
        self.assertIn("Open Telegram", strings)

    def test_modern_dashboard_uses_themed_cards_and_accessible_controls(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        colors = (ROOT / "app/src/main/res/values/colors.xml").read_text()
        night_colors = (ROOT / "app/src/main/res/values-night/colors.xml").read_text()

        self.assertIn("createCard", self.source)
        self.assertIn("createPrimaryButton", self.source)
        self.assertIn("setMinimumHeight(dp(48))", self.source)
        self.assertIn("R.color.royalty_background", self.source)
        self.assertIn("Private Telegram controls", strings)
        self.assertIn("Hidden chats", strings)
        self.assertIn("royalty_background", colors)
        self.assertIn("royalty_background", night_colors)

    def test_refresh_requests_catalog_and_observes_completion(self):
        self.assertIn("CatalogRequestClient.request(this)", self.source)
        self.assertIn("CatalogProtocol.ACTION_UPDATED", self.source)
        self.assertIn("registerReceiver", self.source)
        self.assertIn("unregisterReceiver", self.source)
        self.assertIn("onPause()", self.source)

    def test_timeout_keeps_cached_rows_and_shows_guidance(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("loadCatalog", self.source)
        self.assertIn("postDelayed", self.source)
        self.assertIn("Open Telegram, then refresh", strings)

    def test_missing_selected_dialogs_remain_manageable(self):
        self.assertIn("addMissingSelections", self.source)
        self.assertIn("Unavailable from current catalog", self.source)


if __name__ == "__main__":
    unittest.main()
