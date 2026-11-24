from pathlib import Path
from tempfile import TemporaryDirectory
import tomllib
import unittest

from kmp_native_cache_key import cache_key


class KmpNativeCacheKeyTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.gradle_dir = Path(temporary.name)
        self.catalog = self.gradle_dir / "libs.versions.toml"
        self.targets = self.gradle_dir / "kmp-native-targets.txt"
        self.catalog.write_text('[versions]\nkotlin = "2.4.10"\n', encoding="utf-8")
        self.targets.write_text("iosArm64\niosSimulatorArm64\n", encoding="utf-8")

    def test_preserves_the_cache_key_for_existing_targets(self):
        for runner in ("Linux", "macOS"):
            with self.subTest(runner=runner):
                prefix = f"konan-{runner}-kotlin-2.4.10-"
                self.assertEqual((prefix + "iosArm64-iosSimulatorArm64", prefix),
                                 cache_key(self.gradle_dir, runner))

    def test_target_order_and_toml_format_do_not_change_key(self):
        expected = cache_key(self.gradle_dir, "Linux")
        self.catalog.write_text('[versions]\nkotlin="2.4.10" # pinned\n', encoding="utf-8")
        self.targets.write_text("\niosSimulatorArm64\n  iosArm64\n", encoding="utf-8")
        self.assertEqual(expected, cache_key(self.gradle_dir, "Linux"))

    def test_empty_duplicated_and_malformed_targets(self):
        for targets in ("", "iosArm64\niosArm64", "iosArm64\ninvalid target"):
            with self.subTest(targets=targets):
                self.targets.write_text(targets, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "kmp-native-targets.txt"):
                    cache_key(self.gradle_dir, "Linux")

    def test_missing_input(self):
        self.targets.unlink()
        with self.assertRaises(FileNotFoundError):
            cache_key(self.gradle_dir, "Linux")

    def test_duplicate_kotlin_key(self):
        self.catalog.write_text('[versions]\nkotlin = "2.4.10"\nkotlin = "2.4.9"\n', encoding="utf-8")
        with self.assertRaises(tomllib.TOMLDecodeError):
            cache_key(self.gradle_dir, "Linux")


if __name__ == "__main__":
    unittest.main()
