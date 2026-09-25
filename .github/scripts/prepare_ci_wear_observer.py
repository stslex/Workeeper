#!/usr/bin/env python3
"""Build a diagnostic-only JUnit observer outside application/test source sets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import selectors
import time
from datetime import datetime, timezone
import subprocess
import urllib.request

VERSION = '6.1.2'
PINS = {
    'junit-platform-launcher': '858197212c1b2acc257c9eec9b450a31923c61c1bc61e66acf0057d57bbe577a',
    'junit-platform-engine': '484e90828846ad6b88efe226b6fe014a75941b32b226e914d9a7758802f46f91',
    'junit-platform-commons': '204894c039d321743ee11e7d1dc8360170d7c64391fbea1211178a645c33a92a',
}
# This class is compiled only into RUNNER_TEMP; it is not an application/test dependency.
SOURCE = r'''
package workeeper.ci;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public final class LivenessObserver implements TestExecutionListener {
    private static final String TARGET_CLASS =
        "io.github.stslex.workeeper.wear.ui.WearTouchTargetGateRuTest";
    private static final String TARGET_METHOD = "everyClickTargetIsVisiblyReachable";
    private final String output = System.getenv("CI_CONTEXT_PHASE_OUTPUT");
    private final String token = System.getenv("CI_CONTEXT_OBSERVER_TOKEN");
    private final String taskPath = System.getenv("CI_WEAR_TASK_PATH");
    private final String flavor = System.getenv("CI_WEAR_FLAVOR");
    private final long pid = ProcessHandle.current().pid();
    private final long birth = startTicks();

    public LivenessObserver() {
        emit("observer_loaded", null, "", null);
    }

    @Override
    public void executionStarted(TestIdentifier test) {
        if (isWearUi(test)) emit("test_start", test, "", null);
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (isWearUi(test)) emit("test_end", test, result.getStatus().name(),
            isTarget(test) ? result.getThrowable().orElse(null) : null);
    }

    @Override
    public void executionSkipped(TestIdentifier test, String reason) {
        if (isWearUi(test)) emit("test_skipped", test, "SKIPPED", null);
    }

    private static boolean isTarget(TestIdentifier test) {
        if (!test.isTest()) return false;
        return test.getSource().filter(MethodSource.class::isInstance)
            .map(MethodSource.class::cast)
            .map(source -> TARGET_CLASS.equals(source.getClassName())
                && TARGET_METHOD.equals(source.getMethodName())).orElse(false);
    }

    private static boolean isWearUi(TestIdentifier test) {
        return test.isTest() && test.getSource().filter(MethodSource.class::isInstance)
            .map(MethodSource.class::cast)
            .map(source -> source.getClassName().startsWith("io.github.stslex.workeeper.wear.ui."))
            .orElse(false);
    }

    private static String failureText(Throwable failure) {
        if (failure == null) return "";
        java.io.StringWriter out = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static long startTicks() {
        try {
            String stat = Files.readString(Path.of("/proc/self/stat"));
            return Long.parseLong(stat.substring(stat.lastIndexOf(')') + 2).trim().split("\\s+")[19]);
        } catch (IOException | RuntimeException failure) {
            return -1;
        }
    }

    private static String quote(String text) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '"') escaped.append('\\').append(c);
            else if (c < 32) escaped.append(String.format("\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.append('"').toString();
    }

    private synchronized void emit(String event, TestIdentifier test, String result, Throwable failure) {
        if (output == null || token == null || taskPath == null || flavor == null) return;
        MethodSource source = test == null ? null : test.getSource()
            .filter(MethodSource.class::isInstance).map(MethodSource.class::cast).orElse(null);
        String fullFailure = failureText(failure);
        String shownFailure = fullFailure.substring(0, Math.min(fullFailure.length(), 32768));
        String line = "{\"event\":" + quote(event)
            + ",\"token\":" + quote(token)
            + ",\"task_path\":" + quote(taskPath)
            + ",\"flavor\":" + quote(flavor)
            + ",\"utc\":" + quote(Instant.now().toString())
            + ",\"epoch_ms\":" + System.currentTimeMillis()
            + ",\"nano_time\":" + System.nanoTime()
            + ",\"pid\":" + pid + ",\"start_ticks\":" + birth
            + ",\"class_name\":" + quote(source == null ? "" : source.getClassName())
            + ",\"method\":" + quote(source == null ? "" : source.getMethodName())
            + ",\"unique_id\":" + quote(test == null ? "" : test.getUniqueId())
            + ",\"display_name\":" + quote(test == null ? "" : test.getDisplayName())
            + ",\"result\":" + quote(result)
            + ",\"throwable\":" + quote(shownFailure)
            + ",\"throwable_sha256\":" + quote(digest(fullFailure))
            + ",\"throwable_capped\":" + (shownFailure.length() < fullFailure.length())
            + "}\n";
        try {
            Files.writeString(Path.of(output, "observer-" + pid + "-" + birth + ".jsonl"), line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException writeFailure) {
            // Observation failure must not change application/test behavior; the supervisor rejects it.
            System.err.println("CI liveness observer could not write an event: "
                + writeFailure.getClass().getSimpleName());
        }
    }
}
'''


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()



def attach_preflight(output, java_bin):
    source = output / 'AttachProbe.java'
    source.write_text('public final class AttachProbe { public static void main(String[] args) throws Exception { '
                      'System.out.println("READY"); System.out.flush(); Thread.sleep(30000); } }\n')
    subprocess.run([str(java_bin / 'javac'), '--release', '21', '-d', str(output), str(source)],
                   check=True, timeout=20, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    started = time.monotonic()
    probe = subprocess.Popen([str(java_bin / 'java'), '-cp', str(output), 'AttachProbe'],
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    receipt = {'utc': datetime.now(timezone.utc).isoformat(), 'pid': probe.pid,
               'scope': 'Same-JDK temporary JVM only; not proof attachment works during the target hang.'}
    try:
        with selectors.DefaultSelector() as ready:
            ready.register(probe.stdout, selectors.EVENT_READ)
            if not ready.select(timeout=8) or probe.stdout.readline().strip() != 'READY':
                raise RuntimeError('Attach probe JVM did not become ready')
        log = output / 'attach-preflight.threads.txt'
        with log.open('w') as stream:
            result = subprocess.run([str(java_bin / 'jcmd'), str(probe.pid), 'Thread.print', '-l'],
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=5, check=False)
        receipt.update(exit_code=result.returncode, thread_dump_sha256=sha(log),
                       main_stack_observed='AttachProbe.main' in log.read_text())
        if result.returncode != 0 or not receipt['main_stack_observed']:
            raise RuntimeError('Same-JDK Thread.print attach preflight failed')
        receipt['status'] = 'PASS'
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as failure:
        receipt.update(status='FAIL', error_type=type(failure).__name__, error=str(failure))
        raise
    finally:
        if probe.poll() is None:
            probe.terminate()
            try:
                probe.wait(timeout=2)
            except subprocess.TimeoutExpired:
                probe.kill()
                probe.wait(timeout=2)
        probe.stdout.close()
        receipt.update(probe_exit_code=probe.returncode, elapsed_seconds=time.monotonic() - started)
        (output / 'attach-preflight.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print('CI_WEAR_ATTACH_PREFLIGHT ' + json.dumps(receipt), flush=True)


def prepare(output):
    output.mkdir(parents=True, exist_ok=False)
    jars = []
    resolved = []
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    for artifact, expected in PINS.items():
        name = f'{artifact}-{VERSION}.jar'
        candidates = sorted((cache / 'caches/modules-2/files-2.1/org.junit.platform' /
                             artifact / VERSION).glob('*/' + name))
        source = next((path for path in candidates if sha(path) == expected), None)
        target = output / name
        if source is None:
            url = f'https://repo.maven.apache.org/maven2/org/junit/platform/{artifact}/{VERSION}/{name}'
            with urllib.request.urlopen(url, timeout=30) as response:
                target.write_bytes(response.read())
            origin = 'pinned Maven Central compiler input'
        else:
            shutil.copy2(source, target)
            origin = 'verified existing Gradle dependency cache'
        if sha(target) != expected:
            raise SystemExit(f'Compiler dependency checksum mismatch: {artifact}')
        jars.append(str(target))
        resolved.append({'artifact': artifact, 'version': VERSION, 'sha256': expected, 'origin': origin})
    java = output / 'LivenessObserver.java'
    java.write_text(SOURCE)
    classes = output / 'classes'
    classes.mkdir()
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    attach_preflight(output, java_bin)
    with (output / 'compile.log').open('w') as log:
        subprocess.run([str(java_bin / 'javac'), '--release', '21', '-classpath', os.pathsep.join(jars),
                        '-d', str(classes), str(java)], check=True, timeout=30,
                       stdout=log, stderr=subprocess.STDOUT)
    service = classes / 'META-INF/services/org.junit.platform.launcher.TestExecutionListener'
    service.parent.mkdir(parents=True)
    service.write_text('workeeper.ci.LivenessObserver\n')
    jar = output / 'observer.jar'
    subprocess.run([str(java_bin / 'jar'), '--create', '--file', str(jar), '-C', str(classes), '.'],
                   check=True, timeout=10)
    (output / 'observer.json').write_text(json.dumps({
        'source_sha256': sha(java), 'jar_sha256': sha(jar), 'compiler_inputs': resolved,
        'junit_version': VERSION, 'java_release': 21,
        'service': 'org.junit.platform.launcher.TestExecutionListener',
        'provider': 'workeeper.ci.LivenessObserver',
        'note': 'Target Test classpath changes; this is diagnostic evidence, not a fresh gate.',
    }, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args().output.resolve())
