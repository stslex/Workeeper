#!/usr/bin/env python3
"""Bundle identity gate: prove an AAB is the one its role claims before anything talks to Play.

Spec: documentation/feature-specs/wear-release-pipeline.md §6. One bundle per run:

    python3 .github/scripts/assert_play_bundle.py --aab <path> --role phone|wear \
        --toml gradle/libs.versions.toml
    python3 .github/scripts/assert_play_bundle.py --self-test

G1 exactly one existing, non-empty file at the path (a glob must match exactly one).
G2 package is io.github.stslex.workeeper.
G3 versionCode is the TOML value (phone) or 1_000_000 + the TOML value (wear).
G4 versionName is the TOML value (phone) or the TOML value + "-wear" (wear).
G5 phone: no uses-feature android.hardware.type.watch; wear: present and not required="false".
G6 wear: meta-data com.google.android.wearable.standalone is "false" (not applicable to phone).
G7 every lib/armeabi-v7a/*.so has the same file under lib/arm64-v8a/, in every module.

The manifest comes from the catalog-pinned bundletool: `./gradlew :bundletoolClasspath` writes the
classpath this script reads (ci-cd.md § "Bundle identity gate"). G7 reads the zip directly. Every
check prints what it read. Exit 0: all checks passed. Exit 1: a check failed. Exit 2: the gate
could not run (bad arguments, bundletool missing or failing).
"""

import argparse
import glob
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CLASSPATH = REPO_ROOT / "build" / "bundletool" / "classpath.txt"
FIXTURES = Path(__file__).resolve().parent / "fixtures" / "assert_play_bundle"

PACKAGE = "io.github.stslex.workeeper"
# GUARD: must equal WEAR_VERSION_CODE_OFFSET in ConfigureWearApplication.kt (spec §5, D2).
WEAR_VERSION_CODE_OFFSET = 1_000_000
WEAR_VERSION_NAME_SUFFIX = "-wear"
WATCH_FEATURE = "android.hardware.type.watch"
STANDALONE_META = "com.google.android.wearable.standalone"
ANDROID = "{http://schemas.android.com/apk/res/android}"
BUNDLETOOL_MAIN = "com.android.tools.build.bundletool.BundleToolMain"
ROLES = ("phone", "wear")
CHECKS = ("G1", "G2", "G3", "G4", "G5", "G6", "G7")
PASS, FAIL, SKIP, NOT_APPLICABLE = "PASS", "FAIL", "SKIP", "N/A"


class GateError(Exception):
    """The gate could not run at all; distinct from a check that ran and failed."""


def read_toml_identity(toml_path):
    """Return (versionName, versionCode) from the [versions] table."""
    try:
        versions = tomllib.loads(Path(toml_path).read_text(encoding="utf-8"))["versions"]
        name, code = versions["versionName"], versions["versionCode"]
    except (OSError, KeyError, tomllib.TOMLDecodeError) as error:
        raise GateError(f"cannot read versionName/versionCode from {toml_path}: {error}") from error
    if not isinstance(name, str) or not name or not isinstance(code, str) or not code.isdigit():
        raise GateError(f"{toml_path}: versionName={name!r} versionCode={code!r} is not X.Y.Z / digits")
    return name, int(code)


def expected_identity(role, toml_name, toml_code):
    if role == "wear":
        return WEAR_VERSION_CODE_OFFSET + toml_code, toml_name + WEAR_VERSION_NAME_SUFFIX
    return toml_code, toml_name


def check_single_file(pattern):
    """G1. Returns (status, detail, path or None)."""
    matches = sorted(glob.glob(pattern)) if glob.has_magic(pattern) else [pattern]
    matches = [match for match in matches if os.path.lexists(match)]
    if len(matches) != 1:
        return FAIL, f"{len(matches)} files matched {pattern!r}, expected exactly 1: {matches}", None
    path = Path(matches[0])
    if not path.is_file():
        return FAIL, f"{path} is not a regular file", None
    size = path.stat().st_size
    if size == 0:
        return FAIL, f"{path} is empty (0 bytes)", None
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return PASS, f"1 file: {path} ({size} bytes, sha256 {digest})", path


