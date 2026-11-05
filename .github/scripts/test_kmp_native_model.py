import json
import os
from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from kmp_native_model import check_device_links, load_model


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


if __name__ == "__main__":
    unittest.main()
