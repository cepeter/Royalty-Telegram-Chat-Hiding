import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramHook.java"
CONTACTS = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/TelegramContactHook.java"


class ContactHookContractTests(unittest.TestCase):
    def setUp(self):
        self.hook = HOOK.read_text()
        self.contacts = CONTACTS.read_text()

    def test_group_and_contact_search_use_filtered_replacement_copies(self):
        self.assertIn('install("contacts"', self.hook)
        self.assertIn("TelegramSemanticResolver.Target.GROUP_ADAPTER", self.contacts)
        self.assertIn("TelegramSemanticResolver.Target.CONTACT_SEARCH", self.contacts)
        self.assertIn("DialogFilter.filteredPairedCopy", self.contacts)
        self.assertIn("DialogFilter.filteredCopy", self.contacts)
        self.assertIn("ModernHookBridge.setObjectField", self.contacts)
        self.assertIn("FilteredListState.capture", self.contacts)
        self.assertNotIn("private static final class ListState", self.contacts)

    def test_refresh_resolution_does_not_gate_surface_installation(self):
        self.assertNotIn("verifyRefreshMethod", self.contacts)

    def test_sectioned_contact_list_is_position_mapped_not_modified(self):
        self.assertIn("TelegramSemanticResolver.Target.CONTACT_LIST", self.contacts)
        for method in ('"M"', '"O"', '"N"', '"P"', '"V"', '"W"'):
            self.assertIn(method, self.contacts)
        self.assertIn("DialogFilter.visiblePositions", self.contacts)
        self.assertIn("ModernHookBridge.invokeOriginalMethod", self.contacts)
        self.assertIn("concrete.isInstance(p.thisObject)", self.contacts)

    def test_unknown_picker_rows_fail_open_with_health_detail(self):
        self.assertIn("TelegramObjectKey.fromPickerResult", self.contacts)
        self.assertIn('"unknown_rows_visible"', self.contacts)
        self.assertIn('status.report("runtime_error"', self.contacts)


if __name__ == "__main__":
    unittest.main()
