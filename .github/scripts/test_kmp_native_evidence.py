from contextlib import redirect_stdout
import copy
from email.message import Message
import hashlib
import io
import json
from pathlib import Path
import stat
import uuid
import xml.etree.ElementTree as ET
import unittest
from unittest.mock import patch
from urllib.error import URLError
from urllib.request import HTTPHandler, HTTPSHandler, ProxyHandler, build_opener
from urllib.response import addinfourl
import zipfile

from kmp_native_evidence import (
    GRADLE_INPUTS, api, child_outputs, compare_toolchains, digest, owned_simulator_runtime,
    validate_archive, validate_metadata, validate_producer,
)
from kmp_native_model import read_json


def encoded(value):
    return json.dumps(value).encode()


HISTORICAL_TOOLCHAINS = Path(__file__).with_name("fixtures") / "kmp_native_attempt_3_toolchains.json"
# Attempt 3 predates these fields. Use the same synthetic values for both receipts.
NEW_TOOLCHAIN_FIELDS = {"macos_build": "25G332", "architecture": "arm64", "iphonesimulator_sdk_build": "23F81a"}


def completed_toolchain(role):
    return {**read_json(HISTORICAL_TOOLCHAINS)[role], **NEW_TOOLCHAIN_FIELDS}


class NativeToolchainTest(unittest.TestCase):
    def test_identical_toolchains(self):
        simulator = completed_toolchain("simulator")
        with redirect_stdout(io.StringIO()):
            compare_toolchains(simulator, copy.deepcopy(simulator))

    def test_attempt_three_image_only_drift_passes_and_reports_both_images(self):
        history = read_json(HISTORICAL_TOOLCHAINS)
        simulator, device = history["simulator"], history["device"]
        changes = {name for name in simulator.keys() | device.keys() if simulator.get(name) != device.get(name)}
        self.assertEqual({"runner_image"}, changes)
        output = io.StringIO()
        with redirect_stdout(output):
            compare_toolchains(completed_toolchain("simulator"), completed_toolchain("device"))
        self.assertIn("simulator='20261102.0473.1', device='20261117.0516.1'", output.getvalue())

    def test_non_image_toolchain_drift_fails(self):
        for field in ("xcode", "iphonesimulator_sdk_build", "iphoneos_sdk_build", "architecture", "macos_build"):
            with self.subTest(field=field), redirect_stdout(io.StringIO()):
                device = dict(completed_toolchain("device"), **{field: "different"})
                with self.assertRaisesRegex(ValueError, f"child toolchain drift: {field}"):
                    compare_toolchains(completed_toolchain("simulator"), device)

    def test_required_fields_and_gradle_input_hashes_cannot_be_missing_on_both_sides(self):
        for field in completed_toolchain("simulator"):
            with self.subTest(field=field):
                incomplete = completed_toolchain("simulator")
                del incomplete[field]
                with self.assertRaisesRegex(ValueError, "missing"):
                    compare_toolchains(incomplete, copy.deepcopy(incomplete))
        for path in GRADLE_INPUTS:
            with self.subTest(path=path):
                incomplete = completed_toolchain("simulator")
                del incomplete["inputs"][path]
                with self.assertRaisesRegex(ValueError, "missing or invalid Gradle input hash"):
                    compare_toolchains(incomplete, copy.deepcopy(incomplete))

    def test_hash_drift_and_extra_toolchain_fields_fail(self):
        device = completed_toolchain("device")
        device["inputs"][GRADLE_INPUTS[0]] = "0" * 64
        with redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "child toolchain drift: inputs"):
            compare_toolchains(completed_toolchain("simulator"), device)
        device = dict(completed_toolchain("device"), compiler="unexpected")
        with redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "child toolchain drift: compiler"):
            compare_toolchains(completed_toolchain("simulator"), device)

    def test_shared_extra_fields_preserve_json_type(self):
        simulator = dict(completed_toolchain("simulator"), extension=True)
        device = dict(completed_toolchain("device"), extension=1)
        with redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "child toolchain drift: extension"):
            compare_toolchains(simulator, device)

    def test_owned_runtime_is_selected_by_booted_device_identity(self):
        identifier = "com.apple.CoreSimulator.SimRuntime.iOS-26-5"
        udid = str(uuid.uuid4())
        inventory = {"devices": {"other": [{"udid": "different", "state": "Booted"}],
                                identifier: [{"udid": udid, "state": "Booted"}]},
                     "runtimes": [{"identifier": identifier, "version": "26.5",
                                   "buildversion": "23F81a", "isAvailable": True}]}
        self.assertEqual({"simulator": udid, "identifier": identifier, "version": "26.5", "build": "23F81a"},
                         owned_simulator_runtime(udid, inventory))
        inventory["devices"][identifier][0]["state"] = "Shutdown"
        with self.assertRaisesRegex(ValueError, "owned simulator must be booted"):
            owned_simulator_runtime(udid, inventory)


