#!/usr/bin/env python3
"""Bundle identity gate: prove an AAB is the one its role claims before anything talks to Play.

Spec: documentation/feature-specs/wear-release-pipeline.md §6 (G1–G7),
documentation/feature-specs/wear-paired-transport.md §9.2 item 4 (G8) and the 1.52.2 hotfix
(G9–G11, documentation/ci-cd.md § "Bundle identity gate"). One bundle per run:

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
G8 advertising ID off, for both roles: none of com.google.android.gms.permission.AD_ID,
   android.permission.ACCESS_ADSERVICES_AD_ID or android.permission.ACCESS_ADSERVICES_ATTRIBUTION
   in the base manifest (uses-permission or uses-permission-sdk-23), and the application meta-data
   google_analytics_adid_collection_enabled is exactly "false" (below Android 13 the ID is
   readable without the permission). The apps show no ads.
G9 both roles: the base resource table has array/android_wear_capabilities with exactly one
   configuration, (default), holding exactly one item: workeeper_phone_active_workout_v1 (phone)
   or workeeper_watch_active_workout_v1 (wear). Google Play services reads the array by name, so
   nothing in code references it and the release resource shrinker drops it unless a res/raw keep
   file names it. An absent array is a FAIL, not a gate error.
G10 phone: exactly one <service> has an intent filter with the action
   com.google.android.gms.wearable.REQUEST_RECEIVED; it is android:exported="true", and that
   filter's data is exactly scheme wear, host * and path /workeeper/wear/v1/rpc (no pathPrefix,
   pathPattern or any other data attribute). Not applicable to wear.
G11 both roles: application android:icon is @mipmap/ic_launcher, and that resource has an anydpi
   entry (density 65534) that is an XML file: the adaptive icon.

The manifest and the resources come from the catalog-pinned bundletool: `./gradlew
:bundletoolClasspath` writes the classpath this script reads (ci-cd.md § "Bundle identity gate").
`dump resources` merges the tables of every module with no module attribution, so G9 and G11
require base/resources.pb to be the bundle's only resource table, and a positive control
(string/app_name) must be found before an absent resource counts as absent. G7 reads the zip
directly. Every check prints what it read. Exit 0: all checks passed. Exit 1: a check failed.
Exit 2: the gate could not run (bad arguments, bundletool missing or failing, a resource dump it
cannot read).
"""

