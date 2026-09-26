import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STATE = ROOT / "app/src/main/java/io/github/cepeter/royalty/xposed/FilteredListState.java"


class FilteredListStateContractTests(unittest.TestCase):
    def test_reconcile_is_identity_based_and_linear(self):
        source = STATE.read_text()
        self.assertIn("IdentityHashMap", source)
        self.assertIn("sameIdentityContents", source)
        self.assertNotIn(".indexOf(", source)


if __name__ == "__main__":
    unittest.main()
