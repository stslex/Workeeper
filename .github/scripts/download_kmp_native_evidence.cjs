'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');

function requireValue(value, message) {
  if (!value) throw new Error(`Native artifact transport: ${message}`);
}

function field(object, snake) {
  const camel = snake.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
  requireValue(!(snake in object && camel in object && object[snake] !== object[camel]), 'ambiguous response field');
  return object[snake] ?? object[camel];
}

function backend(token) {
  const pieces = token.split('.');
  requireValue(pieces.length === 3, 'malformed runtime token');
  const claims = JSON.parse(Buffer.from(pieces[1], 'base64url').toString('utf8'));
  const scopes = typeof claims.scp === 'string' ? claims.scp.split(/\s+/).filter(s => s.startsWith('Actions.Results:')) : [];
  requireValue(scopes.length === 1, 'missing or ambiguous runtime scope');
  const match = /^Actions\.Results:([a-f0-9-]{36}):([a-f0-9-]{36})$/i.exec(scopes[0]);
  requireValue(match, 'malformed runtime backend identifiers');
  return { workflow_run_backend_id: match[1], workflow_job_run_backend_id: match[2] };
}

function children(needs) {
  requireValue(needs['native-source']?.result === 'success', 'source did not resolve');
  return ['simulator', 'device'].map(role => {
    const child = needs[`kmp-ios-${role}`];
    requireValue(child?.result === 'success', `${role} did not succeed`);
    const { artifact_id: id, artifact_digest: digest } = child.outputs;
    requireValue(/^[1-9]\d*$/.test(id || ''), 'missing artifact identifier');
    requireValue(/^[a-f0-9]{64}$/.test(digest || ''), 'missing artifact digest');
    return { role, id, digest };
  });
}

async function download({ env = process.env, fetchImpl = fetch, directory = 'build/kmp-verification' } = {}) {
  const wanted = children(JSON.parse(env.NATIVE_NEEDS));
  const identity = backend(env.ACTIONS_RUNTIME_TOKEN || '');
  const service = new URL(env.ACTIONS_RESULTS_URL);
  requireValue(service.protocol === 'https:' && !service.username && !service.password, 'invalid results endpoint');
  const post = async (method, body) => {
    const url = new URL(`/twirp/github.actions.results.api.v1.ArtifactService/${method}`, service);
    const response = await fetchImpl(url, {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
      headers: { Authorization: `Bearer ${env.ACTIONS_RUNTIME_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    requireValue(response.ok, `${method} failed (${response.status})`);
    return response.json();
  };
  fs.mkdirSync(directory, { recursive: true });
  for (const child of wanted) {
    const listed = await post('ListArtifacts', { ...identity, id_filter: child.id });
    requireValue(Array.isArray(listed.artifacts) && listed.artifacts.length === 1, 'missing or duplicate artifact');
    const artifact = listed.artifacts[0];
    requireValue(String(field(artifact, 'database_id')) === child.id, 'wrong artifact identifier');
    for (const key of ['workflow_run_backend_id', 'workflow_job_run_backend_id']) {
      requireValue(/^[a-f0-9-]{36}$/i.test(field(artifact, key) || ''), 'invalid producer backend identity');
    }
    requireValue(new RegExp(`^kmp-ios-${child.role}-${env.GITHUB_RUN_ID}-[1-9]\\d*$`).test(artifact.name), 'wrong artifact name');
    const artifactDigest = field(artifact, 'digest');
    requireValue(artifactDigest == null || artifactDigest === child.digest || artifactDigest === `sha256:${child.digest}`,
      'runtime artifact digest differs');
    const signed = await post('GetSignedArtifactURL', {
      workflow_run_backend_id: field(artifact, 'workflow_run_backend_id'),
      workflow_job_run_backend_id: field(artifact, 'workflow_job_run_backend_id'), name: artifact.name,
    });
    const blob = new URL(field(signed, 'signed_url'));
    requireValue(blob.protocol === 'https:' && !blob.username && !blob.password, 'invalid blob endpoint');
    // Runtime credentials must never accompany the signed storage URL.
    const response = await fetchImpl(blob, { redirect: 'error', signal: AbortSignal.timeout(600000) });
    requireValue(response.ok && response.body, `blob download failed (${response.status})`);
    const hash = crypto.createHash('sha256');
    const stream = Readable.fromWeb(response.body);
    stream.on('data', chunk => hash.update(chunk));
    const target = path.join(directory, `${child.role}.zip`);
    try {
      await pipeline(stream, fs.createWriteStream(target, { flags: 'wx' }));
      requireValue(hash.digest('hex') === child.digest, 'raw ZIP digest differs');
    } catch (error) {
      fs.rmSync(target, { force: true });
      throw error;
    }
  }
}

module.exports = { backend, children, download, field };
