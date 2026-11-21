"""Exercise the checkpoint HTTP boundary and the actual Gradle wrapper entry point."""

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Receiver(BaseHTTPRequestHandler):
    def respond(self, status, body):
        if self.server.block_replies:
            time.sleep(1)
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        data = self.read_payload()
        if self.path != '/repos/example/project/check-runs' or self.server.deny:
            self.respond(403, {})
            return
        self.server.posts += 1
        self.server.created = data
        self.capture(data['output'])
        if self.server.lose_reply:
            self.close_connection = True
            return
        self.respond(201, {'id': 7})

    def do_PATCH(self):
        if self.path != '/repos/example/project/check-runs/7':
            self.respond(404, {})
            return
        self.capture(self.read_payload()['output'])
        self.respond(200, {})

    def capture(self, output):
        self.server.output = output
        row = json.loads(output['summary'].splitlines()[1])
        self.server.samples.append(row)
        if row['last_observed_task'] == ':module:compileDebug':
            self.server.updated.set()
        if row.get('wrapper_alive') is False:
            self.server.wrapper_lost.set()
        if row.get('gradle_failure_utc') and row.get('gradle_exit_utc') is None:
            self.server.failed.set()
        if row.get('gradle_exit_utc'):
            self.server.exited.set()

    def do_GET(self):
        if self.path == '/repos/example/project/check-runs/7':
            self.respond(200, self.server.output)
        elif self.path.startswith('/repos/example/project/commits/'):
            self.respond(200, {'check_runs': [dict(self.server.created, id=7)]})
        else:
            self.respond(404, {})

    def read_payload(self):
        return json.loads(self.rfile.read(int(self.headers['Content-Length'])))

    def log_message(self, *args):
        pass


class GradleCheckpointTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        (self.root / 'meminfo').write_text('MemAvailable:    4096 kB\nSwapTotal: 2048 kB\nSwapFree: 1024 kB\n')
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Receiver)
        self.memory_events = self.root / 'cgroups/job/memory.events'
        self.memory_events.parent.mkdir(parents=True)
        self.memory_events.write_text('oom 7\noom_kill 3\n')
        self_cgroup = self.root / 'proc/self/cgroup'
        self_cgroup.parent.mkdir(parents=True)
        self_cgroup.write_text('0::/job\n')
        daemon = self.root / 'proc/1234'
        daemon.mkdir()
        (daemon / 'cmdline').write_bytes(b'java\0org.gradle.launcher.daemon.bootstrap.GradleDaemon\0')
        (daemon / 'cgroup').write_text('0::/job\n')
        self.server.block_replies = False
        self.server.deny = False
        self.server.lose_reply = False
        self.server.posts = 0
        self.server.updated = threading.Event()
        self.server.wrapper_lost = threading.Event()
        self.server.failed = threading.Event()
        self.server.exited = threading.Event()
        self.server.samples = []
        self.server.output = {}
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def start(self, phase, code, mode=False):
        executable = self.root / 'gradlew'
        executable.write_text('#!' + sys.executable + '\nimport os,sys,time,signal\n' + code)
        executable.chmod(0o700)
        driver = '''
import http.client, os, pathlib, sys, time
import android_gradle_checkpoint as c
c.MEMINFO = pathlib.Path('meminfo')
c.PROC = pathlib.Path('proc')
c.CGROUP_ROOT = pathlib.Path('cgroups')
if sys.argv[3] == 'True':
    c.CHECK_DEADLINE = .25
    c.OBSERVER_SHUTDOWN = .75
if sys.argv[3] == 'delay_reparent':
    getppid = os.getppid
    original_parent = getppid()
    reparented_at = None
    def delayed_parent():
        global reparented_at
        current_parent = getppid()
        if current_parent == original_parent:
            return original_parent
        if reparented_at is None:
            reparented_at = time.monotonic()
        return original_parent if time.monotonic() - reparented_at < .2 else current_parent
    def delayed_setsid():
        setsid()
        global original_parent
        original_parent = getppid()
        os.getppid = delayed_parent
    setsid = os.setsid
    os.setsid = delayed_setsid
c.http.client.HTTPSConnection = lambda host, timeout: http.client.HTTPConnection('127.0.0.1', int(sys.argv[2]), timeout=timeout)
sys.exit(c.main(sys.argv[1], interval=0.05))
'''
        git_dir = subprocess.check_output(['git', 'rev-parse', '--absolute-git-dir'], text=True).strip()
        self.sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent),
                   GIT_DIR=git_dir, GITHUB_REPOSITORY='example/project',
                   GITHUB_RUN_ID='42', GITHUB_RUN_ATTEMPT='1', CHECKPOINT_TOKEN='test-only-sentinel',
                   RUNNER_TEMP=str(self.root))
        return subprocess.Popen([sys.executable, '-c', driver, phase, str(self.server.server_port), str(mode)],
                                cwd=self.root, env=env, start_new_session=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def test_checkpoint_read_after_abrupt_producer_loss(self):
        self.server.lose_reply = True
        p = self.start('assembleDebugAndroidTest', '''
assert sys.argv[1:] == ['assembleDebugAndroidTest','--full-stacktrace','--max-workers=2','--console=plain']
assert 'CHECKPOINT_TOKEN' not in os.environ
with open('cgroups/job/memory.events', 'w') as counters:
    counters.write('oom 9\\noom_kill 4\\n')
print('> Task :module:compileDebug FROM-CACHE', flush=True)
print('> Task :bad with-private-output', flush=True)
print('x'*8192 + '> Task :forged', flush=True)
time.sleep(120)
''', mode='delay_reparent')
        # Do not let a pipe fill and stall the fixture before the task reaches the parser.
        drain = threading.Thread(target=p.stdout.read, daemon=True)
        drain.start()
        try:
            self.assertTrue(self.server.updated.wait(8), 'no periodic update during Gradle')
            self.assertIsNone(p.poll())
        finally:
            os.killpg(p.pid, signal.SIGKILL)
            p.wait(timeout=5)
            self.assertTrue(self.server.wrapper_lost.wait(8), 'detached observer did not report wrapper death')
            drain.join(timeout=3)
            p.stdout.close()
            p.stderr.close()
        self.assertEqual(-signal.SIGKILL, p.returncode)
        self.assertEqual(1, self.server.posts)
        reader = HTTPConnection('127.0.0.1', self.server.server_port)
        try:
            reader.request('GET', '/repos/example/project/check-runs/7')
            row = json.loads(json.load(reader.getresponse())['summary'].splitlines()[1])
        finally:
            reader.close()
        self.assertEqual('42/1/assembleDebugAndroidTest', self.server.created['external_id'])
        self.assertEqual(self.sha, self.server.created['head_sha'])
        self.assertEqual(set(row), {'utc','phase','last_observed_task','memory_available_mib',
                                    'swap_free_mib','swap_total_mib','workspace_disk_available_mib',
                                    'last_task_outcome','last_task_utc','wrapper_alive','wrapper_signal',
                                    'gradle_client_alive','gradle_daemon_pids','oom_delta','oom_kill_delta',
                                    'gradle_failure_utc','gradle_exit_utc','gradle_exit_code','gradle_signal'})
        self.assertEqual('assembleDebugAndroidTest', row['phase'])
        self.assertEqual(':module:compileDebug', row['last_observed_task'])
        self.assertEqual('FROM-CACHE', row['last_task_outcome'])
        self.assertFalse(row['wrapper_alive'])
        self.assertIsNone(row['gradle_exit_code'])
        self.assertIsNone(row['gradle_signal'])
        self.assertEqual([1234], row['gradle_daemon_pids'])
        self.assertEqual((2, 1), (row['oom_delta'], row['oom_kill_delta']))
        self.assertEqual([4,1,2], [row[k] for k in ('memory_available_mib','swap_free_mib','swap_total_mib')])
        self.assertGreater(row['workspace_disk_available_mib'], 0)
        self.assertTrue(row['utc'].endswith('+00:00'))

    def test_permission_denied_preserves_gradle_failure(self):
        self.server.deny = True
        p = self.start('lintDebug', "assert sys.argv[1:] == ['lintDebug','--no-configuration-cache','--full-stacktrace','--max-workers=2','--console=plain']\nprint('original Gradle output')\nsys.exit(37)\n")
        stdout, stderr = p.communicate(timeout=10)
        self.assertEqual(37, p.returncode)
        self.assertIn(b'original Gradle output', stdout)
        self.assertIn(b'Checkpoint unavailable', stderr)
        self.assertNotIn(b'test-only-sentinel', stdout+stderr)

    def test_success_and_signal_exit(self):
        for result,code in [('pass',0), ('os.kill(os.getpid(),signal.SIGTERM)',143)]:
            with self.subTest(result=result):
                p = self.start('lintDebug',result+'\n')
                p.communicate(timeout=10)
                self.assertEqual(code,p.returncode)


    def test_assembly_observes_gradle_exit_and_signal(self):
        for code, returncode, exit_code, sig in [
            ('pass', 0, 0, None),
            ('sys.exit(37)', 37, 37, None),
            ('os.kill(os.getpid(), signal.SIGTERM)', 143, None, signal.SIGTERM),
        ]:
            with self.subTest(code=code):
                p = self.start('assembleDebugAndroidTest', code + '\n')
                p.communicate(timeout=10)
                self.assertEqual(returncode, p.returncode)
                result = self.server.samples[-1]
                self.assertEqual(exit_code, result['gradle_exit_code'])
                self.assertEqual(sig, result['gradle_signal'])
                self.assertFalse(result['gradle_client_alive'])
                self.assertTrue(result['gradle_exit_utc'].endswith('+00:00'))

    def test_gradle_failure_publishes_before_process_exit(self):
        p = self.start('assembleDebugAndroidTest', """
print('> Task :module:broken FAILED', flush=True)
time.sleep(2)
sys.exit(37)
""")
        try:
            self.assertTrue(self.server.failed.wait(4))
            observed = next(row for row in self.server.samples if row['gradle_failure_utc'] and not row['gradle_exit_utc'])
            self.assertEqual(':module:broken', observed['last_observed_task'])
            self.assertEqual('FAILED', observed['last_task_outcome'])
            self.assertIsNone(observed['gradle_exit_code'])
        finally:
            p.communicate(timeout=10)
        self.assertEqual(37, p.returncode)

    def test_gradle_exit_publishes_while_descendant_still_holds_output_pipe(self):
        p = self.start('assembleDebugAndroidTest', """
import subprocess
subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(2)'])
sys.exit(17)
""")
        try:
            self.assertTrue(self.server.exited.wait(4))
            self.assertIsNone(p.poll(), 'wrapper should still be draining output')
            self.assertEqual(17, self.server.samples[-1]['gradle_exit_code'])
        finally:
            p.communicate(timeout=10)
        self.assertEqual(17, p.returncode)

    def test_unavailable_cgroup_counters_are_unknown(self):
        self.memory_events.unlink()
        p = self.start('assembleDebugAndroidTest', 'pass\n')
        p.communicate(timeout=10)
        self.assertEqual(0, p.returncode)
        result = self.server.samples[-1]
        self.assertIsNone(result['oom_delta'])
        self.assertIsNone(result['oom_kill_delta'])
        self.assertEqual(0, result['gradle_exit_code'])

    def test_observer_shutdown_is_bounded_when_api_replies_stall(self):
        self.server.block_replies = True
        p = self.start('assembleDebugAndroidTest', 'time.sleep(.3); sys.exit(37)\n', mode=True)
        _, stderr = p.communicate(timeout=4)
        self.assertEqual(37, p.returncode)
        self.assertIn(b'Checkpoint unavailable (TimeoutError)', stderr)
