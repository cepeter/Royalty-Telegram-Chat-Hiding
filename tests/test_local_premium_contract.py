import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramPremiumHook.java"
TELEGRAM_HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"
STORE = ROOT / "app/src/main/java/io/github/cepeter/royalty/config/ConfigStore.java"
ACTIVITY = ROOT / "app/src/main/java/io/github/cepeter/royalty/MainActivity.java"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"


class LocalPremiumContractTests(unittest.TestCase):
    def test_hook_only_overrides_client_check_when_enabled(self):
        source = HOOK.read_text()
        self.assertIn('"org.telegram.messenger.UserConfig"', source)
        self.assertIn('"isPremium"', source)
        self.assertIn("enabled.getAsBoolean()", source)
        self.assertIn("param.setResult(Boolean.TRUE)", source)
        self.assertIn('status.report("runtime_error"', source)

    def test_hook_is_registered_and_reported(self):
        source = TELEGRAM_HOOK.read_text()
        self.assertIn('install("premium"', source)
        self.assertIn("CONFIG::localPremiumEnabled", source)
        self.assertIn('"notifications", "premium", "reveal"', source)

    def test_toggle_defaults_off_and_is_persisted(self):
        store = STORE.read_text()
        activity = ACTIVITY.read_text()
        strings = STRINGS.read_text()
        self.assertIn('LOCAL_PREMIUM = "local_premium"', store)
        self.assertIn("getBoolean(LOCAL_PREMIUM, false)", store)
        self.assertIn("putBoolean(LOCAL_PREMIUM, localPremium)", store)
        self.assertIn("premiumSwitch.isChecked()", activity)
        self.assertIn("Local Premium", strings)
        self.assertIn("Server-side limits remain unchanged", strings)


if __name__ == "__main__":
    unittest.main()