"""Exercise the checkpoint HTTP boundary and the actual Gradle wrapper entry point."""

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer



class Receiver(BaseHTTPRequestHandler):
    def respond(self, status, body):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        data = self.read_payload()
        if self.path != '/repos/example/project/check-runs' or self.server.deny:
            self.respond(403, {})
            return
        self.server.posts += 1
        self.server.created = data
        self.server.output = data['output']
        if self.server.lose_reply:
            self.close_connection = True
            return
        self.respond(201, {'id': 7})

    def do_PATCH(self):
        if self.path != '/repos/example/project/check-runs/7':
            self.respond(404, {})
            return
        self.server.output = self.read_payload()['output']
        if ':module:compileDebug' in self.server.output['summary']:
            self.server.updated.set()
        self.respond(200, {})

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
        self.server.deny = False
        self.server.lose_reply = False
        self.server.posts = 0
        self.server.updated = threading.Event()
        self.server.output = {}
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def start(self, phase, code):
        executable = self.root / 'gradlew'
        executable.write_text('#!' + sys.executable + '\nimport os,sys,time,signal\n' + code)
        executable.chmod(0o700)
        driver = '''
import http.client, pathlib, sys
import android_gradle_checkpoint as c
c.MEMINFO = pathlib.Path('meminfo')
c.http.client.HTTPSConnection = lambda host, timeout: http.client.HTTPConnection('127.0.0.1', int(sys.argv[2]), timeout=timeout)
sys.exit(c.main(sys.argv[1], interval=0.05))
'''
        git_dir = subprocess.check_output(['git', 'rev-parse', '--absolute-git-dir'], text=True).strip()
        self.sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent),
                   GIT_DIR=git_dir, GITHUB_REPOSITORY='example/project',
                   GITHUB_RUN_ID='42', GITHUB_RUN_ATTEMPT='1', CHECKPOINT_TOKEN='test-only-sentinel')
        return subprocess.Popen([sys.executable, '-c', driver, phase, str(self.server.server_port)],
                                cwd=self.root, env=env, start_new_session=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def test_checkpoint_read_after_abrupt_producer_loss(self):
        self.server.lose_reply = True
        p = self.start('assembleDebugAndroidTest', '''
assert sys.argv[1:] == ['assembleDebugAndroidTest','--full-stacktrace','--console=plain']
assert 'CHECKPOINT_TOKEN' not in os.environ
print('> Task :module:compileDebug FROM-CACHE', flush=True)
print('> Task :bad with-private-output', flush=True)
print('x'*8192 + '> Task :forged', flush=True)
time.sleep(120)
''')
        # Do not let a pipe fill and stall the fixture before the task reaches the parser.
        drain = threading.Thread(target=p.stdout.read, daemon=True)
        drain.start()
        try:
            self.assertTrue(self.server.updated.wait(8), 'no periodic update during Gradle')
            self.assertIsNone(p.poll())
        finally:
            os.killpg(p.pid, signal.SIGKILL)
            p.wait(timeout=5)
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
                                    'swap_free_mib','swap_total_mib','workspace_disk_available_mib'})
        self.assertEqual('assembleDebugAndroidTest', row['phase'])
        self.assertEqual(':module:compileDebug', row['last_observed_task'])
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
