#!/usr/bin/env python3
"""Manual full-context observation; original Gradle execution settings stay intact."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from ci_wear_liveness_common import append, pressure_snapshot, process_rows, save, utc

CLASS = 'io.github.stslex.workeeper.wear.ui.WearTouchTargetGateRuTest'
METHOD = 'everyClickTargetIsVisiblyReachable'
DISPLAY = 'Russian targets have visible 48dp bounds and do not overlap at either screen or font extreme'
SUBJECTS = (
    'app/wear/src/test/kotlin/io/github/stslex/workeeper/wear/ui/WearTouchTargetGateRuTest.kt',
    'app/wear/src/test/kotlin/io/github/stslex/workeeper/wear/ui/WearTouchTargetGateTest.kt',
)
TASKS = {'testDevDebugUnitTest': 'devDebug', 'testStoreDebugUnitTest': 'storeDebug'}
COMMANDS = {
    'paparazzi': ['./gradlew', 'verifyPaparazziDebug', '--full-stacktrace'],
    'unit': ['./gradlew', 'testDebugUnitTest', '--full-stacktrace'],
}
DUMP_AT = (20, 40, 55)
JOB_OBSERVATION_SECONDS = 52 * 60
PHASE_SECONDS = 25 * 60


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def emit(kind, value):
    print('CI_WEAR_' + kind + ' ' + json.dumps(value, separators=(',', ':')), flush=True)


def emit_text(kind, path, limit=65536, context=None):
    raw = path.read_bytes()
    emit(kind + '_BEGIN', {'utc': utc(), 'file': path.name, 'bytes': len(raw),
                          'sha256': hashlib.sha256(raw).hexdigest(), 'capped': len(raw) > limit, **(context or {})})
    if len(raw) <= limit:
        selected = raw
    else:
        selected = raw[:limit // 2] + b'\n[... middle omitted; full file in artifact ...]\n' + raw[-limit // 2:]
    for line in selected.decode('utf-8', errors='replace').splitlines():
        # Prefix each line so observed content cannot become a workflow command.
        for start in range(0, max(1, len(line)), 4000):
            print('CI_WEAR_' + kind + ' ' + line[start:start + 4000], flush=True)
    emit(kind + '_END', {'utc': utc(), 'file': path.name})


def source_hashes():
    return {str(p): sha(p) for base in ('app/wear/src', 'core/wear-protocol/src')
            for p in sorted(Path(base).rglob('*')) if p.is_file()}


def read_events(output, strict=False):
    events, errors = [], []
    for name in TASKS:
        for path in sorted((output / name).glob('observer-*.jsonl')):
            for ordinal, line in enumerate(path.read_text().splitlines(), 1):
                try:
                    event = json.loads(line)
                    event.update(event_file=str(path.relative_to(output)), event_ordinal=ordinal)
                    events.append(event)
                except json.JSONDecodeError:
                    if strict:
                        errors.append({'file': str(path.relative_to(output)), 'line': ordinal})
    return events, errors


def xml_inventory(output, started_ns):
    inventory = {}
    for task in TASKS:
        saved = output / task / 'xml'
        saved.mkdir(parents=True, exist_ok=True)
        rows = []
        for path in sorted((Path('app/wear/build/test-results') / task).glob('TEST-*.xml')):
            target = saved / path.name
            row = {'file': path.name, 'cases': [], 'fresh': False, 'counters_match': False}
            try:
                before_stat = path.stat()
                raw = path.read_bytes()
                after_stat = path.stat()
                target.write_bytes(raw)
                stable = ((before_stat.st_size, before_stat.st_mtime_ns) ==
                          (after_stat.st_size, after_stat.st_mtime_ns) and len(raw) == after_stat.st_size)
                row.update(sha256=hashlib.sha256(raw).hexdigest(), mtime_ns=after_stat.st_mtime_ns,
                           fresh=stable and after_stat.st_mtime_ns >= started_ns, stable_copy=stable)
                suite = ET.parse(target).getroot()
                for ordinal, case in enumerate(suite.findall('testcase'), 1):
                    row['cases'].append({'ordinal': ordinal, 'class': case.get('classname'),
                                         'name': case.get('name'), 'time': case.get('time'),
                                         **{key: len(case.findall(tag)) for key, tag in
                                            [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]}})
                actual = {key: sum(c[key] for c in row['cases']) for key in ('failures', 'errors', 'skipped')}
                actual['tests'] = len(row['cases'])
                row['counters_match'] = suite.tag == 'testsuite' and all(
                    int(suite.get(key, '-1')) == value for key, value in actual.items())
            except (ET.ParseError, ValueError) as failure:
                row['parse_error'] = str(failure)
            except OSError as failure:
                row['unavailable'] = type(failure).__name__
            rows.append(row)
        inventory[task] = rows
    save(output / 'xml-inventory.json', inventory)
    return inventory


def task_verdict(task, events, inventory, token, started_ms, ended_ms):
    path, flavor = ':app:wear:' + task, TASKS[task]
    scoped = [e for e in events if e.get('token') == token and e.get('task_path') == path]
    context_valid = all(e.get('flavor') == flavor and e.get('event_file', '').startswith(task + '/')
                        for e in scoped)
    target = [e for e in scoped if e.get('class_name') == CLASS and e.get('method') == METHOD]
    starts = [e for e in target if e.get('event') == 'test_start']
    ends = [e for e in target if e.get('event') == 'test_end']
    loaded = [e for e in scoped if e.get('event') == 'observer_loaded']
    complete = False
    duration = None
    if len(starts) == len(ends) == 1 and not any(e.get('event') == 'test_skipped' for e in target):
        first, last = starts[0], ends[0]
        keys = ('pid', 'start_ticks', 'unique_id', 'event_file', 'task_path', 'flavor')
        same = all(first.get(k) == last.get(k) for k in keys)
        worker = any(e.get('pid') == first.get('pid') and e.get('start_ticks') == first.get('start_ticks')
                     and e.get('event_file') == first.get('event_file')
                     and started_ms <= e.get('epoch_ms', -1) <= first.get('epoch_ms', -1) for e in loaded)
        valid = (isinstance(first.get('pid'), int) and first['pid'] > 0 and first.get('start_ticks', -1) > 0
                 and bool(first.get('unique_id')) and first.get('display_name') == last.get('display_name') == DISPLAY
                 and started_ms <= first.get('epoch_ms', -1) <= last.get('epoch_ms', -1) <= ended_ms
                 and last.get('nano_time', -1) >= first.get('nano_time', 0))
        complete = context_valid and same and worker and valid
        if complete:
            duration = (last['nano_time'] - first['nano_time']) / 1_000_000_000
    rows = inventory.get(task, [])
    exact = [(row, case) for row in rows for case in row.get('cases', [])
             if case['class'] == CLASS and case['name'] == DISPLAY]
    xml_valid = bool(rows) and all(row.get('counters_match') and row.get('fresh') for row in rows)
    exact_xml = len(exact) == 1 and exact[0][0]['fresh']
    complete = bool(complete and xml_valid and exact_xml)
    passed = bool(complete and ends[0]['result'] == 'SUCCESSFUL' and
                  all(c[k] == 0 for row in rows for c in row['cases'] for k in ('failures', 'errors', 'skipped')))
    return {'task_path': path, 'flavor': flavor, 'evidence_complete': complete, 'target_test_passed': passed,
            'starts': len(starts), 'ends': len(ends), 'exact_fresh_xml': bool(exact_xml),
            'target_duration_seconds': duration, 'ui_events': len(scoped)}


def validate(events, inventory, token, started_ms, ended_ms, errors):
    results = {task: task_verdict(task, events, inventory, token, started_ms, ended_ms) for task in TASKS}
    unexpected = [e for e in events if e.get('token') != token or e.get('task_path') not in
                  [':app:wear:' + task for task in TASKS]]
    return {'tasks': results, 'event_parse_errors': errors, 'unexpected_events': len(unexpected),
            'evidence_complete': not errors and not unexpected and all(r['evidence_complete'] for r in results.values()),
            'target_test_passed': not errors and not unexpected and all(r['target_test_passed'] for r in results.values())}


def dump(output, event, offset):
    pid, birth = event['pid'], event['start_ticks']
    task = event['task_path'].rsplit(':', 1)[1]
    receipt = {'utc': utc(), 'task_path': event['task_path'], 'flavor': event['flavor'], 'pid': pid,
               'start_ticks': birth, 'unique_id': event['unique_id'], 'offset_seconds': offset}
    present = any(p['pid'] == pid and p['start_ticks'] == birth for p in process_rows())
    if not present:
        receipt['result'] = 'worker_no_longer_present'
    else:
        path = output / task / f'target-{offset}s-pid{pid}-{birth}.threads.txt'
        try:
            with path.open('w') as stream:
                result = subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/jcmd'), str(pid),
                                         'Thread.print', '-l'], stdout=stream, stderr=subprocess.STDOUT,
                                        timeout=5, check=False)
            receipt.update(result='completed', exit_code=result.returncode, file=str(path.relative_to(output)))
        except subprocess.TimeoutExpired:
            receipt['result'] = 'jcmd_timeout_5s'
        except OSError as failure:
            receipt.update(result='jcmd_unavailable', error_type=failure.__class__.__name__)
        if path.is_file():
            emit_text('THREAD_DUMP', path, context={'task_path': event['task_path'], 'flavor': event['flavor']})
    append(output / 'thread-dumps.jsonl', receipt)
    emit('DUMP_RECEIPT', receipt)


def stop_client(process):
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)



def mirror_gradle(output, state, final=False):
    path = output / 'gradle.log'
    if not path.exists():
        return
    selected = re.compile(r'(^> Task |FAILED|FAILURE:|^BUILD |actionable tasks|tests? completed|'
                          r'^\* (What went wrong|Exception is)|^Execution failed|^Caused by:|'
                          r'^\s+at |^\s*\.\.\. \d+ more|^\s*[\w.$]+(?:Error|Exception)(?::|$))')
    # Gradle dependency/task failures are useful even when the Wear worker never starts.
    with path.open('rb') as stream:
        stream.seek(state['offset'])
        raw = stream.read() if final else stream.read(262144)
    state['offset'] += len(raw)
    lines = (state['carry'] + raw).split(b'\n')
    state['carry'] = b'' if final else lines.pop()
    for raw_line in lines:
        line = raw_line.decode('utf-8', errors='replace')
        if not selected.search(line):
            continue
        # Never mirror an environment dump or a spawned JVM/daemon command line.
        if re.search(r'(?i)(environment variables|command line|command:|gradle daemon.*\[|JAVA_TOOL_OPTIONS)', line):
            continue
        bounded = line[:8192]
        cost = len(bounded.encode('utf-8'))
        if state['bytes'] + cost > 1048576:
            if not state['capped']:
                emit('GRADLE_MIRROR_CAP', {'utc': utc(), 'limit_bytes': 1048576})
                state['capped'] = True
            continue
        state['bytes'] += cost
        print('CI_WEAR_GRADLE ' + bounded, flush=True)
        if len(bounded) != len(line):
            emit('GRADLE_LINE_CAPPED', {'utc': utc(), 'sha256': hashlib.sha256(raw_line).hexdigest()})
    if final:
        emit('GRADLE_LOG', {'utc': utc(), 'sha256': sha(path), 'bytes': path.stat().st_size,
                           'mirrored_bytes': state['bytes'], 'capped': state['capped']})


def run_phase(root, phase):
    if sys.platform != 'linux' or not os.environ.get('JAVA_HOME'):
        raise SystemExit('Linux and JAVA_HOME are required.')
    job = json.loads((root / 'job.json').read_text())
    if phase == 'unit':
        prior = json.loads((root / 'paparazzi/result.json').read_text())
        if prior['gradle_exit_code'] != 0 or prior['interruption'] or prior['bounded_timeout']:
            raise SystemExit('Prior original Paparazzi phase did not complete successfully.')
    output = root / phase
    output.mkdir(exist_ok=False)
    before = source_hashes()
    save(output / 'source-before.json', before)
    for source in SUBJECTS:
        shutil.copy2(source, output / Path(source).name)
    started_ns, started = time.time_ns(), time.monotonic()
    save(output / 'xml-before.json', xml_inventory(output, started_ns))
    token = str(uuid.uuid4())
    observer = root / 'observer/observer.jar'
    observer_sha = sha(observer) if phase == 'unit' else None
    command = COMMANDS[phase].copy()
    env = dict(os.environ)
    if phase == 'unit':
        command += ['-I', '.github/scripts/ci_wear_liveness.init.gradle']
        env.update(CI_CONTEXT_PHASE_OUTPUT=str(output), CI_CONTEXT_OBSERVER_TOKEN=token,
                   CI_CONTEXT_OBSERVER_JAR=str(observer))
    budget = min(PHASE_SECONDS, max(0.0, job['observation_deadline_epoch'] - time.time()))
    save(output / 'command.json', {'utc': utc(), 'started_epoch_ns': started_ns, 'phase': phase,
         'original_argv': COMMANDS[phase], 'actual_argv': command, 'observer_sha256': observer_sha,
         'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'budget_seconds': budget, 'clock_ticks_per_second': os.sysconf('SC_CLK_TCK'),
         'dump_offsets': DUMP_AT, 'note': 'Diagnostic classpath/env alter Test input fingerprints; cache policy unchanged.'})
    process, returncode, interruption, timed_out = None, None, None, False
    emitted, dump_keys, previous_cpu = set(), set(), {}
    mirror = {'offset': 0, 'carry': b'', 'bytes': 0, 'capped': False}
    last_sample, last_archive, last_log_resource = -5.0, -15.0, -15.0
    def interrupted(signum, frame):
        raise InterruptedError(f'signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        if budget < 30:
            raise InterruptedError('Diagnostic upload reserve reached before this phase.')
        with (output / 'gradle.log').open('w') as log:
            process = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            while process.poll() is None:
                elapsed = time.monotonic() - started
                mirror_gradle(output, mirror)
                events, _ = read_events(output)
                for event in events:
                    key = (event['event_file'], event['event_ordinal'])
                    if key not in emitted:
                        emitted.add(key)
                        emit('EVENT', event)
                target = [e for e in events if e.get('token') == token and e.get('class_name') == CLASS
                          and e.get('method') == METHOD and e.get('task_path') in [':app:wear:' + t for t in TASKS]]
                for event in [e for e in target if e.get('event') == 'test_start']:
                    ended = any(e.get('event') == 'test_end' and all(e.get(k) == event.get(k) for k in
                                ('task_path', 'pid', 'start_ticks', 'unique_id')) for e in target)
                    if ended or event.get('start_ticks', -1) <= 0:
                        continue
                    for offset in DUMP_AT:
                        key = (event['task_path'], event['pid'], event['start_ticks'], event['unique_id'], offset)
                        if time.time() - event['epoch_ms'] / 1000 >= offset and key not in dump_keys:
                            dump_keys.add(key)
                            dump(output, event, offset)
                if elapsed - last_sample >= 5:
                    rows = process_rows()
                    for row in rows:
                        key = (row['pid'], row['start_ticks'])
                        workers = [e for e in events if e.get('event') == 'observer_loaded'
                                   and e.get('pid') == key[0] and e.get('start_ticks') == key[1]]
                        row['wear_tasks'] = sorted({e['task_path'] for e in workers})
                        prev = previous_cpu.get(key)
                        row['cpu_percent_over_sample'] = None if prev is None else (
                            (row['cpu_ticks'] - prev[0]) / os.sysconf('SC_CLK_TCK') / (elapsed - prev[1]) * 100)
                        previous_cpu[key] = (row['cpu_ticks'], elapsed)
                    sample = {'utc': utc(), 'elapsed_seconds': elapsed, 'java_processes': rows, 'pressure': pressure_snapshot(),
                              'disk_free_bytes': {str(path): shutil.disk_usage(path).free for path in (Path.cwd(), root)}}
                    append(output / 'resources.jsonl', sample)
                    if elapsed - last_log_resource >= 15:
                        emit('RESOURCES', sample)
                        last_log_resource = elapsed
                    last_sample = elapsed
                if elapsed - last_archive >= 15:
                    xml_inventory(output, started_ns)
                    last_archive = elapsed
                if elapsed >= budget:
                    timed_out = True
                    break
                time.sleep(0.5)
    except (InterruptedError, OSError) as failure:
        interruption = str(failure)
    finally:
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        if process is not None:
            stop_client(process)
            returncode = process.returncode
        mirror_gradle(output, mirror, final=True)
        inventory = xml_inventory(output, started_ns)
        events, errors = read_events(output, strict=True)
        for event in events:
            if (event['event_file'], event['event_ordinal']) not in emitted:
                emit('EVENT', event)
        ended_ns = time.time_ns()
        verdict = validate(events, inventory, token, started_ns // 1_000_000,
                           (ended_ns + 999_999) // 1_000_000, errors) if phase == 'unit' else {
                               'evidence_complete': True, 'target_test_passed': None,
                               'note': 'Original Paparazzi command does not run either Wear task; no target events required.'}
        unchanged = before == source_hashes() and (phase != 'unit' or sha(observer) == observer_sha)
        result = dict(verdict, utc=utc(), started_epoch_ns=started_ns, ended_epoch_ns=ended_ns,
                      gradle_exit_code=returncode, bounded_timeout=timed_out, interruption=interruption,
                      source_and_observer_unchanged=unchanged, elapsed_seconds=time.monotonic() - started,
                      note='Diagnostic only; no cache-free, device, root-cause or uninstrumented equivalence claim.')
        save(output / 'result.json', result)
        emit('RESULT', result)
    return 0 if (returncode == 0 and not timed_out and not interruption and unchanged and result['evidence_complete']
                 and (phase != 'unit' or result['target_test_passed'])) else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--phase', choices=COMMANDS)
    parser.add_argument('--initialize', action='store_true')
    args = parser.parse_args()
    root = args.output.resolve()
    if args.initialize:
        if args.phase:
            parser.error('--initialize and --phase are mutually exclusive')
        root.mkdir(parents=True, exist_ok=False)
        save(root / 'job.json', {'utc': utc(), 'initialized_epoch': time.time(),
             'observation_deadline_epoch': time.time() + JOB_OBSERVATION_SECONDS,
             'phase_budget_seconds': PHASE_SECONDS,
             'note': 'Existing diagnostic 25-minute phase/52-minute observation bound; outer production job remains 60 minutes.'})
        return 0
    if not args.phase:
        parser.error('--phase is required unless --initialize is used')
    return run_phase(root, args.phase)


if __name__ == '__main__':
    sys.exit(main())
