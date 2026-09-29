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

    def test_refresh_resolution_does_not_gate_surface_installation(self):
        self.assertNotIn("verifyRefreshMethod", self.share)

    def test_share_lists_use_filtered_copies_and_maps_keep_identity(self):
        self.assertIn("DialogFilter.filteredCopy", self.share)
        self.assertIn("ModernHookBridge.setObjectField", self.share)
        self.assertIn('callMethod(target, "b")', self.share)
        self.assertIn('target, "k"', self.share)
        self.assertNotIn("getDeclaredConstructor", self.share)
        self.assertNotIn('setObjectField(adapter, "e"', self.share)
        self.assertNotIn('setObjectField(outer, "T"', self.share)
        self.assertIn("error.addSuppressed(rollbackError)", self.share)

    def test_reveal_restore_and_complete_selected_collection_guard_are_present(self):
        self.assertIn("FilteredListState.capture", self.share)
        self.assertNotIn("private static final class ListState", self.share)
        self.assertNotIn("LAST_REVEAL", self.share)
        self.assertNotIn("SelectionMarker", self.share)
        guard = self.share[
            self.share.index("private static void guardSelection") :
            self.share.index("private static void safely")
        ]
        # Wiring contract; repeated concealed calls and rollback behavior are
        # exercised by TelegramShareSelectionTest on the actual Java guard.
        self.assertIn("SelectedMapSnapshot.read(selected, account)", guard)
        self.assertIn("config.isHidden(DialogKey.of(account, entry.id))", guard)
        self.assertIn("retained.add(entry.value)", guard)
        self.assertIn("replaceMapContents(selected, retained, account)", guard)
        self.assertIn('status.report("runtime_error"', self.share)


if __name__ == "__main__":
    unittest.main()
