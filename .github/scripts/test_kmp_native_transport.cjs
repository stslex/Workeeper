'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const { backend, children, download, field } = require('./download_kmp_native_evidence.cjs');

const run = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const aggregate = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const producer = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
const token = scopes => `header.${Buffer.from(JSON.stringify({ scp: scopes })).toString('base64url')}.signature`;
const bytes = Buffer.from('the exact archived bytes');
const digest = crypto.createHash('sha256').update(bytes).digest('hex');
function fixture() {
  const needs = { 'native-source': { result: 'success' } };
  for (const [index, role] of ['simulator', 'device'].entries()) {
    needs[`kmp-ios-${role}`] = { result: 'success', outputs: { artifact_id: String(index + 10), artifact_digest: digest } };
  }
  return { NATIVE_NEEDS: JSON.stringify(needs), GITHUB_RUN_ID: '15',
    ACTIONS_RUNTIME_TOKEN: token(`Actions.Results:${run}:${aggregate}`), ACTIONS_RESULTS_URL: 'https://results.example' };
}

async function probe(change = () => {}) {
  const env = fixture();
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'native-transport-'));
  const requests = [];
  let role;
  const fetchImpl = async (url, options) => {
    requests.push({ url, options });
    if (url.pathname.endsWith('/ListArtifacts')) {
      assert.equal(options.redirect, 'error');
      assert.equal(options.headers.Authorization, `Bearer ${env.ACTIONS_RUNTIME_TOKEN}`);
      const body = JSON.parse(options.body);
      assert.equal(body.workflow_run_backend_id, run);
      assert.equal(body.workflow_job_run_backend_id, aggregate);
      role = body.id_filter === '10' ? 'simulator' : 'device';
      const response = { artifacts: [{ database_id: body.id_filter, name: `kmp-ios-${role}-15-1`,
        workflow_run_backend_id: run, workflow_job_run_backend_id: producer, digest }] };
      const override = change('list', response);
      return override || Response.json(response);
    }
    if (url.pathname.endsWith('/GetSignedArtifactURL')) {
      const body = JSON.parse(options.body);
      assert.equal(body.workflow_job_run_backend_id, producer);
      assert.equal(body.workflow_run_backend_id, run);
      const response = { signed_url: 'https://storage.example/archive?signature=secret-fixture' };
      const override = change('signed', response);
      return override || Response.json(response);
    }
    assert.equal(url.origin, 'https://storage.example');
    assert.equal(options.headers, undefined);
    assert.equal(options.redirect, 'error');
    return change('blob', bytes) || new Response(bytes);
  };
  try {
    await download({ env, fetchImpl, directory });
    for (const role of ['simulator', 'device']) assert.deepEqual(fs.readFileSync(path.join(directory, `${role}.zip`)), bytes);
    assert.equal(requests.length, 6);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

test('runtime scope is unique and well formed', () => {
  assert.equal(backend(fixture().ACTIONS_RUNTIME_TOKEN).workflow_run_backend_id, run);
  for (const scope of ['', `Actions.Results:${run}`, `Actions.Results:${run}:${aggregate} Actions.Results:${run}:${aggregate}`]) {
    assert.throws(() => backend(token(scope)));
  }
  assert.throws(() => backend('malformed'));
});

test('snake and camel protobuf names agree or fail closed', () => {
  assert.equal(field({ signed_url: 'url' }, 'signed_url'), 'url');
  assert.equal(field({ signedUrl: 'url' }, 'signed_url'), 'url');
  assert.equal(field({ signed_url: 'url', signedUrl: 'url' }, 'signed_url'), 'url');
  assert.throws(() => field({ signed_url: 'a', signedUrl: 'b' }, 'signed_url'));
});

test('all children and artifact outputs are mandatory', () => {
  for (const role of ['simulator', 'device']) {
    for (const state of ['failure', 'cancelled', 'skipped', undefined]) {
      const needs = JSON.parse(fixture().NATIVE_NEEDS);
      needs[`kmp-ios-${role}`].result = state;
      assert.throws(() => children(needs));
    }
  }
});

test('exact-id transport uses producer backend and never sends runtime auth to blob', async () => { await probe(); });

test('optional runtime digest can be absent; mandatory raw digest still verifies', async () => {
  await probe((stage, response) => { if (stage === 'list') delete response.artifacts[0].digest; });
});

test('lowerCamelCase responses preserve the same identity', async () => {
  await probe((stage, response) => {
    if (stage === 'list') {
      const artifact = response.artifacts[0];
      for (const key of ['workflow_run_backend_id', 'workflow_job_run_backend_id', 'database_id']) {
        artifact[key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())] = artifact[key];
        delete artifact[key];
      }
    }
    if (stage === 'signed') { response.signedUrl = response.signed_url; delete response.signed_url; }
  });
});

test('missing, duplicate, wrong id/name/backend/digest fail', async () => {
  for (const mutate of [r => { r.artifacts = []; }, r => r.artifacts.push(r.artifacts[0]),
    r => { r.artifacts[0].database_id = '99'; }, r => { r.artifacts[0].name = 'wrong'; },
    r => { r.artifacts[0].workflow_run_backend_id = 'invalid'; }, r => { r.artifacts[0].digest = 'b'.repeat(64); }]) {
    await assert.rejects(probe((stage, response) => { if (stage === 'list') mutate(response); }));
  }
});

test('runtime and blob errors, unsafe endpoint and changed raw bytes fail', async () => {
  for (const failStage of ['list', 'signed', 'blob']) {
    await assert.rejects(probe(stage => stage === failStage ? new Response('', { status: 503 }) : undefined));
  }
  await assert.rejects(probe((stage, response) => { if (stage === 'signed') response.signed_url = 'http://storage.example/archive'; }));
  await assert.rejects(probe(stage => stage === 'blob' ? new Response('changed') : undefined));
});
