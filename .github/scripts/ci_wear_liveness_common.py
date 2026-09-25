"""Linux process/resource observations for the manual Wear diagnostic."""
import json
import os
from pathlib import Path
from datetime import datetime, timezone

def utc():
    return datetime.now(timezone.utc).isoformat()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def append(path, value):
    with path.open('a') as stream:
        stream.write(json.dumps(value) + '\n')


def process_rows():
    rows = []
    for directory in Path('/proc').glob('[0-9]*'):
        try:
            command = (directory / 'comm').read_text().strip()
            if command != 'java':
                continue
            raw = (directory / 'stat').read_text().rsplit(')', 1)[1].split()
            rows.append({'pid': int(directory.name), 'parent_pid': int(raw[1]),
                         'start_ticks': int(raw[19]), 'state': raw[0],
                         'cpu_ticks': int(raw[11]) + int(raw[12]),
                         'rss_bytes': int(raw[21]) * os.sysconf('SC_PAGE_SIZE')})
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
