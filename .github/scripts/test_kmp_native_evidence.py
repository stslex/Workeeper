import copy
import hashlib
import io
import json
from pathlib import Path
import stat
import uuid
import xml.etree.ElementTree as ET
import unittest
import zipfile

from kmp_native_evidence import child_outputs, digest, validate_archive, validate_metadata, validate_producer
from kmp_native_model import read_json


def encoded(value):
    return json.dumps(value).encode()


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
                      toolchain={'xcode': 'Xcode 26.6\nBuild version pinned'})
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
            'build/kmp-verification/simulator-provenance.json': encoded(source),
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
