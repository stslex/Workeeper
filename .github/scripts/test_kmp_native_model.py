from contextlib import redirect_stdout
from io import StringIO
import json
import os
from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from kmp_native_model import check_device_links, load_model, main


class KmpNativeModelTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.manifest = self.root / "identities.json"
        self.manifest.write_text('{"sample:module":{"example.Test":["case"]}}', encoding="utf-8")
        manifest_patch = patch("kmp_native_model.IDENTITIES", self.manifest)
        self.addCleanup(manifest_patch.stop)
        manifest_patch.start()
        self.model = self.root / "model.json"
        self.row = {
            "module": ":sample:module",
            "simulatorTask": ":sample:module:iosSimulatorArm64Test",
            "deviceLinkTask": ":sample:module:linkDebugTestIosArm64",
            "simulatorResults": "sample/module/build/results",
            "simulatorBinary": "sample/module/build/bin/simulator/test.kexe",
            "deviceBinary": "sample/module/build/bin/device/test.kexe",
        }

    def read_rows(self, rows):
        self.model.write_text(json.dumps(rows), encoding="utf-8")
        return load_model(self.model)

    def test_complete_export(self):
        self.assertEqual([self.row], self.read_rows([self.row]))

    def test_missing_or_duplicated_module(self):
        for rows in ([], [self.row, self.row]):
            with self.subTest(rows=len(rows)):
                with self.assertRaisesRegex(ValueError, "differ from evaluated"):
                    self.read_rows(rows)

    def test_incorrect_device_task(self):
        self.row["deviceLinkTask"] = self.row["simulatorTask"]
        with self.assertRaisesRegex(ValueError, "unexpected deviceLinkTask"):
            self.read_rows([self.row])

    def test_relative_output_cannot_escape_the_checkout(self):
        self.row["simulatorResults"] = "../outside"
        with self.assertRaisesRegex(ValueError, "leaves the checkout"):
            self.read_rows([self.row])

    def test_duplicate_output(self):
        self.row["deviceBinary"] = self.row["simulatorBinary"]
        with self.assertRaisesRegex(ValueError, "duplicate or invalid output"):
            self.read_rows([self.row])

    def test_device_binary_must_be_fresh_nonempty_and_ios(self):
        marker = self.root / "device-start"
        marker.touch()
        os.utime(marker, ns=(2_000_000_000, 2_000_000_000))
        binary = self.root / "test.kexe"
        rows = [dict(self.row, deviceBinary=str(binary))]
        with self.assertRaisesRegex(ValueError, "No such file"):
            check_device_links(rows, marker)
        binary.touch()
        with self.assertRaisesRegex(ValueError, "empty or stale"):
            check_device_links(rows, marker)
        binary.write_bytes(b"placeholder")
        os.utime(binary, ns=(1_000_000_000, 1_000_000_000))
        with self.assertRaisesRegex(ValueError, "empty or stale"):
            check_device_links(rows, marker)
        os.utime(binary, ns=(3_000_000_000, 3_000_000_000))
        with patch("kmp_native_model.subprocess.run") as inspect:
            inspect.return_value = CompletedProcess([], 0, stdout="platform IOSSIMULATOR\n")
            with self.assertRaisesRegex(ValueError, "expected IOS device binary"):
                check_device_links(rows, marker)
            inspect.return_value = CompletedProcess([], 0, stdout="platform IOS\n")
            check_device_links(rows, marker)

    def test_apple_upload_path_matches_the_verified_device_binary(self):
        self.manifest.write_text('{"app:ios":{"example.Test":["case"]}}', encoding="utf-8")
        apple_host = dict(
            self.row,
            module=":app:ios",
            simulatorTask=":app:ios:iosSimulatorArm64Test",
            deviceLinkTask=":app:ios:linkDebugTestIosArm64",
            simulatorResults="app/ios/build/test-results",
            simulatorBinary="app/ios/build/bin/iosSimulatorArm64/debugTest/test.kexe",
        )
        previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous)
        marker = Path("device-start")
        marker.touch()
        os.utime(marker, ns=(2_000_000_000, 2_000_000_000))
        def select(command):
            selection = StringIO()
            with (
                patch("sys.argv", ["kmp_native_model.py", command]),
                patch("kmp_native_model.load_model", side_effect=lambda: load_model(self.model)),
                redirect_stdout(selection),
            ):
                main()
            return selection.getvalue().rstrip("\n")

        for build_dir, pattern_dir in (
            ("build", "build"),
            ("relocated-build", "relocated-build"),
            ("ci[26]", r"ci\[26\]"),
            (r"literal*?\dir", r"literal\*\?\\dir"),
        ):
            with self.subTest(build_dir=build_dir):
                build = f"app/ios/{build_dir}"
                pattern = f"./app/ios/{pattern_dir}"
                suffix = "/bin/iosArm64/debugTest/test.kexe"
                binary = Path(build + suffix)
                results = build + "/test-results"
                rows = [dict(apple_host, deviceBinary=binary.as_posix(), simulatorResults=results)]
                self.model.write_text(json.dumps(rows), encoding="utf-8")
                self.assertEqual(pattern + suffix, select("apple-device-upload-pattern"))
                self.assertEqual(pattern + "/test-results", select("result-upload-patterns"))
                xml = Path(results) / "TEST-example.Test.xml"
                xml.parent.mkdir(parents=True)
                xml.write_text("<testsuite/>", encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "No such file"):
                    check_device_links(load_model(self.model), marker)
                binary.parent.mkdir(parents=True)
                binary.write_bytes(b"fixture")
                os.utime(binary, ns=(3_000_000_000, 3_000_000_000))
                with patch("kmp_native_model.subprocess.run") as inspect:
                    inspect.return_value = CompletedProcess([], 0, stdout="platform IOS\n")
                    check_device_links(load_model(self.model), marker)
                    self.assertEqual(binary.as_posix(), inspect.call_args.args[0][-1])


if __name__ == "__main__":
    unittest.main()
