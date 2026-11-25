"""Validate installed-app XCTest evidence on the producer and aggregate."""
import json
import re
import subprocess

HOST = "WorkeeperTests/HostWindowTests/testSharedRootIsAttachedToKeyWindow"
PERSISTENCE = "WorkeeperUITests/TrainingPersistenceTests/testTemplateSurvivesProcessRelaunch"
PHOTO = "WorkeeperUITests/WorkeeperMediaUITests/testPickedImageSurvivesRelaunchInPreviewAndViewer"
RECOVERY = "WorkeeperUITests/TrainingPersistenceTests/testStartupOpensExpectedSurface"
REQUIRED = {
    HOST, PERSISTENCE, PHOTO,
    RECOVERY,
    "WorkeeperUITests/WorkeeperMediaUITests/testDeniedCameraCanOpenAppSettings",
    "WorkeeperUITests/WorkeeperMediaUITests/testSettingsHandsPrivacyLinkToSafari",
    "WorkeeperTests/HostWindowTests/testInstalledSharedAndFeatureResources",
    "WorkeeperTests/HostWindowTests/testImageReplacementAndSandboxRelocation",
    "WorkeeperTests/HostWindowTests/testUnavailableCameraAndRetiredPresenterFinishOnce",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def cases(nodes):
    for node in nodes:
        if node["nodeType"] == "Test Case":
            prefix = "test://com.apple.xcode/Workeeper/"
            identity = node["nodeIdentifierURL"]
            require(identity.startswith(prefix), "test belongs to another product")
            yield identity[len(prefix):], node["result"]
        yield from cases(node.get("children", []))


def validate(summary, hierarchy, device, expected_failure=None, required=REQUIRED):
    configs = summary["devicesAndConfigurations"]
    require(len(configs) == 1 and hierarchy["devices"] == [configs[0]["device"]], "unexpected test destinations")
    actual = configs[0]["device"]
    require(actual["deviceId"] == device and actual["architecture"] == "arm64" and
            actual["platform"] == "iOS Simulator", "wrong app simulator")
    observed = list(cases(hierarchy["testNodes"]))
    require(len(dict(observed)) == len(observed) == summary["totalTestCount"], "XCTest count/identity mismatch")
    require(summary["skippedTests"] == summary["expectedFailures"] == 0, "skipped or expected failures")
    if expected_failure is None:
        require(summary["result"] == "Passed" and summary["failedTests"] == 0 and
                len(observed) == summary["passedTests"] and required <= dict(observed).keys() and
                all(result == "Passed" for _, result in observed), "installed suite did not pass all identities")
    else:
        identity, message = expected_failure
        require(summary["result"] == "Failed" and observed == [(identity, "Failed")] and
                summary["failedTests"] == 1, "control did not fail the intended test")
        failures = summary["testFailures"]
        require(failures and all(message in failure["failureText"] for failure in failures),
                "control failed outside the intended assertion")
    return dict(observed)


def executed(log):
    summaries = re.findall(r"^(\d+) actionable tasks: ([^\n]+)$", log, re.MULTILINE)
    require(len(summaries) == 1, "missing or ambiguous Gradle summary")
    count, details = summaries[0]
    require(int(count) > 0 and details.strip() == f"{count} executed", "Gradle reused task outputs")
    return f"{count} actionable tasks: {count} executed"


def validate_embed(log):
    require("Workeeper Gradle :app:ios:embedAndSignAppleFrameworkForXcode --rerun-tasks --no-build-cache --no-configuration-cache" in log,
            "wrong Xcode Gradle invocation")
    for task in ("linkDebugFrameworkIosSimulatorArm64", "assembleDebugAppleFrameworkForXcodeIosSimulatorArm64",
                 "syncComposeResourcesForIos"):
        require(re.search(r"^> Task :app:ios:" + task + r"$", log, re.MULTILINE),
                f"missing executed framework or resources task: {task}")
    return executed(log)


def replay_bundle(folder, bundle_name):
    bundle = folder / bundle_name
    results = []
    for view in ("summary", "tests"):
        raw = subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", view, "--path", str(bundle)])
        decoded = json.loads(raw)
        require(decoded == json.loads((folder / f"{view}.json").read_text()),
                f"{folder.name}: archived {view} differs from {bundle_name}")
        results.append(decoded)
    return results


def validate_archive_contents(root, receipt):

    def read(path):
        return json.loads(path.read_text())

    runs = set()
    source = receipt["provenance"]
    executed((root / "contracts.log").read_text())
    previous_end = source["created_ns"] / 1e9
    for role, failure in (("entry", None), ("empty", (HOST, "Expected the production Compose hosting controller")),
                          ("process", (PERSISTENCE, "Post-relaunch template missing from Trainings")), ("positive", None)):
        folder = root / role
        details = read(folder / "run.json")
        udid = details["device"]["simulator"]
        require(udid not in runs, "controls must own separate simulators")
        runs.add(udid)
        require(details["exit"] == (65 if failure else 0) and details["scheme"] == "Workeeper" and
                details["bundle_id"] == "io.github.stslex.workeeper.dev", "wrong app execution")
        summary, hierarchy = replay_bundle(folder, "Workeeper.xcresult")
        require(previous_end <= summary["startTime"] < summary["finishTime"], "stale or out-of-order test run")
        previous_end = summary["finishTime"]
        simulator = summary["devicesAndConfigurations"][0]["device"]
        require(simulator["osVersion"] == details["device"]["version"] and
                simulator["osBuildNumber"] == details["device"]["build"], "simulator runtime differs from XCTest")
        actual = validate(summary, hierarchy, udid, failure)
        require(actual == details["identities"], "identity receipt differs")
        build = (folder / "build.log").read_text()
        require(validate_embed(build) == details["summary"],
                "installed app embed did not execute")
        testlog = (folder / "test.log").read_text()
        if role == "process":
            require("Initial unique template row saved" in testlog, "per-process control failed before relaunch")
        if role == "positive":
            require(receipt["summary"] == details["summary"], "aggregate build count differs")
            recovery = folder / "recovery"
            result, recovery_tests = replay_bundle(recovery, "Recovery.xcresult")
            require(result["startTime"] >= summary["finishTime"], "stale recovery run")
            require(validate(result, recovery_tests, udid, required={RECOVERY}) ==
                    {RECOVERY: "Passed"}, "unexpected recovery identities")
            sentinel = read(folder / "sentinel.json")
            require(sentinel["version"] == 87008 and sentinel["device"] == udid and sentinel["sentinel"],
                    "missing unsupported database evidence")