class NativeApiTest(unittest.TestCase):
    token = "api-token-sentinel"
    artifact_path = "repos/owner/project/actions/artifacts/12"

    def mock_requests(self, sequence, operation="simulator artifact metadata"):
        calls = []
        responses = iter(sequence)

        def receive(request):
            calls.append((request.full_url, request.get_header("Authorization")))
            reply = next(responses)
            if isinstance(reply, Exception):
                raise reply
            status, location, data = reply
            headers = Message()
            if location:
                headers["Location"] = location
            response = addinfourl(io.BytesIO(data), headers, request.full_url, status)
            response.msg = "server response"
            return response

        class MockHttp(HTTPHandler):
            http_open = staticmethod(receive)

        class MockHttps(HTTPSHandler):
            https_open = staticmethod(receive)

        def run():
            with patch("kmp_native_evidence.build_opener",
                       side_effect=lambda redirect: build_opener(ProxyHandler({}), redirect, MockHttp(), MockHttps())):
                return api(operation, self.artifact_path, self.token)

        return run, calls

    def test_authenticated_artifact_lookup(self):
        run, calls = self.mock_requests([(200, None, b'{"id": 12}')])
        self.assertEqual({"id": 12}, run())
        self.assertEqual([("https://api.github.com/" + self.artifact_path, "Bearer " + self.token)], calls)

    def test_api_redirect_keeps_credential_on_github_api_only(self):
        run, calls = self.mock_requests([(301, "/repos/owner/project/actions/artifacts/12", b""),
                                         (200, None, b'{"id": 12}')])
        self.assertEqual({"id": 12}, run())
        self.assertEqual([("https://api.github.com/" + self.artifact_path, "Bearer " + self.token)] * 2, calls)

    def test_cross_origin_downgrade_and_signed_storage_redirects_send_no_request(self):
        for destination in ("https://objects.example/artifact?sig=secret", "http://api.github.com/metadata",
                            "https://api.github.com@objects.example/metadata", "https://api.github.com:444/metadata"):
            with self.subTest(destination=destination):
                run, calls = self.mock_requests([(302, destination, b"")])
                with self.assertRaisesRegex(ValueError, r"simulator artifact metadata: GitHub API HTTP 302") as err:
                    run()
                self.assertEqual(1, len(calls))
                self.assertNotIn(destination, str(err.exception))
                self.assertNotIn(self.token, str(err.exception))

    def test_http_status_and_operation_fail_closed_without_echoing_body(self):
        for status in (401, 403, 429, 503):
            with self.subTest(status=status):
                run, calls = self.mock_requests([(status, None, self.token.encode())], "device producer jobs")
                with self.assertRaisesRegex(ValueError, f"device producer jobs: GitHub API HTTP {status}") as err:
                    run()
                self.assertNotIn(self.token, str(err.exception))
                self.assertEqual(1, len(calls))

    def test_unavailable_and_bad_json_fail_closed(self):
        for reply, error in ((URLError(self.token), "unavailable"),
                             ((200, None, self.token.encode()), r"invalid GitHub API response \(HTTP 200\)")):
            with self.subTest(error=error):
                run, _ = self.mock_requests([reply])
                with self.assertRaisesRegex(ValueError, error) as err:
                    run()
                self.assertNotIn(self.token, str(err.exception))


