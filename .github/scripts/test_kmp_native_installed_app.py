import copy
import unittest
from ios_app_results import HOST, PERSISTENCE, REQUIRED, validate, validate_embed


class InstalledAppEvidenceTest(unittest.TestCase):
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
