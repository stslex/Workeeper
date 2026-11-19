"""Run one Android gate while publishing its latest resource sample to GitHub Checks."""

import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
from urllib.parse import urlencode
from datetime import datetime, timezone

PHASES = {
    'assembleDebugAndroidTest': ['--full-stacktrace', '--max-workers=2'],
    'lintDebug': ['--no-configuration-cache', '--full-stacktrace', '--max-workers=2'],
}
TASK = re.compile(rb'> Task ((?::[A-Za-z0-9_-]+)+)(?: (?:UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?')
MEMINFO = Path('/proc/meminfo')


def sample(phase, task):
    memory = dict(re.findall(r'^(MemAvailable|SwapFree|SwapTotal):\s+(\d+) kB$', MEMINFO.read_text(), re.M))
    disk = os.statvfs('.')
    return {
        'utc': datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'phase': phase,
        'last_observed_task': task,
        'memory_available_mib': int(memory['MemAvailable']) // 1024,
        'swap_free_mib': int(memory['SwapFree']) // 1024,
        'swap_total_mib': int(memory['SwapTotal']) // 1024,
        'workspace_disk_available_mib': disk.f_bavail * disk.f_frsize // (1024 ** 2),
    }


class Checkpoint:
    def __init__(self, phase, token):
        self.phase = phase
        self.token = token
        self.repository = os.environ['GITHUB_REPOSITORY']
        self.sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        self.external_id = f"{os.environ['GITHUB_RUN_ID']}/{os.environ['GITHUB_RUN_ATTEMPT']}/{phase}"
        self.check_id = None
        self.name = f'Android checkpoint ({phase})'
        self.creation_uncertain = False

    def request(self, method, path, payload=None):
        conn = http.client.HTTPSConnection('api.github.com', timeout=5)
        try:
            conn.connect()
            if method == 'POST':
                self.creation_uncertain = True
            conn.request(method, f'/repos/{self.repository}/{path}',
                         json.dumps(payload) if payload is not None else None, headers={
                             'Authorization': 'Bearer ' + self.token, 'User-Agent': 'workeeper-android-checkpoint',
                             'Content-Type': 'application/json', 'Accept': 'application/vnd.github+json',
                         })
            response = conn.getresponse()
            if response.status not in (200, 201):
                if method == 'POST' and 400 <= response.status < 500:
                    self.creation_uncertain = False
                if response.status in (401, 403) and not (response.getheader('Retry-After') or
                                                          response.getheader('X-RateLimit-Remaining') == '0'):
                    raise PermissionError('check access denied')
                raise ValueError(f'GitHub Checks HTTP {response.status}')
            return json.load(response)
        finally:
            conn.close()

    def publish(self, row):
        output = {'title': self.phase, 'summary': '```json\n' + json.dumps(row) + '\n```'}
        if self.check_id is None and self.creation_uncertain:
            # After an ambiguous POST, recover the record by identity. Never create a duplicate.
            query = urlencode({'check_name': self.name, 'filter': 'all', 'per_page': 100})
            checks = self.request('GET', f'commits/{self.sha}/check-runs?{query}')['check_runs']
            matches = [c['id'] for c in checks if c['external_id'] == self.external_id
                       and c['head_sha'] == self.sha and c['name'] == self.name]
            if len(matches) != 1:
                raise ValueError('checkpoint creation unconfirmed')
            self.check_id = int(matches[0])
        if self.check_id is None:
            check = self.request('POST', 'check-runs', dict(name=self.name, head_sha=self.sha, output=output,
                                 external_id=self.external_id, status='completed', conclusion='neutral'))
            self.check_id = int(check['id'])
        else:
            self.request('PATCH', f'check-runs/{self.check_id}', {'output': output})


def run(phase, command, token, interval=30):
    finished = threading.Event()
    last_task = None

    def report():
        try:
            check = Checkpoint(phase, token)
            while True:
                final = finished.is_set()
                try:
                    check.publish(sample(phase, last_task))
                except PermissionError:
                    print('Checkpoint unavailable (permission denied)', file=sys.stderr, flush=True)
                    return
                except (OSError, http.client.HTTPException, ValueError, KeyError) as exc:
                    print(f'Checkpoint unavailable ({type(exc).__name__})', file=sys.stderr, flush=True)
                if final:
                    return
                finished.wait(interval)
        except (KeyError, OSError, subprocess.SubprocessError) as exc:
            print(f'Checkpoint setup unavailable ({type(exc).__name__})', file=sys.stderr, flush=True)

    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as gradle:
        reporter = threading.Thread(target=report, daemon=True)
        reporter.start()
        try:
            at_start = True
            for part in iter(lambda: gradle.stdout.readline(8192), b''):
                sys.stdout.buffer.write(part)
                sys.stdout.buffer.flush()
                if at_start and len(part) <= 256:
                    match = TASK.fullmatch(part.rstrip(b'\r\n'))
                    if match:
                        last_task = match[1].decode('ascii')
                at_start = part.endswith(b'\n')
            code = gradle.wait()
        finally:
            finished.set()
            reporter.join(timeout=6)
    return code if code >= 0 else 128 - code


def main(phase, interval=30):
    token = os.environ.pop('CHECKPOINT_TOKEN')
    return run(phase, ['./gradlew', phase, *PHASES[phase], '--console=plain'], token, interval)


if __name__ == '__main__':
    phase, = sys.argv[1:]
    sys.exit(main(phase))
