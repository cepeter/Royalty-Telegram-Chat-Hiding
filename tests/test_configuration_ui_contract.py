import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/io/github/cepeter/royalty/MainActivity.java"


class ConfigurationUiContractTests(unittest.TestCase):
    def setUp(self):
        self.source = ACTIVITY.read_text()

    def test_ui_shows_compact_connection_and_multiple_choice_catalog(self):
        self.assertIn("loadHookStatuses", self.source)
        self.assertIn("CHOICE_MODE_MULTIPLE", self.source)
        self.assertIn("loadCatalog", self.source)
        self.assertIn("frameworkStatusDot", self.source)
        self.assertIn("telegramStatusDot", self.source)
        self.assertIn("R.color.royalty_success", self.source)
        self.assertIn("R.color.royalty_error", self.source)
        self.assertIn("CatalogSelectionControls", self.source)

    def test_dashboard_scrolls_and_chat_picker_has_search_and_useful_height(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("new ScrollView(this)", self.source)
        self.assertIn("new EditText(this)", self.source)
        self.assertIn("addTextChangedListener", self.source)
        self.assertIn("applyCatalogFilter", self.source)
        self.assertIn("dp(360)", self.source)
        self.assertIn("Search chats", strings)

    def test_chat_search_has_one_tap_clear_action(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("clearSearchButton", self.source)
        self.assertIn('searchInput.setText("")', self.source)
        self.assertIn("text.length() == 0 ? View.GONE : View.VISIBLE", self.source)
        self.assertIn("Clear search", strings)

    def test_ui_saves_hidden_keys_and_notification_setting(self):
        self.assertIn("ConfigStore.save", self.source)
        self.assertIn("setSuppressNotifications", self.source)
        self.assertIn("RuntimeException", self.source)
        self.assertIn("Changes saved", self.source)

    def test_ui_explains_supported_scope_and_refresh(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("Telegram 12.10.4 is the tested profile", strings)
        self.assertIn("semantic hook resolution validates every required surface", strings)
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

    def test_dashboard_uses_collapsible_diagnostics_and_grouped_actions(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("diagnosticsExpanded", self.source)
        self.assertIn("protectionDetails.setVisibility(View.GONE)", self.source)
        self.assertIn("createSoftPanel", self.source)
        self.assertIn("LinearLayout chatsCard = createCard", self.source)
        self.assertIn("LinearLayout saveActions", self.source)
        self.assertIn("Privacy controls", strings)
        self.assertIn("Show diagnostics", strings)
        self.assertIn("Backup &amp; recovery", strings)

    def test_dashboard_uses_four_native_tabs(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        for token in ("TAB_CHATS", "TAB_PRIVACY", "TAB_STATUS", "TAB_BACKUP",
                      "createDashboardTab", "selectDashboardTab", "styleDashboardTab"):
            self.assertIn(token, self.source)
        for label in ("Chats", "Privacy", "Status", "Backup"):
            self.assertIn(">" + label + "<", strings)
        self.assertIn("chatsPage.addView", self.source)
        self.assertIn("privacyPage.addView", self.source)
        self.assertIn("statusPage.addView", self.source)
        self.assertIn("backupPage.addView", self.source)

    def test_authentication_copy_is_settings_only(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("Lock Royalty settings with device screen lock", strings)
        self.assertIn("Telegram reveal stays instant", strings)
        self.assertNotIn("Require device screen lock to reveal", self.source)

    def test_refresh_requests_catalog_and_observes_completion(self):
        self.assertIn("CatalogRequestClient.request(this)", self.source)
        self.assertIn("CatalogUpdates.shared().subscribe", self.source)
        self.assertIn("CatalogUpdates.shared().unsubscribe", self.source)
        self.assertIn("event.nonce().equals(requestNonce)", self.source)
        self.assertIn("onPause()", self.source)

    def test_timeout_keeps_cached_rows_and_shows_guidance(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("loadCatalog", self.source)
        self.assertIn("postDelayed", self.source)
        self.assertIn("Open Telegram, then refresh", strings)

    def test_inactive_telegram_keeps_last_verified_hook_health_neutral(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
        self.assertIn("protection.inactive()", self.source)
        self.assertIn("Last verified", strings)

    def test_missing_selected_dialogs_remain_manageable(self):
        self.assertIn("addMissingSelections", self.source)
        self.assertIn("Unavailable from current catalog", self.source)


if __name__ == "__main__":
    unittest.main()