class NativeAggregateTest(unittest.TestCase):
    def setUp(self):
        self.needs = {'native-source': {'result': 'success'}}
        for role in ('simulator', 'device'):
            self.needs[f'kmp-ios-{role}'] = {'result': 'success', 'outputs': {'artifact_id': '12', 'artifact_digest': 'a' * 64}}

    def test_success_requires_both_children_and_source(self):
        self.assertEqual({'simulator', 'device'}, set(child_outputs(self.needs)))
        for job in self.needs:
            for status in ('failure', 'cancelled', 'skipped', ''):
                with self.subTest(job=job, status=status):
                    changed = copy.deepcopy(self.needs)
                    changed[job]['result'] = status
                    with self.assertRaises(ValueError):
                        child_outputs(changed)
            changed = copy.deepcopy(self.needs)
            del changed[job]
            with self.assertRaises(KeyError):
                child_outputs(changed)

    def test_missing_or_malformed_child_outputs_fail(self):
        for name in ('artifact_id', 'artifact_digest'):
            for value in ('', '0', '../escape', 'null'):
                with self.subTest(name=name, value=value):
                    changed = copy.deepcopy(self.needs)
                    changed['kmp-ios-simulator']['outputs'][name] = value
                    with self.assertRaises(ValueError):
                        child_outputs(changed)

    def test_pr_head_is_separate_from_tested_merge_sha(self):
        metadata = {'id': 12, 'expired': False, 'digest': 'sha256:' + 'a' * 64,
                    'workflow_run': {'id': 45, 'head_sha': 'pr-head'}}
        outputs = self.needs['kmp-ios-simulator']['outputs']
        validate_metadata(metadata, outputs, {'run_id': 45, 'event_sha': 'merge-sha'}, 'pr-head')
        for key, value in [('id', 13), ('expired', True), ('digest', 'sha256:' + 'b' * 64)]:
            with self.subTest(key=key):
                changed = copy.deepcopy(metadata)
                changed[key] = value
                with self.assertRaises(ValueError):
                    validate_metadata(changed, outputs, {'run_id': 45}, 'pr-head')
        with self.assertRaises(ValueError):
            validate_metadata(metadata, outputs, {'run_id': 46}, 'pr-head')
        with self.assertRaises(ValueError):
            validate_metadata(metadata, outputs, {'run_id': 45}, 'merge-sha')

    def test_reusable_workflow_producer_is_unique_and_successful(self):
        for name in ('KMP iOS simulator', 'build / KMP iOS simulator'):
            jobs = {'total_count': 1, 'jobs': [{'name': name, 'conclusion': 'success'}]}
            validate_producer(jobs, 'simulator')
            for conclusion in ('cancelled', 'failure', 'skipped', None):
                jobs['jobs'][0]['conclusion'] = conclusion
                with self.assertRaises(ValueError):
                    validate_producer(jobs, 'simulator')
        for jobs in ({'total_count': 0, 'jobs': []},
                     {'total_count': 2, 'jobs': [{'name': 'KMP iOS simulator', 'conclusion': 'success'}]},
                     {'total_count': 2, 'jobs': [{'name': 'KMP iOS simulator', 'conclusion': 'success'}] * 2}):
            with self.assertRaises(ValueError):
                validate_producer(jobs, 'simulator')