def bundletool_reader(classpath_file):
    """Return a function that dumps an AAB's base manifest with the pinned bundletool."""

    def read(aab):
        if not Path(classpath_file).is_file():
            raise GateError(f"{classpath_file} is missing; run ./gradlew :bundletoolClasspath first")
        jars = [line.strip() for line in Path(classpath_file).read_text().splitlines() if line.strip()]
        missing = [jar for jar in jars if not Path(jar).is_file()]
        if not jars or missing:
            raise GateError(f"{classpath_file}: {len(jars)} jars, missing {missing}; re-run :bundletoolClasspath")
        java = str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if os.environ.get("JAVA_HOME") else "java"
        pinned = next((Path(jar).name for jar in jars if Path(jar).name.startswith("bundletool-")), "?")
        print(f"bundletool: {pinned} ({len(jars)} jars) dump manifest --bundle {aab}")
        result = subprocess.run(
            [java, "-cp", os.pathsep.join(jars), BUNDLETOOL_MAIN, "dump", "manifest", "--bundle", str(aab)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise GateError(f"bundletool exited {result.returncode}: {result.stderr.strip()[-2000:]}")
        return result.stdout

    return read


def parse_manifest(xml_text):
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as error:
        raise GateError(f"manifest dump is not XML: {error}") from error
    if root.tag != "manifest":
        raise GateError(f"manifest dump root is <{root.tag}>, expected <manifest>")
    application = root.find("application")
    return {
        "package": root.get("package"),
        "versionCode": root.get(ANDROID + "versionCode"),
        "versionName": root.get(ANDROID + "versionName"),
        "watch": [
            feature.get(ANDROID + "required")
            for feature in root.findall("uses-feature")
            if feature.get(ANDROID + "name") == WATCH_FEATURE
        ],
        "standalone": [
            meta.get(ANDROID + "value")
            for meta in (application.findall("meta-data") if application is not None else [])
            if meta.get(ANDROID + "name") == STANDALONE_META
        ],
    }


def check_manifest(manifest, role, code, name):
    """G2..G6 over a parsed manifest. Returns {check: (status, detail)}."""
    results = {}
    package = manifest["package"]
    results["G2"] = (PASS if package == PACKAGE else FAIL, f"package {package!r}, expected {PACKAGE!r}")

    read_code = manifest["versionCode"]
    derivation = f" = {WEAR_VERSION_CODE_OFFSET} + TOML {code - WEAR_VERSION_CODE_OFFSET}" if role == "wear" else " = TOML"
    code_ok = read_code is not None and read_code.isdigit() and int(read_code) == code
    results["G3"] = (PASS if code_ok else FAIL, f"versionCode {read_code!r}, expected {code}{derivation}")

    read_name = manifest["versionName"]
    results["G4"] = (PASS if read_name == name else FAIL, f"versionName {read_name!r}, expected {name!r}")

    watch = manifest["watch"]
    if role == "phone":
        results["G5"] = (
            PASS if not watch else FAIL,
            f"uses-feature {WATCH_FEATURE}: {len(watch)} declared (required={watch}), expected 0",
        )
    else:
        # An absent android:required means required="true".
        required = len(watch) == 1 and watch[0] != "false"
        results["G5"] = (
            PASS if required else FAIL,
            f"uses-feature {WATCH_FEATURE}: {len(watch)} declared (required={watch}), expected 1, not required=\"false\"",
        )

    standalone = manifest["standalone"]
    if role == "phone":
        results["G6"] = (NOT_APPLICABLE, f"meta-data {STANDALONE_META}: {standalone} (checked for wear only)")
    else:
        results["G6"] = (
            PASS if standalone == ["false"] else FAIL,
            f"meta-data {STANDALONE_META}: {standalone}, expected exactly ['false']",
        )
    return results


def check_abi_parity(aab):
    """G7. Native libraries live at <module>/lib/<abi>/<name>.so inside an AAB."""
    try:
        with zipfile.ZipFile(aab) as bundle:
            names = bundle.namelist()
    except zipfile.BadZipFile as error:
        return FAIL, f"not a zip archive: {error}"
    libraries = {}
    for entry in names:
        parts = entry.split("/")
        if len(parts) == 4 and parts[1] == "lib" and parts[3].endswith(".so"):
            libraries.setdefault(parts[0], {}).setdefault(parts[2], set()).add(parts[3])
    total = sum(len(files) for abis in libraries.values() for files in abis.values())
    if not libraries:
        return PASS, "0 native libraries in 0 modules"
    lines, orphans = [], []
    for module in sorted(libraries):
        abis = libraries[module]
        counts = " ".join(f"{abi}={len(abis[abi])}" for abi in sorted(abis))
        lines.append(f"{module}: {counts}")
        missing = sorted(abis.get("armeabi-v7a", set()) - abis.get("arm64-v8a", set()))
        orphans.extend(f"{module}/lib/armeabi-v7a/{name}" for name in missing)
    detail = f"{total} native libraries in {len(libraries)} modules; " + "; ".join(lines)
    if orphans:
        return FAIL, detail + f"; no arm64-v8a twin for {orphans}"
    return PASS, detail


def evaluate(aab_pattern, role, toml_path, read_manifest):
    """Run G1..G7 and return {check: (status, detail)}. Raises GateError if the gate cannot run."""
    toml_name, toml_code = read_toml_identity(toml_path)
    code, name = expected_identity(role, toml_name, toml_code)
    print(f"role={role} toml={toml_path} versionName={toml_name!r} versionCode={toml_code}")
    status, detail, path = check_single_file(aab_pattern)
    results = {"G1": (status, detail)}
    if path is None:
        results.update({check: (SKIP, "G1 failed") for check in CHECKS[1:]})
        return results
    results.update(check_manifest(parse_manifest(read_manifest(path)), role, code, name))
    results["G7"] = check_abi_parity(path)
    return results


def report(results):
    for check in CHECKS:
        status, detail = results[check]
        print(f"{check} {status:4} {detail}")
    failed = [check for check in CHECKS if results[check][0] in (FAIL, SKIP)]
    ran = sum(1 for check in CHECKS if results[check][0] != SKIP)
    print(f"RESULT {'FAIL ' + ', '.join(failed) if failed else 'PASS'} ({ran}/{len(CHECKS)} checks ran on 1 bundle)")
    return 1 if failed else 0


def self_test():
    """Replay the committed fixture cases and require each check to be shown both PASS and FAIL."""
    cases = json.loads((FIXTURES / "cases.json").read_text(encoding="utf-8"))
    print(f"self-test: {len(cases)} cases from {FIXTURES.relative_to(REPO_ROOT)}")
    if not cases:
        print("self-test FAIL: zero cases")
        return 1
    mismatches, seen = [], {check: set() for check in CHECKS}
    for case in cases:
        manifest = (FIXTURES / case["manifest"]).read_text(encoding="utf-8")
        for old, new in case.get("replace", []):
            if manifest.count(old) != 1:
                print(f"self-test FAIL: {case['name']}: replacement anchor matched {manifest.count(old)} times")
                return 1
            manifest = manifest.replace(old, new)
        with tempfile.TemporaryDirectory() as tmp:
            pattern = build_fixture_bundle(Path(tmp), case)
            results = evaluate(pattern, case["role"], FIXTURES / case["toml"], lambda _path: manifest)
        actual = {check: results[check][0] for check in CHECKS}
        expected = {check: case["expect"].get(check, PASS) for check in CHECKS}
        for check, status in expected.items():
            seen[check].add(status)
        verdict = "ok" if actual == expected else "MISMATCH"
        print(f"  {verdict:8} {case['name']}: " + " ".join(f"{c}={actual[c]}" for c in CHECKS))
        if actual != expected:
            mismatches.append(case["name"])
            report(results)
    unproven = [check for check in CHECKS if not {PASS, FAIL} <= seen[check]]
    if unproven:
        print(f"self-test FAIL: no case shows both PASS and FAIL for {unproven}")
        return 1
    if mismatches:
        print(f"self-test FAIL: {len(mismatches)}/{len(cases)} cases mismatched: {mismatches}")
        return 1
    print(f"self-test PASS: {len(cases)} cases, every check shown both PASS and FAIL")
    return 0


def build_fixture_bundle(directory, case):
    """Materialise a case's bundle; the manifest is supplied separately, as bundletool would."""
    shape = case.get("bundle", "zip")
    if shape == "missing":
        return str(directory / "absent.aab")
    if shape == "empty":
        (directory / "empty.aab").write_bytes(b"")
        return str(directory / "empty.aab")
    names = ["bundle.aab", "other.aab"] if shape == "two" else ["bundle.aab"]
    for name in names:
        with zipfile.ZipFile(directory / name, "w") as bundle:
            bundle.writestr("base/manifest/AndroidManifest.xml", b"fixture")
            for entry in case.get("entries", []):
                bundle.writestr(entry, b"\x7fELF")
    return str(directory / "*.aab") if shape == "two" else str(directory / "bundle.aab")


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--aab", help="path (or glob that must match exactly one file) of the bundle")
    parser.add_argument("--role", choices=ROLES)
    parser.add_argument("--toml", default=str(REPO_ROOT / "gradle" / "libs.versions.toml"))
    parser.add_argument("--bundletool-classpath", default=str(DEFAULT_CLASSPATH))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            return self_test()
        if not args.aab or not args.role:
            parser.error("--aab and --role are required unless --self-test")
        return report(evaluate(args.aab, args.role, args.toml, bundletool_reader(args.bundletool_classpath)))
    except GateError as error:
        print(f"GATE ERROR (exit 2): {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
