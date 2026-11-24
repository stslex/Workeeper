#!/usr/bin/env python3
"""Stop the Android job's build daemons and record the effect on runner memory."""

import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

from android_gradle_checkpoint import ASSEMBLY, DAEMON_CLASS, PROC, cgroup, sample

KOTLIN = b'org.jetbrains.kotlin.daemon.KotlinCompileDaemon'
DAEMON_TYPES = {DAEMON_CLASS: 'gradle', KOTLIN: 'kotlin'}


def read_daemon(path, group):
    try:
        if path.stat().st_uid != os.getuid():
            return None
        args = (path / 'cmdline').read_bytes().split(b'\0')
        if args[0].rsplit(b'/', 1)[-1] != b'java':
            return None
        kind = next((DAEMON_TYPES[arg] for arg in args[1:] if arg in DAEMON_TYPES), None)
        if kind is None or cgroup(path.name) != group:
            return None
        fields = (path / 'stat').read_text().rsplit(') ', 1)[1].split()
        if fields[0] == 'Z':
            return None
        rss = re.search(r'^VmRSS:\s+(\d+)\s+kB$', (path / 'status').read_text(), re.M)
        return {
            'pid': int(path.name),
            'start_ticks': int(fields[19]),
            'kind': kind,
            'rss_kib': int(rss[1]) if rss else None,
        }
    except (OSError, IndexError):
        return None


def daemons(group):
    return [daemon for path in PROC.iterdir() if path.name.isdecimal()
            and (daemon := read_daemon(path, group)) is not None]


def identity(daemon):
    return daemon['pid'], daemon['start_ticks']


def stop_kotlin(daemon, group):
    pid = daemon['pid']
    try:
        fd = os.pidfd_open(pid)
        try:
            # Check the identity after opening the pidfd so PID reuse cannot direct the signal elsewhere.
            current = read_daemon(PROC / str(pid), group)
            if current and identity(current) == identity(daemon) and current['kind'] == 'kotlin':
                signal.pidfd_send_signal(fd, signal.SIGTERM)
                return True
        finally:
            os.close(fd)
    except ProcessLookupError:
        pass
    return False


def snapshot(processes):
    metrics = sample(ASSEMBLY, None)
    return {key: metrics[key] for key in ('utc', 'memory_available_mib', 'swap_free_mib')} | {
        'daemons': sorted(processes, key=identity),
    }


def write_receipt(path, receipt):
    pending = path.with_suffix('.tmp')
    pending.write_text(json.dumps(receipt, indent=2) + '\n')
    pending.replace(path)
    print(json.dumps(receipt), flush=True)


def cleanup(output):
    group = cgroup('self')
    if group is None:
        raise RuntimeError('Unable to identify the runner cgroup')
    before = daemons(group)
    receipt = {
        'checkout_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, timeout=5).strip(),
        'run_id': os.environ['GITHUB_RUN_ID'],
        'attempt': os.environ['GITHUB_RUN_ATTEMPT'],
        'runner_image': os.environ.get('ImageVersion'),
        'before': snapshot(before),
    }
    write_receipt(output, receipt)
    stop = subprocess.run(['timeout', '--kill-after=2s', '20s', './gradlew', '--stop'], check=False)
    receipt['gradle_stop_exit_code'] = stop.returncode
    receipt['kotlin_sigterm_sent_pids'] = [
        daemon['pid'] for daemon in before if daemon['kind'] == 'kotlin' and stop_kotlin(daemon, group)
    ]

    deadline = time.monotonic() + 20
    remaining = daemons(group)
    while remaining and time.monotonic() < deadline:
        time.sleep(0.25)
        remaining = daemons(group)
    live = {identity(daemon) for daemon in remaining}
    receipt['stopped'] = [daemon for daemon in before if identity(daemon) not in live]
    receipt['after'] = snapshot(remaining)
    write_receipt(output, receipt)
    if stop.returncode != 0 or remaining:
        print('::error::Runner build daemon cleanup failed; see the memory receipt.', flush=True)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(cleanup(Path(sys.argv[1])))