class NativeArchiveTest(unittest.TestCase):
    def setUp(self):
        self.modules = [':' + name for name in read_json(Path('gradle/kmp-native-identities.json'))]
        self.model = []
        for module in self.modules:
            root = module[1:].replace(':', '/') + '/' + getattr(self, 'build_suffix', 'build [relocated]')
            self.model.append(dict(module=module, buildDirectory=root,
                                   simulatorTask=module + ':iosSimulatorArm64Test',
                                   simulatorResults=root + '/test-results/iosSimulatorArm64Test',
                                   simulatorBinary=root + '/bin/iosSimulatorArm64/debugTest/test.kexe',
                                   deviceLinkTask=module + ':linkDebugTestIosArm64',
                                   deviceBinary=root + '/bin/iosArm64/debugTest/test.kexe'))
        self.intended = dict(source_sha='s', source_tree='t', workflow_sha='w', workflow_ref='ref',
                             event_sha='merge', repository='owner/repo', run_id=1, attempt=2)
        source = dict(self.intended, role='device', attempt=1, created_ns=100, workspace='/producer',
                      source_workflow_sha256=digest(Path('.github/workflows/android_build_unified.yml')),
                      toolchain=completed_toolchain("device"))
        inspected = [dict(module=row['module'], path=row['deviceBinary'], size=3, mtime_ns=300,
                          marker_mtime_ns=200, sha256=hashlib.sha256(b'bin').hexdigest(), vtool=' platform IOS\n')
                     for row in self.model]
        flags = ['--continue', '--rerun-tasks', '--no-build-cache', '--no-configuration-cache',
                 '--full-stacktrace', '--console=plain']
        tasks = [row['deviceLinkTask'] for row in self.model]
        self.files = {
            'build/kmp-verification/model.json': encoded(self.model),
            'build/kmp-verification/device-link-start': b'',
            'build/kmp-verification/device-provenance.json': encoded(source),
            'build/kmp-verification/device-inspection.json': encoded(inspected),
            'build/kmp-verification/device-gradle.log': ('\n'.join('> Task ' + task for task in tasks) +
                                                        '\n21 actionable tasks: 21 executed\n').encode(),
            'build/kmp-verification/device-execution.json': encoded(dict(role='device', exit_code=0, arguments=tasks + flags)),
            'gradle/kmp-native-identities.json': Path('gradle/kmp-native-identities.json').read_bytes(),
            self.model[-1]['deviceBinary']: b'bin',
        }
        self.receipt = dict(valid=True, role='device', provenance=source, modules=self.modules,
                            summary='21 actionable tasks: 21 executed', inspected_binaries=inspected,
                            archived_binaries=[self.model[-1]['deviceBinary']])

    def archive(self, extra=None):
        self.receipt['files'] = [dict(path=path, size=len(data), sha256=hashlib.sha256(data).hexdigest(),
                                      mtime_ns=200 if path.endswith(('device-link-start', 'native-test-start')) else 300)
                                 for path, data in self.files.items()]
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            for path, data in self.files.items():
                archive.writestr(path, data)
            archive.writestr(f"build/kmp-verification/{self.receipt['role']}-receipt.json", encoded(self.receipt))
            if extra:
                archive.writestr(*extra)
        data = stream.getvalue()
        return data, hashlib.sha256(data).hexdigest()

    def verify(self, extra=None):
        data, checksum = self.archive(extra)
        return validate_archive(data, checksum, self.receipt['role'], self.intended, self.modules)

    def test_complete_relocated_bracket_archive_and_earlier_successful_attempt(self):
        self.assertTrue(self.verify()['valid'])

    def test_literal_glob_and_backslash_path_remains_a_valid_posix_path(self):
        self.build_suffix = r'literal*?\dir'
        self.setUp()
        self.assertTrue(self.verify()['valid'])

    def test_missing_each_required_file_fails_even_with_consistent_inventory(self):
        for path in list(self.files):
            with self.subTest(path=path):
                data = self.files.pop(path)
                try:
                    with self.assertRaises((ValueError, KeyError, OSError)):
                        self.verify()
                finally:
                    self.files[path] = data

    def test_inconsistent_provenance_and_future_attempt_fail(self):
        for key in self.intended:
            with self.subTest(key=key):
                original = self.receipt['provenance'][key]
                self.receipt['provenance'][key] = 3 if key == 'attempt' else 'wrong'
                try:
                    with self.assertRaises(ValueError):
                        self.verify()
                finally:
                    self.receipt['provenance'][key] = original

    def test_incomplete_module_inventory_fails(self):
        self.receipt['modules'] = self.modules[:-1]
        with self.assertRaisesRegex(ValueError, 'incomplete Native module'):
            self.verify()

    def test_reused_or_missing_task_outputs_fail(self):
        name = 'build/kmp-verification/device-gradle.log'
        original = self.files[name]
        for data in (original.replace(b'21 executed', b'20 executed, 1 from cache'),
                     original.replace(b'> Task ', b'> Task ', 1).replace(b'linkDebugTestIosArm64\n', b'linkDebugTestIosArm64 UP-TO-DATE\n', 1),
                     b'21 actionable tasks: 21 executed\n'):
            self.files[name] = data
            with self.assertRaises(ValueError):
                self.verify()

    def test_wrong_platform_or_stale_device_inspection_fails(self):
        original = copy.deepcopy(self.receipt['inspected_binaries'])
        for key, value in [('vtool', 'platform IOSSIMULATOR\n'), ('size', 0), ('mtime_ns', 199), ('path', 'wrong')]:
            changed = copy.deepcopy(original)
            changed[0][key] = value
            self.receipt['inspected_binaries'] = changed
            self.files['build/kmp-verification/device-inspection.json'] = encoded(changed)
            with self.assertRaises(ValueError):
                self.verify()

    def test_digest_escaping_link_duplicate_and_unlisted_members_fail(self):
        data, _ = self.archive()
        with self.assertRaisesRegex(ValueError, 'artifact digest'):
            validate_archive(data, '0' * 64, 'device', self.intended, self.modules)
        link = zipfile.ZipInfo('link')
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        for extra in [('../escape', b'x'), ('/absolute', b'x'), ('a\\b', b'x'), (link, b'target'),
                      ('unlisted', b'x'), ('build/kmp-verification/model.json', b'x')]:
            with self.subTest(extra=str(extra[0])):
                with self.assertRaises(ValueError):
                    self.verify(extra)


