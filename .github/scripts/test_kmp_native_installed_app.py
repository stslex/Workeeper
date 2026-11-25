import copy
import hashlib
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from ios_app_results import HOST, PERSISTENCE, REQUIRED, empty_xcresult_payloads, replay_bundle, validate, validate_embed
from kmp_native_evidence import inventory


class InstalledAppEvidenceTest(unittest.TestCase):
    def test_empty_xcode_payload_is_hashed_but_empty_reports_and_other_objects_are_rejected(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            name = "data.0~eGoC90IBWQPGxv2FJVLScpEvR0DhWEdhiobiF_cfVBnSXhAxr-5YUxOJZESTTrBLkDpoWxRIt1XVb3Aa_pvizg=="
            data = root / "process/Workeeper.xcresult/Data"
            data.mkdir(parents=True)
            payload = data / name
            payload.touch()
            allowed = empty_xcresult_payloads(root)
            row, = inventory([payload], allowed_empty=allowed)
            self.assertEqual((row["path"], row["size"], row["sha256"]),
                             (str(payload), 0, hashlib.sha256(b"").hexdigest()))
            for path in (root / "process/summary.json", data / "data.0~other", root / name):
                path.touch()
                with self.assertRaisesRegex(ValueError, "empty required evidence"):
                    inventory([path], allowed_empty=allowed)

    def setUp(self):
        self.udid = "8ba0d52c-d4da-4d5e-9ddd-48a2edd61574"
        self.device = {"deviceId": self.udid, "architecture": "arm64", "platform": "iOS Simulator"}
        self.summary = {"devicesAndConfigurations": [{"device": self.device}], "result": "Passed",
                        "totalTestCount": len(REQUIRED), "passedTests": len(REQUIRED), "failedTests": 0,
                        "expectedFailures": 0, "skippedTests": 0}
        self.nodes = {"devices": [self.device], "testNodes": [
            {"nodeType": "Test Case", "result": "Passed",
             "nodeIdentifierURL": "test://com.apple.xcode/Workeeper/" + identity} for identity in sorted(REQUIRED)
        ]}

    def test_both_named_identities_must_pass_on_the_owned_device(self):
        self.assertEqual(set(validate(self.summary, self.nodes, self.udid)), REQUIRED)
        with self.assertRaises(ValueError):
            validate(self.summary, self.nodes, "another-simulator")
        for key, change in (("skippedTests", 1), ("expectedFailures", 1), ("totalTestCount", 0)):
            with self.assertRaises(ValueError):
                validate(dict(self.summary, **{key: change}), self.nodes, self.udid)
        for identity in (HOST, PERSISTENCE):
            altered = copy.deepcopy(self.nodes)
            case = next(n for n in altered["testNodes"] if n["nodeIdentifierURL"].endswith(identity))
            case["nodeIdentifierURL"] += "Renamed"
            with self.assertRaises(ValueError):
                validate(self.summary, altered, self.udid)

    def test_aggregate_replays_each_raw_bundle_and_rejects_swapped_or_unreadable_results(self):
        with TemporaryDirectory() as tmp:
            folder = Path(tmp)
            for view, data in (("summary", self.summary), ("tests", self.nodes)):
                (folder / f"{view}.json").write_text(json.dumps(data))
            bundle = "Recovery.xcresult"
            with patch("ios_app_results.subprocess.check_output") as extract:
                extract.side_effect = [json.dumps(self.summary), json.dumps(self.nodes)]
                self.assertEqual(replay_bundle(folder, bundle), [self.summary, self.nodes])
                for view, call in zip(("summary", "tests"), extract.call_args_list):
                    self.assertEqual(call.args[0], ["xcrun", "xcresulttool", "get", "test-results", view,
                                                     "--path", str(folder / bundle)])
                extract.side_effect = [json.dumps(dict(self.summary, result="Failed"))]
                with self.assertRaisesRegex(ValueError, "archived summary differs"):
                    replay_bundle(folder, bundle)
                extract.side_effect = [json.dumps(self.summary), json.dumps(dict(self.nodes, testNodes=[]))]
                with self.assertRaisesRegex(ValueError, "archived tests differs"):
                    replay_bundle(folder, bundle)
                extract.side_effect = subprocess.CalledProcessError(1, "xcresulttool")
                with self.assertRaises(subprocess.CalledProcessError):
                    replay_bundle(folder, bundle)

    def test_static_embed_requires_executed_link_assembly_resources_and_all_flags(self):
        log = ("Workeeper Gradle :app:ios:embedAndSignAppleFrameworkForXcode --rerun-tasks --no-build-cache "
               "--no-configuration-cache\n> Task :app:ios:linkDebugFrameworkIosSimulatorArm64\n"
               "> Task :app:ios:assembleDebugAppleFrameworkForXcodeIosSimulatorArm64\n"
               "> Task :app:ios:syncComposeResourcesForIos\n> Task :app:ios:embedAndSignAppleFrameworkForXcode SKIPPED\n"
               "300 actionable tasks: 300 executed\n")
        self.assertEqual(validate_embed(log), "300 actionable tasks: 300 executed")
        for altered in (log.replace("--no-build-cache", ""), log.replace("Arm64\n", "Arm64 FROM-CACHE\n"),
                        log.replace("300 executed", "299 executed, 1 up-to-date")):
            with self.assertRaises(ValueError):
                validate_embed(altered)
