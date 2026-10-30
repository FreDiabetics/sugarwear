"""Product boundaries that must hold before Gradle or ADB is invoked."""
import importlib.util
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class StandaloneBuildTest(unittest.TestCase):
    def test_shared_modules_are_pinned_submodule_sources(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        modules = re.search(r"val sharedModules = listOf\((.*?)\)", settings).group(1)
        self.assertEqual(set(re.findall(r'"([^" ]+)"', modules)), {
            "core-model", "dexcom-g7", "wear-protocol", "wear-storage", "ui-shared", "complications",
        })
        self.assertIn('project(":$module").projectDir = sharedRoot.resolve(module)', settings)
        self.assertIn('include(":g7watch", ":watchfaces:vigil")', settings)
        for module in re.findall(r'"([^" ]+)"', modules):
            self.assertFalse((ROOT / module).exists(), f"Shared code copied: {module}")
        submodule = (ROOT / ".gitmodules").read_text()
        self.assertIn("path = shared/sugarlicious", submodule)
        self.assertIn("https://github.com/FreDiabetics/sugarlicious", submodule)

    def test_installer_only_builds_owned_watch_packages(self):
        build = (ROOT / "build.gradle.kts").read_text()
        self.assertIn('dependsOn(":g7watch:assembleDebug", ":watchfaces:vigil:assembleDebug")', build)
        self.assertIn('extra["productPhoneApks"] = emptyList<String>()', build)
        self.assertEqual(2, len(re.findall(r'"[^"\n]+/build/outputs/apk/debug/[^"\n]+\.apk"', build)))
        self.assertIn('sharedRoot.resolve("gradle/product-installer.gradle")', build)
        installer = (ROOT / "shared/sugarlicious/gradle/product-installer.gradle").read_text()
        self.assertIn('watches.size() != 1', installer)
        self.assertIn('devices.findAll { isWatch(it) }', installer)
        self.assertIn('unique { physicalId(it) }', installer)
        self.assertIn("'-s', serial, 'install', '-r', '-t'", installer)
        self.assertIn("equalsIgnoreCase('Success')", installer)
        self.assertNotIn('app-mobile', build)
        self.assertNotIn('app-wear', build)
        self.assertNotIn('uninstall', build)

    def test_ci_initializes_submodules_and_validates_only_vigil(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        self.assertIn("submodules: recursive", workflow)
        self.assertIn("-WatchfaceFiles", workflow)
        self.assertIn("watchfaces/vigil/src/main/res/raw/watchface.xml", workflow)
        self.assertNotIn("verify-codefree-watchfaces.ps1", workflow)
        self.assertNotIn("sugarlicious-direct-to-watch", workflow)

    def test_codefree_verifier_accepts_resources_and_rejects_dex(self):
        spec = importlib.util.spec_from_file_location("verifier", ROOT / "tools/verify_codefree_vigil.py")
        verifier = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(verifier)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "vigil.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("res/raw/watchface.xml", "<WatchFace />")
            self.assertEqual(len(verifier.verify(apk)), 64)
            with zipfile.ZipFile(apk, "a") as archive:
                archive.writestr("classes2.dex", b"dex")
            with self.assertRaisesRegex(ValueError, "executable DEX"):
                verifier.verify(apk)


if __name__ == "__main__":
    unittest.main()