class NativeSimulatorArchiveTest(unittest.TestCase):
    def setUp(self):
        from prove_ios_install_epoch_relaunch import IDENTITY as RELAUNCH_ID
        from prove_ios_install_epoch_sync import IDENTITY as SYNC_ID, PREFIX, SOURCE, compiler_command
        from test_kmp_native_sync import NativeSyncTraceTest
        sample = NativeArchiveTest()
        sample.setUp()
        self.sample = sample
        source = dict(sample.receipt['provenance'], role='simulator')
        source['toolchain'].update(iphonesimulator_sdk='26.6', iphonesimulator_sdk_path='/Applications/pinned/sdk')
        sample.files = {key: value for key, value in sample.files.items()
                        if key.endswith(('model.json', 'kmp-native-identities.json'))}
        sample.files.update({
            'build/kmp-verification/native-test-start': b'',
        })
        tasks = [row['simulatorTask'] for row in sample.model]
        flags = ['--continue', '--rerun-tasks', '--no-build-cache', '--no-configuration-cache',
                 '--full-stacktrace', '--console=plain', '--no-parallel']
        sample.files['build/kmp-verification/simulator-execution.json'] = encoded(
            dict(role='simulator', exit_code=0, arguments=tasks + flags))
        sample.files['build/kmp-verification/simulator-gradle.log'] = (
            '\n'.join('> Task ' + task for task in tasks) + '\n21 actionable tasks: 21 executed\n').encode()
        identities = read_json(Path('gradle/kmp-native-identities.json'))
        for row in sample.model:
            classes = identities[row['module'][1:]]
            suite = ET.Element('testsuite', tests=str(sum(map(len, classes.values()))), failures='0', errors='0', skipped='0')
            for classname, names in classes.items():
                for name in names:
                    ET.SubElement(suite, 'testcase', classname='iosSimulatorArm64Test.' + classname,
                                  name=name + '[iosSimulatorArm64]')
            sample.files[row['simulatorResults'] + '/TEST-fixture.xml'] = ET.tostring(suite)
        database = next(row for row in sample.model if row['module'] == ':core:data:database')
        for version in (5, 6, 8):
            for name in ('pre-open.db', 'schema.json', 'source-blob.txt'):
                sample.files[f"{database['buildDirectory']}/native-fixtures/v{version}-fixture/{name}"] = b'fixture'
        apple = sample.model[-1]
        binary = apple['simulatorBinary']
        sample.files[binary] = b'bin'
        binary_hash = hashlib.sha256(b'bin').hexdigest()
        invocation = '/producer/' + binary
        epoch = apple['buildDirectory'] + '/install-epoch-relaunch'
        simulator, nonce = str(uuid.uuid4()), str(uuid.uuid4())
        source["simulator_runtime"] = {"simulator": simulator, "identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26-6",
                                       "version": "26.6", "build": "fixture-build"}
        sample.files["build/kmp-verification/simulator-provenance.json"] = encoded(source)
        sample.files[epoch + '/run.json'] = encoded(dict(started_ns=250, simulator=simulator, nonce=nonce))
        for index in (1, 2):
            launch = str(uuid.uuid4())
            storage = dict(nonce=nonce, launch=launch, expected_udid=simulator, actual_udid=simulator,
                           pid=index, io_main=False, sha256='a' * 64,
                           path=f'/Devices/{simulator}/data/Library/Application Support/WorkeeperInstallation/install_epoch')
            log = (f'[ RUN      ] {RELAUNCH_ID}\nWORKEEPER_INSTALL_EPOCH_RECEIPT ' + json.dumps(storage) +
                   f'\n[       OK ] {RELAUNCH_ID}\n').encode()
            sample.files[f'{epoch}/launch-{index}.log'] = log
            sample.files[f'{epoch}/launch-{index}.json'] = encoded(dict(
                launch=launch, started_ns=260 + index * 10, ended_ns=265 + index * 10, exit_code=0,
                identity=RELAUNCH_ID, binary_sha256=binary_hash, log_sha256=hashlib.sha256(log).hexdigest(),
                command=['xcrun', 'simctl', 'spawn', simulator, invocation,
                         f'--ktest_filter={RELAUNCH_ID}', '--ktest_logger=GTEST'], storage=storage))
        sync = apple['buildDirectory'] + '/install-epoch-sync'
        trace = NativeSyncTraceTest()
        trace.setUp()
        for row in trace.rows:
            row['nonce'] = nonce
            row['path'] = row['path'].replace('owned-simulator', simulator)
            row['target'] = row['target'].replace('owned-simulator', simulator)
        operations = [json.dumps(row) for row in trace.rows]
        log = (f'[ RUN      ] {SYNC_ID}\n' + '\n'.join(PREFIX + row for row in operations) +
               f'\n[       OK ] {SYNC_ID}\n').encode()
        sample.files[sync + '/native.log'] = log
        sample.files[sync + '/operations.jsonl'] = ('\n'.join(operations) + '\n').encode()
        sample.files[sync + '/observer.dylib'] = b'observer'
        sample.files[sync + '/receipt.json'] = encoded(dict(
            exit_code=0, started_ns=280, ended_ns=290, simulator=simulator, nonce=nonce,
            xcode=source['toolchain']['xcode'], sdk='26.6',
            build_command=compiler_command('26.6', '/Applications/pinned/sdk',
                                           '/producer/.github/scripts/ios_install_epoch_sync_observer.c',
                                           '/producer/' + sync + '/observer.dylib'),
            binary_sha256=binary_hash, observer_source_sha256=digest(SOURCE),
            observer_sha256=hashlib.sha256(b'observer').hexdigest(), log_sha256=hashlib.sha256(log).hexdigest(),
            command=['xcrun', 'simctl', 'spawn', simulator, invocation,
                     f'--ktest_filter={SYNC_ID}', '--ktest_logger=GTEST']))
        sample.receipt = dict(valid=True, role='simulator', provenance=source, modules=sample.modules,
                              summary='21 actionable tasks: 21 executed')

    def test_simulator_archive_replays_full_xml_epoch_and_sync_validators(self):
        self.assertTrue(self.sample.verify()['valid'])

    def test_runtime_identity_must_match_both_storage_proofs(self):
        sample = self.sample
        source = sample.receipt["provenance"]
        source["simulator_runtime"]["simulator"] = str(uuid.uuid4())
        sample.files["build/kmp-verification/simulator-provenance.json"] = encoded(source)
        with redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "runtime differs from storage proof"):
            sample.verify()

    def test_each_simulator_evidence_file_is_individually_required(self):
        from contextlib import redirect_stdout
        for path in list(self.sample.files):
            with self.subTest(path=path):
                data = self.sample.files.pop(path)
                try:
                    with redirect_stdout(io.StringIO()), self.assertRaises((ValueError, KeyError, OSError, SystemExit)):
                        self.sample.verify()
                finally:
                    self.sample.files[path] = data

    def test_observer_toolchain_and_compile_command_must_match_job(self):
        path = self.sample.model[-1]['buildDirectory'] + '/install-epoch-sync/receipt.json'
        original = json.loads(self.sample.files[path])
        for field, value in [('xcode', 'Xcode 99.0'), ('sdk', '99.0'), ('build_command', ['definitely-not-clang'])]:
            with self.subTest(field=field):
                self.sample.files[path] = encoded(dict(original, **{field: value}))
                with self.assertRaisesRegex(ValueError, 'observer'):
                    self.sample.verify()

    def test_omitted_native_identity_fails_after_archive_extraction(self):
        name = self.sample.model[-1]['simulatorResults'] + '/TEST-fixture.xml'
        tree = ET.fromstring(self.sample.files[name])
        tree.remove(tree.find('testcase'))
        tree.set('tests', str(len(tree.findall('testcase'))))
        self.sample.files[name] = ET.tostring(tree)
        with self.assertRaises(SystemExit):
            self.sample.verify()


if __name__ == '__main__':
    unittest.main()
