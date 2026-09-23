#!/usr/bin/env python3
"""Observe two original CI invocations without changing their execution/cache settings."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from run_ci_test_liveness import CLASS, METHOD, SOURCE, RESULTS, pressure_snapshot, process_rows, utc, save, append

COMMANDS = {
    'paparazzi': ['./gradlew', 'verifyPaparazziDebug', '--full-stacktrace'],
    'unit': ['./gradlew', 'testDebugUnitTest', '--full-stacktrace'],
}
DUMP_AT = (20, 40, 55)
# Leave time for upload under the existing 60-minute job; earlier unrelated steps keep their own semantics.
JOB_OBSERVATION_SECONDS = 52 * 60
PHASE_SECONDS = 25 * 60


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def file_inventory():
    return [{'file': path.name, 'mtime_ns': path.stat().st_mtime_ns, 'sha256': sha(path)}
            for path in sorted(RESULTS.glob('TEST-*.xml'))]


def archive_xml(output, started_ns):
    copied = output / 'xml'
    copied.mkdir(exist_ok=True)
    inventory = []
    for path in sorted(RESULTS.glob('TEST-*.xml')):
        saved = copied / path.name
        shutil.copy2(path, saved)
        row = {'file': path.name, 'mtime_ns': path.stat().st_mtime_ns, 'sha256': sha(saved),
               'fresh_this_phase': path.stat().st_mtime_ns >= started_ns}
        try:
            suite = ET.parse(saved).getroot()
            cases = suite.findall('testcase')
            row['suite_timestamp'] = suite.get('timestamp')
            row['cases'] = [{'ordinal': index, 'class': case.get('classname'), 'name': case.get('name'),
                             'time': case.get('time'), 'failures': len(case.findall('failure')),
                             'errors': len(case.findall('error')), 'skipped': len(case.findall('skipped'))}
                            for index, case in enumerate(cases, 1)]
            actual = {'tests': len(cases), 'failures': sum(c['failures'] for c in row['cases']),
                      'errors': sum(c['errors'] for c in row['cases']),
                      'skipped': sum(c['skipped'] for c in row['cases'])}
            row['counters_match'] = suite.tag == 'testsuite' and all(
                int(suite.get(key, '-1')) == count for key, count in actual.items())
        except (ET.ParseError, ValueError) as failure:
            row['parse_error'] = str(failure)
        inventory.append(row)
    save(output / 'xml-inventory.json', inventory)
    return inventory


def events_from(output, strict=False):
    events = []
    errors = []
    for path in sorted(output.glob('observer-*.jsonl')):
        for index, line in enumerate(path.read_text().splitlines(), 1):
            try:
                event = json.loads(line)
                event['event_file'] = path.name
                events.append(event)
            except json.JSONDecodeError:
                if strict:
                    errors.append({'file': path.name, 'line': index})
    return events, errors


def exact_events(events, token, started_ms, ended_ms):
    selected = [e for e in events if e.get('token') == token and e.get('class_name') == CLASS
                and e.get('method') == METHOD]
    starts = [e for e in selected if e.get('event') == 'test_start']
    ends = [e for e in selected if e.get('event') == 'test_end']
    loaded = [e for e in events if e.get('event') == 'observer_loaded' and e.get('token') == token]
    if len(starts) != 1 or len(ends) != 1 or any(e.get('event') == 'test_skipped' for e in selected):
        return False, None
    first, last = starts[0], ends[0]
    same_worker = all(first.get(key) == last.get(key) for key in ('pid', 'start_ticks', 'unique_id', 'event_file'))
    loaded_worker = any(e.get('pid') == first.get('pid') and e.get('start_ticks') == first.get('start_ticks')
                        and e.get('event_file') == first.get('event_file')
                        and started_ms <= e.get('epoch_ms', -1) <= first.get('epoch_ms', -1) for e in loaded)
    ordered = (started_ms <= first.get('epoch_ms', -1) <= last.get('epoch_ms', -1) <= ended_ms
               and last.get('nano_time', -1) >= first.get('nano_time', 0))
    valid_pid = isinstance(first.get('pid'), int) and first['pid'] > 0 and first.get('start_ticks', -1) > 0
    return same_worker and loaded_worker and ordered and valid_pid and bool(first.get('unique_id')), last


def dump(output, event, offset):
    pid, birth = event['pid'], event['start_ticks']
    # Only an actual observer event names a worker; recheck its kernel birth tick before attaching.
    current = next((p for p in process_rows('') if p['pid'] == pid and p['start_ticks'] == birth), None)
    receipt = {'utc': utc(), 'pid': pid, 'start_ticks': birth, 'offset_seconds': offset,
               'target_unique_id': event['unique_id']}
    if current is None:
        receipt['result'] = 'worker_no_longer_present'
    else:
        try:
            name = f'test-{offset}s-pid{pid}.threads.txt'
            with (output / name).open('w') as stream:
                result = subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/jcmd'), str(pid),
                                         'Thread.print', '-l'], stdout=stream, stderr=subprocess.STDOUT,
                                        timeout=5, check=False)
            receipt.update(result='completed', exit_code=result.returncode, file=name)
        except subprocess.TimeoutExpired:
            receipt['result'] = 'jcmd_timeout_5s'
        except OSError as failure:
            receipt.update(result='jcmd_unavailable', error_type=failure.__class__.__name__)
    append(output / 'thread-dumps.jsonl', receipt)


def stop_client(process):
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def validate(output, inventory, events, errors, token, started_ns, ended_ns, source_sha, observer_sha):
    target_rows = [(row, case) for row in inventory for case in row.get('cases', [])
                   if case['class'] == CLASS and case['name'] in (METHOD, METHOD + '()')]
    observed, end = exact_events(events, token, started_ns // 1_000_000, (ended_ns + 999_999) // 1_000_000)
    exact_xml = len(target_rows) == 1 and target_rows[0][0]['fresh_this_phase']
    valid_xml = bool(inventory) and all(row.get('counters_match') for row in inventory)
    unchanged = sha(Path(SOURCE)) == source_sha and sha(output.parent / 'observer/observer.jar') == observer_sha
    complete = bool(observed and exact_xml and valid_xml and unchanged and not errors)
    passed = (complete and all(target_rows[0][1][key] == 0 for key in ('failures', 'errors', 'skipped'))
              and end['result'] == 'SUCCESSFUL')
    return {'exact_target_xml_present_and_fresh': bool(exact_xml), 'valid_xml_counters': valid_xml,
            'actual_worker_before_after_present': bool(observed), 'event_parse_errors': errors,
            'source_and_observer_unchanged': unchanged, 'evidence_complete': complete,
            'target_test_passed': bool(passed),
            'target_duration_seconds': None if not observed else (
                next(e for e in events if e.get('event') == 'test_end' and e.get('token') == token)['nano_time'] -
                next(e for e in events if e.get('event') == 'test_start' and e.get('token') == token)['nano_time']
            ) / 1_000_000_000}


def run_phase(root, phase):
    if sys.platform != 'linux' or not os.environ.get('JAVA_HOME'):
        raise SystemExit('Linux and JAVA_HOME (JDK 21) are required.')
    job = json.loads((root / 'job.json').read_text())
    if phase == 'unit' and not (root / 'paparazzi/result.json').is_file():
        raise SystemExit('Missing prior Paparazzi snapshot; refusing to claim full CI context.')
    output = root / phase
    output.mkdir(exist_ok=False)
    save(output / 'xml-before.json', file_inventory())
    source_sha = sha(Path(SOURCE))
    observer = root / 'observer/observer.jar'
    observer_sha = sha(observer)
    shutil.copy2(Path(SOURCE), output / 'subject-test.kt')
    token = str(uuid.uuid4())
    env = dict(os.environ, CI_CONTEXT_PHASE_OUTPUT=str(output), CI_CONTEXT_OBSERVER_TOKEN=token,
               CI_CONTEXT_OBSERVER_JAR=str(observer))
    original = COMMANDS[phase]
    command = original + ['-I', '.github/scripts/ci_context_liveness.init.gradle']
    started_ns = time.time_ns()
    started = time.monotonic()
    remaining = max(0.0, job['observation_deadline_epoch'] - time.time())
    budget = min(PHASE_SECONDS, remaining)
    save(output / 'command.json', {'utc': utc(), 'started_epoch_ns': started_ns, 'phase': phase,
                                 'original_argv': original, 'actual_argv': command,
                                 'source': SOURCE, 'source_sha256': source_sha, 'observer_sha256': observer_sha,
                                 'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                                 'cwd': str(Path.cwd()), 'budget_seconds': budget,
                                 'clock_ticks_per_second': os.sysconf('SC_CLK_TCK'), 'cpu_count': os.cpu_count(),
                                 'dump_offsets': DUMP_AT,
                                 'classification': 'diagnostic; original dependency/cache/parallelism settings retained'})
    process = None
    previous_cpu = {}
    emitted = set()
    last_sample = -5.0
    last_heartbeat = -30.0
    timed_out = False
    interruption = None
    returncode = None
    def interrupted(signum, frame):
        raise InterruptedError(f'signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        if budget < 30:
            raise InterruptedError('Diagnostic upload reserve reached before this phase could start')
        with (output / 'gradle.log').open('w') as log:
            process = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            while process.poll() is None:
                elapsed = time.monotonic() - started
                events, _ = events_from(output)
                starts = [e for e in events if e.get('event') == 'test_start' and e.get('token') == token
                          and e.get('class_name') == CLASS and e.get('method') == METHOD]
                ends = [e for e in events if e.get('event') == 'test_end' and e.get('token') == token]
                for event in starts:
                    ended = any(all(end.get(k) == event.get(k) for k in ('pid', 'start_ticks', 'unique_id'))
                                for end in ends)
                    if ended or event.get('start_ticks', -1) <= 0:
                        continue
                    for offset in DUMP_AT:
                        key = (event['pid'], event['start_ticks'], event['unique_id'], offset)
                        if time.time() - event['epoch_ms'] / 1000 >= offset and key not in emitted:
                            emitted.add(key)
                            dump(output, event, offset)
                if elapsed - last_sample >= 5:
                    rows = process_rows('')
                    known = {(e['pid'], e['start_ticks']) for e in starts}
                    for row in rows:
                        key = (row['pid'], row['start_ticks'])
                        row['worker'] = key in known
                        previous = previous_cpu.get(key)
                        row['cpu_percent_over_sample'] = None if previous is None else (
                            (row['cpu_ticks'] - previous[0]) / os.sysconf('SC_CLK_TCK') /
                            (elapsed - previous[1]) * 100)
                        previous_cpu[key] = (row['cpu_ticks'], elapsed)
                    append(output / 'resource-samples.jsonl', {'utc': utc(), 'elapsed_seconds': elapsed,
                                                             'java_processes': rows, 'pressure': pressure_snapshot()})
                    last_sample = elapsed
                if elapsed - last_heartbeat >= 30:
                    print(f'{phase} diagnostic: {elapsed:.0f}s; target starts={len(starts)}, ends={len(ends)}', flush=True)
                    last_heartbeat = elapsed
                if elapsed >= budget:
                    timed_out = True
                    break
                time.sleep(0.5)
    except (InterruptedError, OSError) as failure:
        interruption = str(failure)
    finally:
        # Do not stop shared Gradle daemons or alter their reuse policy.
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        if process is not None:
            stop_client(process)
            returncode = process.returncode
        inventory = archive_xml(output, started_ns)
        events, errors = events_from(output, strict=True)
        ended_ns = time.time_ns()
        verdict = validate(output, inventory, events, errors, token, started_ns, ended_ns,
                           source_sha, observer_sha)
        result = dict(verdict, utc=utc(), started_epoch_ns=started_ns, ended_epoch_ns=ended_ns,
                      gradle_exit_code=returncode, bounded_timeout=timed_out,
                      interruption=interruption, elapsed_seconds=time.monotonic() - started,
                      note='Diagnostic only. No all-executed, cache-free, release, or device claim.')
        save(output / 'result.json', result)
    print(json.dumps(result), flush=True)
    return 0 if (returncode == 0 and not timed_out and not interruption and result['evidence_complete']
                 and result['target_test_passed']) else 1


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
                                'note': 'Clock starts immediately after checkout; outer job timeout remains 60 minutes.'})
        return 0
    if not args.phase:
        parser.error('--phase is required unless --initialize is used')
    return run_phase(root, args.phase)


if __name__ == '__main__':
    sys.exit(main())
