import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"


class XposedHookContractTests(unittest.TestCase):
    def setUp(self):
        self.source = HOOK.read_text()

    def test_only_official_telegram_main_process_is_hooked(self):
        self.assertIn('"org.telegram.messenger".equals(lpparam.packageName)', self.source)
        self.assertIn("lpparam.packageName.equals(lpparam.processName)", self.source)

    def test_dialog_hook_returns_copy_and_notification_hook_replaces_argument(self):
        self.assertIn('"getDialogs"', self.source)
        self.assertIn("param.setResult(filtered)", self.source)
        self.assertIn('"processNewMessages"', self.source)
        self.assertIn("param.args[0] = filtered", self.source)
        self.assertNotIn(".remove(", self.source)

    def test_reveal_supports_verified_telegram_12_10_4_runtime_types(self):
        self.assertIn('"org.telegram.ui.ActionBar.l"', self.source)
        self.assertIn('"org.telegram.ui.ActionBar.q2"', self.source)
        self.assertIn('"org.telegram.ui.iz"', self.source)
        self.assertIn('"org.telegram.ui.ActionBar.ActionBarLayout"', self.source)
        self.assertIn('"getLastFragment"', self.source)
        self.assertIn('"getActionBar"', self.source)
        self.assertIn('"dispatchTouchEvent"', self.source)
        self.assertIn("REVEAL_HOLD_DURATION_MS = 3000", self.source)
        self.assertIn("postDelayed", self.source)
        self.assertIn("onDeadline", self.source)
        self.assertIn("baseFragmentClass.isAssignableFrom(field.getType())", self.source)
        self.assertNotIn("REVEAL_GESTURE.onUp", self.source)
        self.assertNotIn('"onInterceptTouchEvent"', self.source)
        self.assertNotIn('"android.view.View"', self.source)

    def test_xposed_preferences_reload_and_catalog_uses_callback_bridge(self):
        repository = (ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/XposedConfigRepository.java").read_text()
        bridge = (ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/CatalogRequestBridge.java").read_text()
        self.assertIn("new XSharedPreferences", repository)
        self.assertIn("preferences.reload()", repository)
        self.assertIn("CatalogRequestBridge.register", self.source)
        self.assertIn("CatalogProtocol.ACTION_REQUEST", bridge)
        self.assertNotIn("bindService", bridge)
        self.assertIn("CatalogSubmission.MAX_ENTRIES", self.source)

    def test_old_binding_bridge_is_removed(self):
        publisher = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/CatalogPublisher.java"
        self.assertFalse(publisher.exists())

    def test_telegram_classes_are_resolved_after_application_on_create(self):
        package_load = self.source[
            self.source.index("public void handleLoadPackage") :
            self.source.index("private static void installApplicationBridge")
        ]
        self.assertIn('install("bridge"', package_load)
        self.assertNotIn('install("dialogs"', package_load)
        self.assertNotIn('install("notifications"', package_load)
        self.assertNotIn('install("reveal"', package_load)

        application_callback = self.source[
            self.source.index("protected void afterHookedMethod") :
            self.source.index("private static void installDialogHook")
        ]
        self.assertIn("installRuntimeHooks", application_callback)
        self.assertIn("compareAndSet(false, true)", self.source)

    def test_each_hook_reports_install_status(self):
        self.assertIn('install("dialogs"', self.source)
        self.assertIn('install("notifications"', self.source)
        self.assertIn('install("reveal"', self.source)
        self.assertIn('reportStatus(hook, "installed"', self.source)
        self.assertIn('reportStatus(hook, "missing"', self.source)


if __name__ == "__main__":
    unittest.main()
