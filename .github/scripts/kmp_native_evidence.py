"""Provenance and archive validation for the two independent Native execution jobs."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import time
from tempfile import TemporaryDirectory
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import UUID
from contextlib import contextmanager
import zipfile

from kmp_native_model import DEVICE_START, MODEL, SIMULATOR_START, load_model, read_json, upload_pattern

ROOT = Path("build/kmp-verification")
ROLES = ("simulator", "device")
JOB_NAMES = {"simulator": "KMP iOS simulator", "device": "KMP iOS device links"}
TOOLCHAIN_COMMANDS = {
    "xcode": ("xcodebuild", "-version"),
    "jdk": ("java", "-version"),
    "macos_build": ("sw_vers", "-buildVersion"),
    "architecture": ("uname", "-m"),
    "iphonesimulator_sdk": ("xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"),
    "iphonesimulator_sdk_path": ("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"),
    "iphonesimulator_sdk_build": ("xcrun", "--sdk", "iphonesimulator", "--show-sdk-build-version"),
    "iphoneos_sdk": ("xcrun", "--sdk", "iphoneos", "--show-sdk-version"),
    "iphoneos_sdk_build": ("xcrun", "--sdk", "iphoneos", "--show-sdk-build-version"),
}
GRADLE_INPUTS = (
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/kmp-native-targets.txt",
    "gradle.properties", "build-logic/gradle.properties",
)
GITHUB_API = "https://api.github.com"



@contextmanager
def chdir(directory):
    previous = Path.cwd()
    try:
        os.chdir(directory)
        yield
    finally:
        os.chdir(previous)


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT).strip()


def nonempty_string(value):
    return isinstance(value, str) and bool(value.strip())


def validate_toolchain(toolchain, role):
    require(isinstance(toolchain, dict), f"{role}: missing toolchain")
    for name in (*TOOLCHAIN_COMMANDS, "runner_image"):
        require(nonempty_string(toolchain.get(name)), f"{role}: missing toolchain field {name}")
    inputs = toolchain.get("inputs")
    require(isinstance(inputs, dict), f"{role}: missing Gradle input hashes")
    for name in GRADLE_INPUTS:
        value = inputs.get(name)
        require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value),
                f"{role}: missing or invalid Gradle input hash {name}")


def compare_toolchains(simulator, device):
    validate_toolchain(simulator, "simulator")
    validate_toolchain(device, "device")
    print(f"Native runner images: simulator={simulator['runner_image']!r}, device={device['runner_image']!r}")
    differences = sorted(name for name in (simulator.keys() | device.keys()) - {"runner_image"}
                         if name not in simulator or name not in device
                         or json.dumps(simulator[name], sort_keys=True) != json.dumps(device[name], sort_keys=True))
    require(not differences, f"child toolchain drift: {', '.join(differences)}")


def owned_simulator_runtime(udid, inventory):
    matches = [identifier for identifier, devices in inventory["devices"].items()
               for device in devices if device["udid"] == udid and device["state"] == "Booted"]
    require(len(matches) == 1, "owned simulator must be booted under one runtime")
    identifier = matches[0]
    runtimes = [runtime for runtime in inventory["runtimes"]
                if runtime["identifier"] == identifier and runtime.get("isAvailable")]
    require(len(runtimes) == 1, "owned simulator runtime is unavailable or ambiguous")
    runtime = runtimes[0]
    return {"simulator": udid, "identifier": identifier, "version": runtime["version"],
            "build": runtime["buildversion"]}


def validate_simulator_runtime(source, apple):
    observed = source.get("simulator_runtime")
    require(isinstance(observed, dict) and all(nonempty_string(observed.get(key))
            for key in ("simulator", "identifier", "version", "build")), "missing owned simulator runtime")
    require(observed["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-"),
            "owned simulator runtime is not iOS")
    UUID(observed["simulator"])
    build = Path(apple["buildDirectory"])
    for proof in ("install-epoch-relaunch/run.json", "install-epoch-sync/receipt.json"):
        require(read_json(build / proof)["simulator"] == observed["simulator"],
                "owned simulator runtime differs from storage proof simulator")


def provenance(role):
    source = command("git", "rev-parse", "HEAD")
    tree = command("git", "rev-parse", "HEAD^{tree}")
    require(source == os.environ["NATIVE_SOURCE_SHA"], "checkout differs from resolved source")
    require(tree == os.environ["NATIVE_SOURCE_TREE"], "checkout tree differs from resolved source")
    toolchain = {name: command(*args) for name, args in TOOLCHAIN_COMMANDS.items()}
    toolchain["runner_image"] = os.environ["ImageVersion"]
    toolchain["inputs"] = {name: digest(Path(name)) for name in GRADLE_INPUTS}
    validate_toolchain(toolchain, role)
    require(toolchain["xcode"].splitlines()[0] == "Xcode 26.6", "Native CI requires Xcode 26.6")
    source_record = {
        "role": role, "source_sha": source, "source_tree": tree,
        "workflow_sha": os.environ["GITHUB_WORKFLOW_SHA"],
        "workflow_ref": os.environ["GITHUB_WORKFLOW_REF"],
        "event_sha": os.environ["GITHUB_SHA"],
        "repository": os.environ["GITHUB_REPOSITORY"],
        "run_id": int(os.environ["GITHUB_RUN_ID"]),
        "attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
        "created_ns": time.time_ns(), "workspace": str(Path.cwd()),
        "source_workflow_sha256": digest(Path(".github/workflows/android_build_unified.yml")),
        "toolchain": toolchain,
    }
    if role == "simulator":
        source_record["simulator_runtime"] = owned_simulator_runtime(
            os.environ["WORKEEPER_DATABASE_TEST_SIMULATOR"], json.loads(command("xcrun", "simctl", "list", "-j")),
        )
    return source_record


def validate_gradle(role, rows):
    execution = read_json(ROOT / f"{role}-execution.json")
    require(execution["exit_code"] == 0, "Native Gradle process failed")
    require(execution["role"] == role, "wrong Native execution phase")
    log = (ROOT / f"{role}-gradle.log").read_text()
    summaries = re.findall(r"^(\d+) actionable tasks: (\d+) executed\s*$", log, re.MULTILINE)
    all_summaries = re.findall(r"^\d+ actionable tasks:.*$", log, re.MULTILINE)
    require(len(summaries) == len(all_summaries) == 1, "missing or reused Gradle task outputs")
    total, executed = summaries[0]
    require(int(total) == int(executed) > 0, "empty or partially executed Gradle gate")
    field = "simulatorTask" if role == "simulator" else "deviceLinkTask"
    tasks = [row[field] for row in rows]
    for task in tasks:
        statuses = re.findall(r"^> Task " + re.escape(task) + r"([^\r\n]*)$", log, re.MULTILINE)
        require(statuses and all(not status.strip() for status in statuses), f"{task}: terminal did not execute")
    expected_flags = {"--rerun-tasks", "--no-build-cache", "--no-configuration-cache",
                      "--continue", "--full-stacktrace", "--console=plain"}
    if role == "simulator":
        expected_flags.add("--no-parallel")
    require(expected_flags.issubset(execution["arguments"]), "missing forced-execution flags")
    require([arg for arg in execution["arguments"] if arg.startswith(":")] == tasks, "incorrect Native task selection")
    return all_summaries[0]


def evidence_paths(role, rows):
    marker = SIMULATOR_START if role == "simulator" else DEVICE_START
    paths = [MODEL, marker, ROOT / f"{role}-provenance.json", ROOT / f"{role}-gradle.log",
             ROOT / f"{role}-execution.json", Path("gradle/kmp-native-identities.json")]
    apple = next(row for row in rows if row["module"] == ":app:ios")
    if role == "device":
        paths += [ROOT / "device-inspection.json", Path(apple["deviceBinary"])]
    else:
        for row in rows:
            results = sorted(Path(row["simulatorResults"]).glob("TEST-*.xml"))
            require(results, f"{row['module']}: missing simulator XML")
            paths.extend(results)
        database = next(row for row in rows if row["module"] == ":core:data:database")
        fixtures = Path(database["buildDirectory"]) / "native-fixtures"
        for version in (5, 6, 8):
            directories = [path for path in fixtures.glob(f"v{version}-*") if path.is_dir()]
            require(len(directories) == 1, f"missing or duplicate v{version} pre-open fixture")
            for name in ("pre-open.db", "schema.json", "source-blob.txt"):
                path = directories[0] / name
                require(path.stat().st_mtime_ns >= marker.stat().st_mtime_ns, f"stale database fixture: {path}")
                paths.append(path)
        epoch = Path(apple["buildDirectory"]) / "install-epoch-relaunch"
        paths += [epoch / name for name in ("run.json", "launch-1.json", "launch-1.log", "launch-2.json", "launch-2.log")]
        sync = Path(apple["buildDirectory"]) / "install-epoch-sync"
        paths += [sync / name for name in ("receipt.json", "native.log", "operations.jsonl", "observer.dylib")]
        paths.append(Path(apple["simulatorBinary"]))
    return paths


def inventory(paths):
    result = []
    for path in paths:
        require(not path.is_symlink() and path.is_file(), f"required evidence is absent or linked: {path}")
        require(path.stat().st_size > 0 or path in (SIMULATOR_START, DEVICE_START), f"empty required evidence: {path}")
        result.append({"path": path.as_posix(), "size": path.stat().st_size,
                       "mtime_ns": path.stat().st_mtime_ns, "sha256": digest(path)})
    require(len({row["path"] for row in result}) == len(result), "duplicate evidence path")
    return result


def emit_outputs(paths):
    patterns = "\n".join(upload_pattern(path.as_posix()) for path in paths)
    if output := os.environ.get("GITHUB_OUTPUT"):
        with open(output, "a") as stream:
            stream.write("paths<<NATIVE_EVIDENCE_PATHS\n" + patterns + "\nNATIVE_EVIDENCE_PATHS\n")


def collect(role):
    receipt = {"valid": False, "role": role}
    paths = []
    rows = []
    failure = None
    try:
        rows = load_model()
        receipt["provenance"] = read_json(ROOT / f"{role}-provenance.json")
        receipt["summary"] = validate_gradle(role, rows)
        receipt["modules"] = [row["module"] for row in rows]
        if role == "device":
            from kmp_native_model import check_device_links
            inspected = check_device_links(rows)
            (ROOT / "device-inspection.json").write_text(json.dumps(inspected, indent=2) + "\n")
            receipt["inspected_binaries"] = inspected
            receipt["archived_binaries"] = [next(row["deviceBinary"] for row in rows if row["module"] == ":app:ios")]
        else:
            from assert_kmp_ios_smoke import main as verify_identities
            from prove_ios_install_epoch_sync import validate_receipt
            verify_identities()
            apple = next(row for row in rows if row["module"] == ":app:ios")
            validate_receipt(
                Path(apple["buildDirectory"]) / "install-epoch-sync", Path(apple["simulatorBinary"]),
                toolchain=receipt["provenance"]["toolchain"],
            )
            validate_simulator_runtime(receipt["provenance"], apple)
        paths = evidence_paths(role, rows)
        receipt["files"] = inventory(paths)
        receipt["valid"] = True
    except (OSError, ValueError, KeyError, StopIteration, SystemExit, subprocess.CalledProcessError) as error:
        failure = error
        receipt["error"] = str(error)
        paths = failure_paths(rows)
    finally:
        ROOT.mkdir(parents=True, exist_ok=True)
        target = ROOT / f"{role}-receipt.json"
        target.write_text(json.dumps(receipt, indent=2) + "\n")
        emit_outputs(list(dict.fromkeys([target, *paths])))
    if failure is not None:
        raise ValueError(f"{role} evidence invalid: {failure}")


def failure_paths(rows):
    roots = [ROOT, Path("gradle/kmp-native-identities.json")]
    for row in rows:
        roots.append(Path(row["simulatorResults"]))
        if row["module"] == ":app:ios":
            roots.extend(Path(row["buildDirectory"]) / name for name in
                         ("install-epoch-relaunch", "install-epoch-sync"))
            roots.extend(Path(row[field]) for field in ("simulatorBinary", "deviceBinary"))
        if row["module"] == ":core:data:database":
            roots.append(Path(row["buildDirectory"]) / "native-fixtures")
    return [file for root in roots for file in ([root] if root.is_file() else root.rglob("*"))
            if file.is_file() and not file.is_symlink()]


def validate_device_inspection(receipt, rows):
    inspected = read_json(ROOT / "device-inspection.json")
    require(inspected == receipt["inspected_binaries"], "device inspections differ")
    require([item["module"] for item in inspected] == [row["module"] for row in rows], "incomplete device inspection")
    marker = DEVICE_START.stat().st_mtime_ns
    for row, item in zip(rows, inspected):
        require(item["path"] == row["deviceBinary"], "inspected the wrong device binary")
        require(item["size"] > 0 and item["mtime_ns"] > item["marker_mtime_ns"] == marker,
                "missing or stale device inspection")
        require(re.fullmatch(r"[a-f0-9]{64}", item["sha256"]), "missing device binary digest")
        platforms = [parts[1] for line in item["vtool"].splitlines()
                     if len(parts := line.split()) == 2 and parts[0] == "platform"]
        require(platforms == ["IOS"], "wrong device platform")
    archived = [row["deviceBinary"] for row in rows if row["module"] == ":app:ios"]
    require(receipt["archived_binaries"] == archived, "incorrect archived device inventory")
    for item in inspected:
        if item["path"] in archived:
            require(digest(Path(item["path"])) == item["sha256"], "archived device differs from inspection")


def validate_contents(receipt, source_workflow_digest, identities_digest):
    role, source = receipt["role"], receipt["provenance"]
    require(digest(Path("gradle/kmp-native-identities.json")) == identities_digest, "identity roster differs from source")
    require(source["source_workflow_sha256"] == source_workflow_digest, "source workflow differs")
    require(read_json(ROOT / f"{role}-provenance.json") == source, "provenance files differ")
    rows = load_model()
    expected_paths = {path.as_posix() for path in evidence_paths(role, rows)}
    require(expected_paths == {item["path"] for item in receipt["files"]}, "required evidence inventory differs")
    require(validate_gradle(role, rows) == receipt["summary"], "Gradle receipt differs from full log")
    marker = SIMULATOR_START if role == "simulator" else DEVICE_START
    require(source["created_ns"] <= marker.stat().st_mtime_ns, "marker predates this checkout")
    if role == "device":
        validate_device_inspection(receipt, rows)
    else:
        from assert_kmp_ios_smoke import main as verify_identities
        from prove_ios_install_epoch_sync import validate_receipt
        verify_identities(invocation_root=source["workspace"])
        apple = next(row for row in rows if row["module"] == ":app:ios")
        binary = Path(apple["simulatorBinary"])
        require(binary.stat().st_mtime_ns > marker.stat().st_mtime_ns, "stale simulator executable")
        sync = Path(apple["buildDirectory"]) / "install-epoch-sync"
        require(read_json(sync / "receipt.json")["started_ns"] > marker.stat().st_mtime_ns, "stale sync execution")
        validate_receipt(sync, binary, toolchain=source["toolchain"], invocation_root=source["workspace"])
        validate_simulator_runtime(source, apple)


def validate_archive(data, expected_digest, role, intended, modules):
    archive_digest = digest(data) if isinstance(data, Path) else hashlib.sha256(data).hexdigest()
    require(archive_digest == expected_digest.removeprefix("sha256:"), "artifact digest mismatch")
    source_workflow_digest = digest(Path(".github/workflows/android_build_unified.yml"))
    identities_digest = digest(Path("gradle/kmp-native-identities.json"))
    with zipfile.ZipFile(data if isinstance(data, Path) else io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(set(names)) == len(names), "duplicate ZIP member")
        for entry in archive.infolist():
            path = PurePosixPath(entry.filename)
            require(not path.is_absolute() and ".." not in path.parts
                    and path.as_posix() == entry.filename and not entry.is_dir(), "escaping or invalid ZIP member")
            require(not stat.S_ISLNK(entry.external_attr >> 16), "linked ZIP member")
        receipt_name = f"build/kmp-verification/{role}-receipt.json"
        receipt = json.loads(archive.read(receipt_name))
        require(receipt["valid"] is True and receipt["role"] == role, "invalid child receipt")
        source = receipt["provenance"]
        require(source["role"] == role, "wrong provenance role")
        for key, value in intended.items():
            if key != "attempt":
                require(source.get(key) == value, f"{key}: inconsistent child provenance")
        require(type(source["attempt"]) is int and 1 <= source["attempt"] <= intended["attempt"], "invalid child attempt")
        require(receipt["modules"] == modules, "incomplete Native module coverage")
        require(re.fullmatch(r"([1-9]\d*) actionable tasks: \1 executed", receipt["summary"]), "invalid task summary")
        files = receipt["files"]
        require(files and len({row["path"] for row in files}) == len(files), "empty or duplicate receipt inventory")
        require(set(names) == {receipt_name, *(row["path"] for row in files)}, "archive inventory differs from receipt")
        validate_toolchain(source.get("toolchain"), role)
        require(source["toolchain"]["xcode"].splitlines()[0] == "Xcode 26.6", "wrong Xcode")
        with TemporaryDirectory(prefix="native-evidence-") as directory:
            root = Path(directory)
            for row in files:
                target = root / row["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(row["path"]) as stream, target.open("wb") as out:
                    import shutil
                    shutil.copyfileobj(stream, out)
                require(target.stat().st_size == row["size"] and digest(target) == row["sha256"], "evidence bytes changed")
                os.utime(target, ns=(row["mtime_ns"], row["mtime_ns"]))
            with chdir(root):
                validate_contents(receipt, source_workflow_digest, identities_digest)
        return receipt


class GitHubApiRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        destination = urlsplit(newurl)
        if (destination.scheme, destination.netloc) != ("https", "api.github.com"):
            raise HTTPError(req.full_url, code, "redirect outside the GitHub API", headers, fp)
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None:
            redirected.add_unredirected_header("Authorization", req.get_header("Authorization"))
        return redirected


def api(operation, path, token):
    request = Request(f"{GITHUB_API}/{path}", headers={"Accept": "application/vnd.github+json"})
    # urllib copies normal headers across redirects. Only the origin-checking handler may forward auth.
    request.add_unredirected_header("Authorization", f"Bearer {token}")
    try:
        with build_opener(GitHubApiRedirect()).open(request, timeout=30) as response:
            status = response.getcode()
            body = response.read()
    except HTTPError as error:
        raise ValueError(f"{operation}: GitHub API HTTP {error.code}") from None
    except (URLError, OSError, ValueError):
        raise ValueError(f"{operation}: GitHub API unavailable") from None
    try:
        return json.loads(body)
    except ValueError:
        raise ValueError(f"{operation}: invalid GitHub API response (HTTP {status})") from None


def child_outputs(needs):
    require(needs["native-source"]["result"] == "success", "source resolution did not succeed")
    result = {}
    for role in ROLES:
        child = needs[f"kmp-ios-{role}"]
        require(child["result"] == "success", f"{role} child result is {child['result']}")
        outputs = child["outputs"]
        require(re.fullmatch(r"[1-9]\d*", outputs.get("artifact_id", "")), "missing artifact identifier")
        require(re.fullmatch(r"[a-f0-9]{64}", outputs.get("artifact_digest", "")), "missing artifact digest")
        result[role] = outputs
    return result


def validate_metadata(metadata, outputs, intended, event_head):
    require(str(metadata["id"]) == outputs["artifact_id"] and not metadata["expired"], "wrong or expired artifact")
    require(metadata["digest"] == "sha256:" + outputs["artifact_digest"], "upload and GitHub artifact digests differ")
    require(metadata["workflow_run"]["id"] == intended["run_id"], "artifact belongs to another run")
    require(metadata["workflow_run"]["head_sha"] == event_head, "artifact belongs to another event commit")


def validate_producer(jobs, role):
    require(jobs["total_count"] == len(jobs["jobs"]), "incomplete producer job listing")
    producers = [job for job in jobs["jobs"] if job["name"] == JOB_NAMES[role]
                 or job["name"].endswith(" / " + JOB_NAMES[role])]
    require(len(producers) == 1 and producers[0]["conclusion"] == "success", "producing job did not succeed")


def aggregate(needs):
    outputs = child_outputs(needs)
    token = os.environ.get("GITHUB_TOKEN")
    require(nonempty_string(token), "aggregate GitHub API requires GITHUB_TOKEN")
    intended = {
        "source_sha": os.environ["NATIVE_SOURCE_SHA"], "source_tree": os.environ["NATIVE_SOURCE_TREE"],
        "workflow_sha": os.environ["GITHUB_WORKFLOW_SHA"], "workflow_ref": os.environ["GITHUB_WORKFLOW_REF"],
        "event_sha": os.environ["GITHUB_SHA"], "repository": os.environ["GITHUB_REPOSITORY"],
        "run_id": int(os.environ["GITHUB_RUN_ID"]), "attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
    }
    require(command("git", "rev-parse", "HEAD") == intended["source_sha"], "aggregate checkout differs")
    require(command("git", "rev-parse", "HEAD^{tree}") == intended["source_tree"], "aggregate source tree differs")
    modules = [":" + module for module in read_json(Path("gradle/kmp-native-identities.json"))]
    receipts = []
    for role in ROLES:
        output = outputs[role]
        metadata = api(f"{role} artifact metadata",
                       f"repos/{intended['repository']}/actions/artifacts/{output['artifact_id']}", token)
        validate_metadata(metadata, output, intended, os.environ["NATIVE_EVENT_HEAD"])
        receipt = validate_archive(ROOT / f"{role}.zip", output["artifact_digest"], role, intended, modules)
        attempt = receipt["provenance"]["attempt"]
        require(metadata["name"] == f"kmp-ios-{role}-{intended['run_id']}-{attempt}", "incorrect artifact phase/attempt")
        jobs = api(f"{role} producer jobs",
                   f"repos/{intended['repository']}/actions/runs/{intended['run_id']}/attempts/{attempt}/jobs?per_page=100",
                   token)
        validate_producer(jobs, role)
        receipts.append(receipt)
    compare_toolchains(*(receipt["provenance"]["toolchain"] for receipt in receipts))
    print("Both Native execution jobs succeeded with verified archives for " + intended["source_sha"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("start", "collect", "aggregate"))
    parser.add_argument("role", nargs="?", choices=ROLES)
    args = parser.parse_args()
    try:
        if args.command == "aggregate":
            aggregate(json.loads(os.environ["NATIVE_NEEDS"]))
        elif args.command == "start":
            ROOT.mkdir(parents=True, exist_ok=True)
            (ROOT / f"{args.role}-provenance.json").write_text(json.dumps(provenance(args.role), indent=2) + "\n")
        else:
            collect(args.role)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        raise SystemExit(f"Native evidence: {error}") from error


if __name__ == "__main__":
    main()
