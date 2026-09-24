import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"


class ModernXposedApiContractTests(unittest.TestCase):
    def test_legacy_xposed_api_is_absent(self):
        sources = "\n".join(path.read_text() for path in JAVA.rglob("*.java"))
        for token in (
            "de.robv.android.xposed",
            "XposedHelpers",
            "XposedBridge",
            "XC_MethodHook",
            "XSharedPreferences",
            "IXposedHookLoadPackage",
        ):
            self.assertNotIn(token, sources)

    def test_hook_bridge_uses_api_101_interceptor_and_origin_invoker(self):
        bridge = (JAVA / "io/github/cepeter/royalty/xposed/ModernHookBridge.java").read_text()
        self.assertIn("XposedInterface.Hooker", bridge)
        self.assertIn("chain.proceed", bridge)
        self.assertIn("Invoker.Type.ORIGIN", bridge)
        self.assertIn("ExceptionMode.PROTECTIVE", bridge)

    def test_entrypoint_attaches_bridge_and_uses_framework_remote_preferences(self):
        hook = (JAVA / "io/github/cepeter/royalty/xposed/TelegramHook.java").read_text()
        self.assertIn("ModernHookBridge.attach(this)", hook)
        self.assertIn("getRemotePreferences(ConfigStore.PREFERENCES_NAME)", hook)

    def test_proguard_preserves_modern_entry_resource_and_constructor(self):
        rules = (ROOT / "app/proguard-rules.pro").read_text()
        self.assertIn("-adaptresourcefilecontents META-INF/xposed/java_init.list", rules)
        self.assertIn("extends io.github.libxposed.api.XposedModule", rules)


if __name__ == "__main__":
    unittest.main()
