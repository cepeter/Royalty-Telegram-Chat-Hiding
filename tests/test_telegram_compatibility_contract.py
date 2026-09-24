import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAP = ROOT / "docs/telegram-12.10.4-compatibility.md"
EXTRACTOR = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramObjectKey.java"
PROBE = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramCompatibilityProbe.java"
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"


class TelegramCompatibilityContractTests(unittest.TestCase):
    def test_map_is_bound_to_verified_telegram_apk(self):
        source = MAP.read_text()
        self.assertIn("`12.10.4` (`70992`)", source)
        self.assertIn(
            "146ec03c20ce4c73ccfa12399f143c0db5992a3419d30ec0f17ef547b3eaba8d",
            source,
        )
        self.assertIn(
            "49c1522548ebacd46ce322b6fd47f6092bb745d0f88082145caf35e14dcc38e1",
            source,
        )

    def test_map_records_all_approved_surface_aliases(self):
        source = MAP.read_text()
        for identity in (
            "`we.b0`",
            "`we.n1`",
            "`org.telegram.ui.Components.wq0`",
            "`org.telegram.ui.Components.oq0`",
            "`org.telegram.ui.Components.sq0`",
            "`ContactsActivity`",
            "`org.telegram.ui.mt`",
            "`org.telegram.ui.nt`",
            "`org.telegram.ui.s70`",
            "`org.telegram.ui.q70`",
        ):
            self.assertIn(identity, source)

    def test_key_extractor_requires_explicit_account_and_fails_open(self):
        source = EXTRACTOR.read_text()
        for entry_point in (
            "fromDialog(int account, Object dialog)",
            "fromMessage(int account, Object message)",
            "fromUser(int account, Object user)",
            "fromChat(int account, Object chat)",
            "fromRecent(int account, Object recent)",
            "fromShareSearch(int account, Object result)",
        ):
            self.assertIn(entry_point, source)
        self.assertIn("Optional.empty()", source)
        self.assertNotIn("UserConfig", source)
        self.assertNotIn("selectedAccount", source)

    def test_runtime_probe_checks_every_surface_and_reports_status(self):
        probe = PROBE.read_text()
        hook = HOOK.read_text()
        for identity in (
            '"we.b0"',
            '"we.a0"',
            '"we.n1"',
            '"org.telegram.ui.Components.wq0"',
            '"org.telegram.ui.Components.oq0"',
            '"org.telegram.ui.Components.sq0"',
            '"org.telegram.ui.Components.kq0"',
            '"org.telegram.ui.ContactsActivity"',
            '"org.telegram.ui.mt"',
            '"org.telegram.ui.nt"',
            '"org.telegram.ui.s70"',
            '"org.telegram.ui.q70"',
        ):
            self.assertIn(identity, probe)
        self.assertIn('install("compatibility"', hook)
        self.assertIn("TelegramCompatibilityProbe.verify(classLoader)", hook)


if __name__ == "__main__":
    unittest.main()
