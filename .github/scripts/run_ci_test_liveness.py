#!/usr/bin/env python3
"""Bounded Linux/JDK diagnostic; does not alter test sources or their timeouts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

TASK = ':feature:live-workout:testDebugUnitTest'
CLASS = 'io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRowSemanticsTest'
METHOD = 'fieldsAnnounceTheirUnit'
SOURCE = 'feature/live-workout/src/test/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveSetRowSemanticsTest.kt'
RESULTS = Path('feature/live-workout/build/test-results/testDebugUnitTest')
DUMP_AT = (20, 40, 55)
MAX_SECONDS = 1500


def utc():
    return datetime.now(timezone.utc).isoformat()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def append(path, value):
    with path.open('a') as stream:
        stream.write(json.dumps(value) + '\n')


def process_rows(marker):
    rows = []
    for directory in Path('/proc').glob('[0-9]*'):
        try:
            command = (directory / 'comm').read_text().strip()
            if command != 'java':
                continue
            # Inspect only to identify this run's worker; never save full command lines.
            arguments = (directory / 'cmdline').read_bytes().split(b'\0')
            raw = (directory / 'stat').read_text().rsplit(')', 1)[1].split()
            rows.append({'pid': int(directory.name), 'parent_pid': int(raw[1]),
                         'start_ticks': int(raw[19]), 'state': raw[0],
                         'cpu_ticks': int(raw[11]) + int(raw[12]),
                         'rss_bytes': int(raw[21]) * os.sysconf('SC_PAGE_SIZE'),
                         'worker': marker.encode() in arguments})
        except (OSError, ValueError, IndexError):
            continue
    return rows


def pressure_snapshot():
    data = {}
    paths = [Path('/proc/meminfo'), Path('/proc/loadavg')]
    paths += [Path('/proc/pressure') / name for name in ('cpu', 'memory', 'io')]
    # GitHub Linux runners normally use cgroup v2; absence is recorded, not treated as zero.
    cg = Path('/sys/fs/cgroup')
    try:
        entry = next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines()
                     if line.startswith('0::'))
        relative = entry.lstrip('/')
        if '..' not in Path(relative).parts and (cg / relative / 'cpu.stat').exists():
            cg = cg / relative
    except (OSError, StopIteration):
        pass
    paths += [cg / name for name in ('memory.current', 'memory.max', 'memory.events',
                                    'cpu.max', 'cpu.stat', 'cpu.pressure', 'memory.pressure', 'io.pressure')]
    for path in paths:
        try:
            data[str(path)] = path.read_text()
        except OSError as failure:
            data[str(path)] = {'unavailable': failure.__class__.__name__}
    return data


def read_events(path):
    if not path.exists():
        return []
    result = []
    for line in path.read_text().splitlines():
        try:
            result.append(json.loads(line))
        except json.JSONDecodeError:
            # The listener may be appending its last line right now.
            continue
    return result


def thread_dump(output, worker, marker, phase, offset):
    # Verify PID plus birth tick to avoid attaching to a recycled or unrelated process.
    current = next((row for row in process_rows(marker)
                    if row['pid'] == worker['pid'] and row['start_ticks'] == worker['start_ticks']
                    and row['worker']), None)
    receipt = {'utc': utc(), 'phase': phase, 'offset_seconds': offset, 'worker': worker}
    if current is None:
        receipt['result'] = 'worker_no_longer_present'
    else:
        name = f"{phase}-{offset}s-pid{worker['pid']}"
        try:
            with (output / f'{name}.threads.txt').open('w') as stream:
                completed = subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/jcmd'),
                                            str(worker['pid']), 'Thread.print', '-l'],
                                           stdout=stream, stderr=subprocess.STDOUT, timeout=5, check=False)
            receipt['exit_code'] = completed.returncode
            receipt['result'] = 'completed'
        except subprocess.TimeoutExpired:
            receipt['result'] = 'jcmd_timeout_5s'
        except OSError as failure:
            receipt['result'] = 'jcmd_unavailable'
            receipt['error_type'] = failure.__class__.__name__
    append(output / 'thread-dumps.jsonl', receipt)


def summarize_xml(output, started_epoch):
    inventory = []
    copied = output / 'xml'
    copied.mkdir(exist_ok=True)
    for path in sorted(RESULTS.glob('TEST-*.xml')):
        shutil.copy2(path, copied / path.name)
        row = {'file': path.name, 'mtime': path.stat().st_mtime,
               'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
               'fresh': path.stat().st_mtime >= started_epoch}
        try:
            suite = ET.parse(path).getroot()
            cases = suite.findall('testcase')
            row['cases'] = [{'class': case.get('classname'), 'name': case.get('name'),
                             'failures': len(case.findall('failure')), 'errors': len(case.findall('error')),
                             'skipped': len(case.findall('skipped'))} for case in cases]
            actual = {'tests': len(cases), 'failures': sum(c['failures'] for c in row['cases']),
                      'errors': sum(c['errors'] for c in row['cases']),
                      'skipped': sum(c['skipped'] for c in row['cases'])}
            row['counters_match'] = all(int(suite.get(key, '-1')) == count for key, count in actual.items())
        except (ET.ParseError, ValueError) as failure:
            row['parse_error'] = str(failure)
        inventory.append(row)
    save(output / 'xml-inventory.json', inventory)
    return inventory


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    if sys.platform != 'linux' or not os.environ.get('JAVA_HOME'):
        raise SystemExit('This diagnostic requires Linux and JAVA_HOME (JDK 21).')
    if list(RESULTS.glob('TEST-*.xml')):
        raise SystemExit('Refusing pre-existing target XML; use a fresh runner checkout.')
    token = str(uuid.uuid4())
    marker = '-Dci.liveness.worker=' + token
    source_sha = hashlib.sha256(Path(SOURCE).read_bytes()).hexdigest()
    command = ['./gradlew', TASK, '--tests', CLASS + '.' + METHOD,
               '--no-parallel', '--max-workers=1', '--rerun-tasks', '--no-build-cache',
               '--no-configuration-cache', '--no-daemon', '--info', '--full-stacktrace', '--console=plain',
               '-I', '.github/scripts/ci_test_liveness.init.gradle',
               '-Dci.liveness.output=' + str(output), '-Dci.liveness.token=' + token]
    started_epoch = time.time()
    started = time.monotonic()
    save(output / 'command.json', {'utc': utc(), 'argv': command, 'cwd': str(Path.cwd()),
                                 'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                                 'source': SOURCE, 'source_sha256': source_sha, 'budget_seconds': MAX_SECONDS,
                                 'clock_ticks_per_second': os.sysconf('SC_CLK_TCK'), 'cpu_count': os.cpu_count(),
                                 'dump_offsets': DUMP_AT})
    workers = {}
    emitted = set()
    last_sample = -5.0
    last_heartbeat = -30.0
    previous_cpu = {}
    timed_out = False
    with (output / 'gradle.log').open('w') as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        while process.poll() is None:
            elapsed = time.monotonic() - started
            rows = process_rows(marker)
            for worker in rows:
                if worker['worker']:
                    workers.setdefault((worker['pid'], worker['start_ticks']), (worker, time.time()))
            events = read_events(output / 'test-events.jsonl')
            starts = [event for event in events if event.get('event') == 'test_start']
            ends = [event for event in events if event.get('event') == 'test_end']
            for key, (worker, seen) in workers.items():
                start = next((event for event in starts if worker['pid'] in event['worker_pids']), None)
                phase = 'test' if start else 'worker-startup'
                age = time.time() - (start['epoch_ms'] / 1000 if start else seen)
                ended = start and any(event['class_name'] == start['class_name'] and event['name'] == start['name']
                                      and event['epoch_ms'] >= start['epoch_ms'] for event in ends)
                if ended:
                    continue
                for offset in DUMP_AT:
                    dump_key = (key, phase, offset)
                    if age >= offset and dump_key not in emitted:
                        emitted.add(dump_key)
                        thread_dump(output, worker, marker, phase, offset)
            if elapsed - last_sample >= 5:
                for row in rows:
                    key = (row['pid'], row['start_ticks'])
                    previous = previous_cpu.get(key)
                    row['cpu_percent_over_sample'] = None if previous is None else (
                        (row['cpu_ticks'] - previous[0]) / os.sysconf('SC_CLK_TCK') /
                        (elapsed - previous[1]) * 100
                    )
                    previous_cpu[key] = (row['cpu_ticks'], elapsed)
                append(output / 'resource-samples.jsonl', {'utc': utc(), 'elapsed_seconds': elapsed,
                                                         'java_processes': rows, 'pressure': pressure_snapshot()})
                last_sample = elapsed
            if elapsed - last_heartbeat >= 30:
                print(f'Diagnostic alive: {elapsed:.0f}s; test starts={len(starts)}, ends={len(ends)}', flush=True)
                last_heartbeat = elapsed
            if elapsed >= MAX_SECONDS:
                timed_out = True
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                break
            time.sleep(0.5)
        returncode = process.wait(timeout=10)
    inventory = summarize_xml(output, started_epoch)
    events = read_events(output / 'test-events.jsonl')
    cases = [case for row in inventory for case in row.get('cases', [])]
    expected = len(cases) == 1 and cases[0]['class'] == CLASS and cases[0]['name'] in (METHOD, METHOD + '()')
    log = (output / 'gradle.log').read_text(errors='replace')
    summaries = re.findall(r'^[ \t]*(\d+) actionable tasks?:([^\r\n]+)', log, re.MULTILINE)
    executed = bool(summaries) and all(re.fullmatch(r'\s*' + count + r' executed\s*', detail)
                                     for count, detail in summaries)
    unchanged = hashlib.sha256(Path(SOURCE).read_bytes()).hexdigest() == source_sha
    observed = all(sum(event.get('event') == kind and event.get('class_name') == CLASS
                       and event.get('name') in (METHOD, METHOD + '()')
                       and len(event.get('worker_pids', [])) == 1 for event in events) == 1
                   for kind in ('test_start', 'test_end'))
    evidence_complete = (expected and observed and executed and unchanged and
                         all(row.get('fresh') and row.get('counters_match') for row in inventory))
    test_passed = (expected and all(case['failures'] == case['errors'] == case['skipped'] == 0 for case in cases)
                   and any(event.get('event') == 'test_end' and event.get('class_name') == CLASS
                           and event.get('result') == 'SUCCESS' for event in events))
    save(output / 'result.json', {'utc': utc(), 'gradle_exit_code': returncode, 'bounded_timeout': timed_out,
                                'elapsed_seconds': time.monotonic() - started, 'source_unchanged': unchanged,
                                'exact_test_present': expected, 'before_after_present': observed,
                                'all_executed_summary': executed, 'summaries': summaries,
                                'evidence_complete': evidence_complete, 'test_passed': test_passed,
                                'note': 'Diagnostic only; no release, physical-watch, or root-suite gate claim.'})
    print(json.dumps({'gradle_exit_code': returncode, 'evidence_complete': evidence_complete,
                      'test_passed': test_passed, 'bounded_timeout': timed_out}), flush=True)
    return 0 if returncode == 0 and evidence_complete and test_passed else 1


if __name__ == '__main__':
    sys.exit(main())
