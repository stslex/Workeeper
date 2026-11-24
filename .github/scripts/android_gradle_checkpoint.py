"""Run one Android gate while publishing its latest resource sample to GitHub Checks."""

import http.client
import json
import os
from pathlib import Path
import re
import select
import signal
import subprocess
import sys
from tempfile import TemporaryDirectory
import threading
import time
from contextlib import ExitStack, contextmanager
from urllib.parse import urlencode
from datetime import datetime, timezone

ASSEMBLY = 'assembleDebugAndroidTest'
PHASES = {
    ASSEMBLY: ['--full-stacktrace', '--max-workers=2'],
    'lintDebug': ['--no-configuration-cache', '--full-stacktrace', '--max-workers=2'],
}
TASK = re.compile(rb'> Task ((?::[A-Za-z0-9_-]+)+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?')
BUILD_FAILED = re.compile(rb'BUILD FAILED(?: in [0-9hms ]+)?|FAILURE: Build failed with an exception\.')
PROC = Path('/proc')
CGROUP_ROOT = Path('/sys/fs/cgroup')
CHECK_DEADLINE = 5
OBSERVER_SHUTDOWN = 12
DAEMON_CLASS = b'org.gradle.launcher.daemon.bootstrap.GradleDaemon'
MEMINFO = Path('/proc/meminfo')


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def sample(phase, task):
    memory = dict(re.findall(r'^(MemAvailable|SwapFree|SwapTotal):\s+(\d+) kB$', MEMINFO.read_text(), re.M))
    disk = os.statvfs('.')
    return {
        'utc': utc_now(),
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


def cgroup(pid):
    try:
        for line in (PROC / str(pid) / 'cgroup').read_text().splitlines():
            if line.startswith('0::/'):
                path = Path(line[3:])
                if '..' not in path.parts:
                    return path
    except OSError:
        pass
    return None


def oom_counters(group):
    if group is None:
        return {}
    path = CGROUP_ROOT / group.relative_to('/') / 'memory.events'
    try:
        return {name: int(count) for name, count in re.findall(r'^(oom|oom_kill) (\d+)$', path.read_text(), re.M)}
    except OSError:
        return {}


def oom_delta(before, after, key):
    if key in before and key in after and after[key] >= before[key]:
        return after[key] - before[key]
    return None


def daemon_pids(group):
    if group is None:
        return None
    try:
        processes = list(PROC.iterdir())
    except OSError:
        return None
    pids = []
    for path in processes:
        try:
            if path.name.isdecimal() and path.stat().st_uid == os.getuid():
                args = (path / 'cmdline').read_bytes().split(b'\0')
                if DAEMON_CLASS in args and cgroup(path.name) == group:
                    pids.append(int(path.name))
        except OSError:
            continue
    return sorted(pids)[:8]


def client_alive(pid, returncode):
    if returncode is not None:
        return False
    if pid is None:
        return None
    try:
        state = (PROC / str(pid) / 'stat').read_text().rsplit(') ', 1)[1][0]
        return state != 'Z'
    except (OSError, IndexError):
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return False
        except PermissionError:
            return None
    return True


def assembly_sample(directory, wrapper_alive, group, baseline):
    state = {}
    for name in ('progress', 'exit'):
        try:
            state.update(json.loads((directory / (name + '.json')).read_text()))
        except FileNotFoundError:
            pass
    task = state.get('task')
    try:
        row = sample(ASSEMBLY, task)
    except (OSError, KeyError, ValueError):
        row = dict(utc=utc_now(), phase=ASSEMBLY, last_observed_task=task,
                   memory_available_mib=None, swap_free_mib=None, swap_total_mib=None,
                   workspace_disk_available_mib=None)
    counts = oom_counters(group)
    code = state.get('returncode')
    row.update(last_task_outcome=state.get('task_outcome'), last_task_utc=state.get('task_utc'),
               wrapper_alive=wrapper_alive, wrapper_signal=state.get('wrapper_signal'),
               gradle_client_alive=client_alive(state.get('pid'), code), gradle_daemon_pids=daemon_pids(group),
               oom_delta=oom_delta(baseline, counts, 'oom'), oom_kill_delta=oom_delta(baseline, counts, 'oom_kill'),
               gradle_failure_utc=state.get('failure_utc'), gradle_exit_utc=state.get('exit_utc'),
               gradle_exit_code=code if code is not None and code >= 0 else None,
               gradle_signal=-code if code is not None and code < 0 else None)
    return row


def check_deadline(_signal, _frame):
    raise TimeoutError('checkpoint deadline')


def observe_assembly(directory, reader, wrapper, token, interval, group, baseline):
    signal.signal(signal.SIGALRM, check_deadline)
    try:
        signal.setitimer(signal.ITIMER_REAL, CHECK_DEADLINE)
        check = Checkpoint(ASSEMBLY, token)
    except (OSError, KeyError, subprocess.SubprocessError) as exc:
        print(f'Checkpoint setup unavailable ({type(exc).__name__})', file=sys.stderr, flush=True)
        return
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
    stopping = False
    lost_control = False
    while True:
        wrapper_alive = os.getppid() == wrapper
        if lost_control and wrapper_alive:
            wrapper_alive = None
        try:
            signal.setitimer(signal.ITIMER_REAL, CHECK_DEADLINE)
            check.publish(assembly_sample(directory, wrapper_alive, group, baseline))
        except PermissionError:
            print('Checkpoint unavailable (permission denied)', file=sys.stderr, flush=True)
            return
        except (OSError, http.client.HTTPException, ValueError, KeyError) as exc:
            print(f'Checkpoint unavailable ({type(exc).__name__})', file=sys.stderr, flush=True)
        finally:
            signal.setitimer(signal.ITIMER_REAL, 0)
        if stopping or lost_control:
            return
        if select.select([reader], [], [], interval)[0]:
            command = os.read(reader, 4096)
            stopping = b'Q' in command
            lost_control = not command
            if lost_control:
                # Linux closes the wrapper's descriptors before reparenting the observer.
                deadline = time.monotonic() + 1
                while os.getppid() == wrapper and time.monotonic() < deadline:
                    time.sleep(0.02)


def wait_for_observer(pid):
    deadline = time.monotonic() + OBSERVER_SHUTDOWN
    while not os.waitpid(pid, os.WNOHANG)[0]:
        if time.monotonic() >= deadline:
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            os.waitpid(pid, os.WNOHANG)
            return
        time.sleep(0.05)


@contextmanager
def assembly_observer(token, interval):
    with ExitStack() as cleanup:
        pipes = ()
        try:
            directory = Path(cleanup.enter_context(
                TemporaryDirectory(prefix='android-assembly-', dir=os.environ.get('RUNNER_TEMP')),
            ))
            group = cgroup('self')
            baseline = oom_counters(group)
            wrapper = os.getpid()
            pipes = reader, writer = os.pipe()
            os.set_blocking(writer, False)
            pid = os.fork()
        except OSError as exc:
            for fd in pipes:
                os.close(fd)
            print(f'Checkpoint setup unavailable ({type(exc).__name__})', file=sys.stderr, flush=True)
            yield lambda *_args, **_kwargs: None
            return
        if pid == 0:
            try:
                os.close(writer)
                # Wrapper and Gradle keep the runner's process group. The observer survives wrapper loss.
                os.setsid()
                observe_assembly(directory, reader, wrapper, token, interval, group, baseline)
            finally:
                if os.getppid() != wrapper:
                    cleanup.close()
                os._exit(0)
        os.close(reader)

        def publish_state(name, state, wake=False):
            path = directory / (name + '.json')
            scratch = path.with_suffix('.tmp')
            try:
                scratch.write_text(json.dumps(state))
                scratch.replace(path)
                if wake:
                    os.write(writer, b'!')
            except OSError:
                pass

        try:
            yield publish_state
        finally:
            try:
                os.write(writer, b'Q')
            except OSError:
                pass
            os.close(writer)
            wait_for_observer(pid)


def gradle_lines(gradle):
    at_start = True
    for part in iter(lambda: gradle.stdout.readline(8192), b''):
        sys.stdout.buffer.write(part)
        sys.stdout.buffer.flush()
        if at_start and len(part) <= 256:
            yield part.rstrip(b'\r\n')
        at_start = part.endswith(b'\n')


def shell_exit(code):
    return code if code >= 0 else 128 - code


def run_assembly(command, token, interval):
    state = {}
    with assembly_observer(token, interval) as publish:
        def record_signal(signum, _frame):
            state['wrapper_signal'] = signum
            publish('progress', state, wake=True)
            signal.signal(signum, signal.SIG_DFL)
            os.kill(os.getpid(), signum)

        monitored_signals = [
            sig for sig in (signal.SIGTERM, signal.SIGHUP) if signal.getsignal(sig) == signal.SIG_DFL
        ]
        for sig in monitored_signals:
            signal.signal(sig, record_signal)
        try:
            with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as gradle:
                state['pid'] = gradle.pid
                publish('progress', state)
                def record_exit():
                    code = gradle.wait()
                    publish('exit', dict(returncode=code, exit_utc=utc_now()), wake=True)

                waiter = threading.Thread(target=record_exit, daemon=True)
                waiter.start()
                for line in gradle_lines(gradle):
                    task = TASK.fullmatch(line)
                    if task:
                        state['task'] = task[1].decode('ascii')
                        state['task_outcome'] = task[2].decode('ascii') if task[2] else None
                        state['task_utc'] = utc_now()
                    failed = (task is not None and task[2] == b'FAILED') or BUILD_FAILED.fullmatch(line)
                    if failed and 'failure_utc' not in state:
                        state['failure_utc'] = utc_now()
                        publish('progress', state, wake=True)
                    elif task:
                        publish('progress', state)
                waiter.join()
                code = gradle.returncode
        finally:
            for sig in monitored_signals:
                signal.signal(sig, signal.SIG_DFL)
    return shell_exit(code)


def run(phase, command, token, interval=30):
    if phase == ASSEMBLY:
        return run_assembly(command, token, interval)
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
            for line in gradle_lines(gradle):
                match = TASK.fullmatch(line)
                if match:
                    last_task = match[1].decode('ascii')
            code = gradle.wait()
        finally:
            finished.set()
            reporter.join(timeout=6)
    return shell_exit(code)


def main(phase, interval=30):
    token = os.environ.pop('CHECKPOINT_TOKEN')
    return run(phase, ['./gradlew', phase, *PHASES[phase], '--console=plain'], token, interval)


if __name__ == '__main__':
    phase, = sys.argv[1:]
    sys.exit(main(phase))