import argparse
import glob
import hashlib
import json
import os
import re
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
# The advertising ID and the Privacy Sandbox pair that firebase-analytics brings (§9.2 item 4).
AD_PERMISSIONS = (
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
)
ADID_COLLECTION_META = "google_analytics_adid_collection_enabled"
CAPABILITIES_RESOURCE = "array/android_wear_capabilities"
CAPABILITIES = {
    # GUARD: must equal WearProtocol.PHONE_CAPABILITY (core/wear-protocol) and the item of
    # feature/wear-bridge/src/main/res/values/wear_capabilities.xml.
    "phone": "workeeper_phone_active_workout_v1",
    # GUARD: must equal the android_wear_capabilities item of app/wear/src/main/res/values/strings.xml.
    "wear": "workeeper_watch_active_workout_v1",
}
# GUARD: must equal WearRpcListenerService.REQUEST_ACTION (MessageClient.ACTION_REQUEST_RECEIVED) and
# the action of the listener's intent filter in feature/wear-bridge/src/main/AndroidManifest.xml.
RPC_ACTION = "com.google.android.gms.wearable.REQUEST_RECEIVED"
# GUARD: the path must equal WearProtocol.RPC_PATH (core/wear-protocol), matched exactly.
RPC_DATA = {"host": ["*"], "path": ["/workeeper/wear/v1/rpc"], "scheme": ["wear"]}
LAUNCHER_ICON = "@mipmap/ic_launcher"
LAUNCHER_ICON_RESOURCE = "mipmap/ic_launcher"
# The anydpi density qualifier (0xfffe), as bundletool prints a configuration in protobuf text format.
ANYDPI_QUALIFIER = "density: 65534"
# The positive control of every resource dump: both apps label themselves with it.
CONTROL_RESOURCE = "string/app_name"
BASE_RESOURCE_TABLE = "base/resources.pb"
ANDROID = "{http://schemas.android.com/apk/res/android}"
BUNDLETOOL_MAIN = "com.android.tools.build.bundletool.BundleToolMain"
ROLES = ("phone", "wear")
CHECKS = ("G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "G10", "G11")
PASS, FAIL, SKIP, NOT_APPLICABLE = "PASS", "FAIL", "SKIP", "N/A"

# `dump resources --values` output: "Package '<name>':", then per entry "0x<id> - <type>/<name>" and one
# tab-indented "<configuration> - [<TYPE>] <value>" per configuration. A configuration with several
# qualifiers prints one qualifier per line, so the lines after the first carry no tab.
RESOURCE_HEADER = re.compile(r"0x[0-9a-f]{8} - (?P<name>[^/\s]+/\S+)")
CONFIG_VALUE = re.compile(r"(?P<config>.*?) - \[(?P<type>[A-Z_]+)\] (?P<value>.*)", re.DOTALL)
# An array prints as ["item", "item"]: each item quoted, quotes and backslashes escaped.
QUOTED_ITEM = re.compile(r'"((?:[^"\\]|\\.)*)"')


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


class BundletoolReader:
    """Dumps an AAB's base manifest, and one resource at a time, with the pinned bundletool."""

    def __init__(self, classpath_file):
        self.classpath_file = Path(classpath_file)

    def manifest(self, aab):
        return self._run(["dump", "manifest", "--bundle", str(aab)])

    def resource(self, aab, name):
        """The dump of one resource with its values; bundletool prints nothing when it is absent."""
        return self._run(["dump", "resources", "--bundle", str(aab), "--resource", name, "--values"])

    def _run(self, args):
        if not self.classpath_file.is_file():
            raise GateError(f"{self.classpath_file} is missing; run ./gradlew :bundletoolClasspath first")
        jars = [line.strip() for line in self.classpath_file.read_text().splitlines() if line.strip()]
        missing = [jar for jar in jars if not Path(jar).is_file()]
        if not jars or missing:
            raise GateError(f"{self.classpath_file}: {len(jars)} jars, missing {missing}; re-run :bundletoolClasspath")
        java = str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if os.environ.get("JAVA_HOME") else "java"
        pinned = next((Path(jar).name for jar in jars if Path(jar).name.startswith("bundletool-")), "?")
        print(f"bundletool: {pinned} ({len(jars)} jars) {' '.join(args)}")
        result = subprocess.run(
            [java, "-cp", os.pathsep.join(jars), BUNDLETOOL_MAIN, *args],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise GateError(f"bundletool exited {result.returncode}: {result.stderr.strip()[-2000:]}")
        return result.stdout


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
        "permissions": [
            permission.get(ANDROID + "name")
            for tag in ("uses-permission", "uses-permission-sdk-23")
            for permission in root.findall(tag)
        ],
        "adid_collection": [
            meta.get(ANDROID + "value")
            for meta in (application.findall("meta-data") if application is not None else [])
            if meta.get(ANDROID + "name") == ADID_COLLECTION_META
        ],
        "services": len(application.findall("service")) if application is not None else 0,
        "rpc_services": [
            {
                "name": service.get(ANDROID + "name"),
                "exported": service.get(ANDROID + "exported"),
                "filters": [data_attributes(intent_filter) for intent_filter in rpc_filters(service)],
            }
            for service in (application.findall("service") if application is not None else [])
            if rpc_filters(service)
        ],
        "icon": application.get(ANDROID + "icon") if application is not None else None,
    }


def rpc_filters(service):
    """The service's intent filters that carry the Wear RPC action."""
    return [
        intent_filter
        for intent_filter in service.findall("intent-filter")
        if any(action.get(ANDROID + "name") == RPC_ACTION for action in intent_filter.findall("action"))
    ]


def data_attributes(intent_filter):
    """Every attribute of the filter's <data> elements, by local name; Android combines them all."""
    attributes = {}
    for data in intent_filter.findall("data"):
        for key, value in data.attrib.items():
            attributes.setdefault(key.rsplit("}", 1)[-1], []).append(value)
    return {key: sorted(values) for key, values in sorted(attributes.items())}


def check_manifest(manifest, role, code, name):
    """G2..G6, G8 and G10 over a parsed manifest. Returns {check: (status, detail)}."""
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

    permissions = manifest["permissions"]
    declared = {name: permissions.count(name) for name in AD_PERMISSIONS}
    collection = manifest["adid_collection"]
    counts = ", ".join(f"{name} {count} times" for name, count in declared.items())
    results["G8"] = (
        PASS if not any(declared.values()) and collection == ["false"] else FAIL,
        f"{len(permissions)} permissions declared; {counts}, expected 0 each; "
        f"meta-data {ADID_COLLECTION_META}: {collection}, expected exactly ['false']",
    )
    results["G10"] = check_rpc_listener(manifest, role)
    return results


def check_rpc_listener(manifest, role):
    """G10. The phone end of the Wear transport: the one service Play services delivers requests to."""
    listeners = manifest["rpc_services"]
    read = (
        f"{manifest['services']} services, {len(listeners)} with action {RPC_ACTION}: "
        + ("; ".join(f"{s['name']} exported={s['exported']!r} filters={s['filters']}" for s in listeners) or "none")
    )
    if role != "phone":
        return NOT_APPLICABLE, read + " (checked for phone only)"
    problems = []
    if len(listeners) != 1:
        problems.append(f"expected exactly 1 such service, found {len(listeners)}")
    else:
        listener = listeners[0]
        if listener["exported"] != "true":
            problems.append(f"exported={listener['exported']!r}, expected 'true'")
        if listener["filters"] != [RPC_DATA]:
            problems.append(f"filter data {listener['filters']}, expected exactly [{RPC_DATA}]")
    return (FAIL, read + "; " + "; ".join(problems)) if problems else (PASS, read)


def resource_tables(aab):
    """Every module's resource table in the bundle: <module>/resources.pb."""
    try:
        with zipfile.ZipFile(aab) as bundle:
            names = bundle.namelist()
    except zipfile.BadZipFile as error:
        raise GateError(f"{aab} is not a zip archive: {error}") from error
    return sorted(name for name in names if len(name.split("/")) == 2 and name.endswith("/resources.pb"))


def parse_resource_dump(text, name):
    """`dump resources --resource <name> --values` -> [(qualifiers, type, value)], or None if absent.

    qualifiers is a tuple with one line per qualifier, ("(default)",) for the default configuration.

    bundletool prints nothing, and exits 0, for a resource the table lacks. Anything it prints that is
    not this one resource is a dump the gate cannot read: GateError, never a guess."""
    entries, configs = 0, []
    for line in text.splitlines():
        if not line or line.startswith("Package '"):
            continue
        header = RESOURCE_HEADER.fullmatch(line)
        if header:
            if header["name"] != name:
                raise GateError(f"the dump of {name} printed {header['name']}")
            entries += 1
        elif line.startswith("\t") and entries:
            configs.append(line[1:])
        elif configs:
            configs[-1] += "\n" + line
        else:
            raise GateError(f"the dump of {name} has an unexpected line: {line!r}")
    if entries == 0:
        return None
    if entries != 1:
        raise GateError(f"the dump of {name} printed it {entries} times; one resource table holds it once")
    parsed = []
    for config in configs:
        match = CONFIG_VALUE.fullmatch(config)
        if not match:
            raise GateError(f"the dump of {name} has a configuration it cannot parse: {config!r}")
        parsed.append((tuple(match["config"].split("\n")), match["type"], match["value"]))
    if not parsed:
        raise GateError(f"the dump of {name} printed the resource with no configuration")
    return parsed


def read_resources(reader, aab):
    """Dump the resources G9 and G11 read, after the two preconditions that make an absence mean absent."""
    tables = resource_tables(aab)
    if not tables:
        raise GateError(f"resource tables [], expected exactly [{BASE_RESOURCE_TABLE!r}]: nothing to read")
    if tables != [BASE_RESOURCE_TABLE]:
        raise GateError(
            f"resource tables {tables}, expected exactly [{BASE_RESOURCE_TABLE!r}]: dump resources merges every "
            "module's table without naming the module, so it cannot read the base table alone"
        )
    control = parse_resource_dump(reader.resource(aab, CONTROL_RESOURCE), CONTROL_RESOURCE)
    if control is None:
        raise GateError(f"positive control {CONTROL_RESOURCE} not found: the resource dump read nothing")
    print(f"resource table {BASE_RESOURCE_TABLE}: control {CONTROL_RESOURCE} has {len(control)} configurations")
    return {
        resource: parse_resource_dump(reader.resource(aab, resource), resource)
        for resource in (CAPABILITIES_RESOURCE, LAUNCHER_ICON_RESOURCE)
    }


def array_items(value):
    """The items of an [ARRAY] value, or None when the value is not a list of quoted strings."""
    if not (value.startswith("[") and value.endswith("]")):
        return None
    inner = value[1:-1]
    quoted = QUOTED_ITEM.findall(inner)
    if ", ".join(f'"{item}"' for item in quoted) != inner:
        return None
    return [re.sub(r"\\(.)", r"\1", item) for item in quoted]


def describe_dump(name, dump):
    if dump is None:
        return f"{name} absent from {BASE_RESOURCE_TABLE}"
    entries = "; ".join(f"{', '.join(qualifiers)} [{kind}] {value}" for qualifiers, kind, value in dump)
    return f"{name}: {len(dump)} configurations: {entries}"


def check_capabilities(dump, role):
    """G9. Play services discovers each end by this array; nothing else keeps it in a release build."""
    expected = [CAPABILITIES[role]]
    found = [(qualifiers, kind, array_items(value)) for qualifiers, kind, value in dump or []]
    ok = found == [(("(default)",), "ARRAY", expected)]
    return (PASS if ok else FAIL), (
        f"{describe_dump(CAPABILITIES_RESOURCE, dump)}; expected exactly 1 configuration, (default), with items {expected}"
    )


def check_launcher_icon(icon, dump):
    """G11. The launcher icon is the adaptive one: an anydpi XML entry behind @mipmap/ic_launcher."""
    adaptive = [
        value
        for qualifiers, kind, value in dump or []
        if ANYDPI_QUALIFIER in qualifiers and kind == "FILE" and value.endswith(".xml")
    ]
    ok = icon == LAUNCHER_ICON and len(adaptive) >= 1
    return (PASS if ok else FAIL), (
        f"application android:icon {icon!r}, expected {LAUNCHER_ICON!r}; "
        f"{describe_dump(LAUNCHER_ICON_RESOURCE, dump)}; {len(adaptive)} anydpi XML entries, expected at least 1"
    )


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


def evaluate(aab_pattern, role, toml_path, reader):
    """Run G1..G11 and return {check: (status, detail)}. Raises GateError if the gate cannot run."""
    toml_name, toml_code = read_toml_identity(toml_path)
    code, name = expected_identity(role, toml_name, toml_code)
    print(f"role={role} toml={toml_path} versionName={toml_name!r} versionCode={toml_code}")
    status, detail, path = check_single_file(aab_pattern)
    results = {"G1": (status, detail)}
    if path is None:
        results.update({check: (SKIP, "G1 failed") for check in CHECKS[1:]})
        return results
    manifest = parse_manifest(reader.manifest(path))
    results.update(check_manifest(manifest, role, code, name))
    results["G7"] = check_abi_parity(path)
    resources = read_resources(reader, path)
    results["G9"] = check_capabilities(resources[CAPABILITIES_RESOURCE], role)
    results["G11"] = check_launcher_icon(manifest["icon"], resources[LAUNCHER_ICON_RESOURCE])
    return results


def report(results):
    for check in CHECKS:
        status, detail = results[check]
        print(f"{check} {status:4} {detail}")
    failed = [check for check in CHECKS if results[check][0] in (FAIL, SKIP)]
    ran = sum(1 for check in CHECKS if results[check][0] != SKIP)
    print(f"RESULT {'FAIL ' + ', '.join(failed) if failed else 'PASS'} ({ran}/{len(CHECKS)} checks ran on 1 bundle)")
    return 1 if failed else 0


class FixtureReader:
    """Replays captured bundletool output in place of the pinned bundletool."""

    def __init__(self, manifest, resources):
        self._manifest, self._resources = manifest, resources

    def manifest(self, _aab):
        return self._manifest

    def resource(self, _aab, name):
        # A resource the table lacks: bundletool prints nothing and exits 0 (measured on 1.18.3).
        return self._resources.get(name, "")


class FixtureError(Exception):
    """A case the self-test cannot replay as written."""


def replaced(text, pairs, label):
    """Apply a case's exact-once text replacements; an anchor that does not match once is a broken case."""
    for old, new in pairs:
        if text.count(old) != 1:
            raise FixtureError(f"{label}: replacement anchor matched {text.count(old)} times")
        text = text.replace(old, new)
    return text


def fixture_reader(case, dumps):
    """A case's manifest plus the resource dumps of its bundle (the manifest's stem unless it names one)."""
    manifest = replaced((FIXTURES / case["manifest"]).read_text(encoding="utf-8"), case.get("replace", []), case["name"])
    bundle = case.get("resources", case["manifest"].split(".")[0])
    resources = {name: "\n".join(lines) for name, lines in dumps[bundle].items()}
    for name, pairs in case.get("resource_replace", {}).items():
        resources[name] = replaced(resources[name], pairs, f"{case['name']}: {name}")
    for name in case.get("resource_absent", []):
        if name not in resources:
            raise FixtureError(f"{case['name']}: resource_absent names {name}, which the fixture lacks")
        del resources[name]
    return FixtureReader(manifest, resources)


def self_test():
    """Replay the committed fixture cases and require each check to be shown both PASS and FAIL."""
    cases = json.loads((FIXTURES / "cases.json").read_text(encoding="utf-8"))
    dumps = json.loads((FIXTURES / "resources.json").read_text(encoding="utf-8"))
    print(f"self-test: {len(cases)} cases from {FIXTURES.relative_to(REPO_ROOT)}")
    if not cases:
        print("self-test FAIL: zero cases")
        return 1
    mismatches, seen, gate_errors = [], {check: set() for check in CHECKS}, 0
    for case in cases:
        try:
            reader = fixture_reader(case, dumps)
        except FixtureError as error:
            print(f"self-test FAIL: {error}")
            return 1
        with tempfile.TemporaryDirectory() as tmp:
            pattern = build_fixture_bundle(Path(tmp), case)
            try:
                results, error = evaluate(pattern, case["role"], FIXTURES / case["toml"], reader), None
            except GateError as gate_error:
                results, error = None, str(gate_error)
        if "gate_error" in case:
            gate_errors += 1
            ok = error is not None and case["gate_error"] in error
            verdict = "ok" if ok else "MISMATCH"
            print(f"  {verdict:8} {case['name']}: GATE ERROR {error!r}, expected one containing {case['gate_error']!r}")
            if not ok:
                mismatches.append(case["name"])
                if results is not None:
                    report(results)
            continue
        if error is not None:
            print(f"  MISMATCH {case['name']}: GATE ERROR {error!r}, expected check results")
            mismatches.append(case["name"])
            continue
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
    print(
        f"self-test PASS: {len(cases)} cases ({gate_errors} expecting a gate error), "
        "every check shown both PASS and FAIL"
    )
    return 0


def build_fixture_bundle(directory, case):
    """Materialise a case's bundle; the manifest and the resource dumps are supplied separately, as
    bundletool would. The zip holds base/resources.pb unless the case lists its resource tables."""
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
            for table in case.get("resource_tables", [BASE_RESOURCE_TABLE]):
                bundle.writestr(table, b"fixture")
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
        return report(evaluate(args.aab, args.role, args.toml, BundletoolReader(args.bundletool_classpath)))
    except GateError as error:
        print(f"GATE ERROR (exit 2): {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
