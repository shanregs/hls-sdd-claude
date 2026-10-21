// Rate-limit and request-size tests for the nginx in front of the app (docker/nginx.*.conf).
// Needs Node 20+ and, for the config checks, Docker. The behaviour tests need the stack running:
//   .\scripts\run-all.ps1            then            node --test scripts/rate-limit.test.mjs
// HLS_BASE_URL overrides the target (default http://localhost:5173). Run it against a local or staging stack only:
// it deliberately trips the limits for this machine's IP, which then needs up to a minute to recover.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.HLS_BASE_URL ?? 'http://localhost:5173';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const login = () =>
  fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'rate.limit.test.nobody', password: 'wrong-password' }),
  });
// An /api/ route outside /api/v1/auth/: the backend answers 401/403/404, never 429, so any 429 came from nginx.
const api = () => fetch(`${BASE}/api/v1/rate-limit-test-no-such-route`);

// Waits until the limiter lets this IP through again, so a test is not poisoned by the one before it (or a previous run).
async function waitUntilNotLimited(request, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const res = await request();
    await res.arrayBuffer();
    if (res.status !== 429) return res.status;
    assert.ok(Date.now() < deadline, 'still rate limited after waiting; is the limit configured as intended?');
    await sleep(2000);
  }
}

const dockerAvailable = spawnSync('docker', ['version'], { stdio: 'ignore' }).status === 0;

for (const variant of ['local', 'prod']) {
  test(`nginx.${variant}.conf passes nginx -t`, { skip: !dockerAvailable && 'docker not available' }, () => {
    const mount = (file, target) => ['-v', `${resolve(ROOT, 'docker', file)}:${target}:ro`];
    const result = spawnSync(
      'docker',
      [
        'run', '--rm', '--add-host', 'backend:127.0.0.1',
        ...mount('nginx.limits.conf', '/etc/nginx/conf.d/00-limits.conf'),
        ...mount('nginx.locations.conf', '/etc/nginx/hls-locations.conf'),
        ...mount(`nginx.${variant}.conf`, '/etc/nginx/conf.d/default.conf'),
        'nginx:alpine', 'nginx', '-t',
      ],
      { encoding: 'utf8' },
    );
    assert.equal(result.status, 0, result.stderr);
  });
}

test('static frontend is served', async () => {
  await waitUntilNotLimited(() => fetch(`${BASE}/`));
  const res = await fetch(`${BASE}/`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type') ?? '', /text\/html/);
});

test('an oversized body is rejected by nginx with 413 before it reaches the backend', async () => {
  await waitUntilNotLimited(api);
  const res = await fetch(`${BASE}/api/v1/rate-limit-test-no-such-route`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: new Uint8Array(13 * 1024 * 1024),
  });
  assert.equal(res.status, 413);
});

test('login endpoints accept only tiny bodies (16 KB)', async () => {
  await waitUntilNotLimited(login);
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'x'.repeat(64 * 1024), password: 'y' }),
  });
  assert.equal(res.status, 413);
});

test('repeated login attempts are limited with 429 once the burst is used up', async () => {
  await waitUntilNotLimited(login);
  await sleep(45_000); // let the bucket refill completely so the burst size is what is measured
  const statuses = [];
  for (let i = 0; i < 60; i++) {
    const res = await login();
    await res.arrayBuffer();
    statuses.push(res.status);
  }
  const limited = statuses.filter((s) => s === 429).length;
  const passed = statuses.length - limited;
  assert.notEqual(statuses[0], 429, 'the first attempt must not be limited');
  assert.ok(passed >= 15, `the burst should let about 21 attempts through, got ${passed}`);
  assert.ok(limited >= 20, `expected the limiter to reject most of the rest, got ${limited} x 429`);
});

test('a 429 on login is answered by nginx and does not stop static files or other API routes', async () => {
  // The login bucket is empty after the previous test, so the next attempt is limited.
  const limitedLogin = await login();
  await limitedLogin.arrayBuffer();
  assert.equal(limitedLogin.status, 429);
  assert.match(limitedLogin.headers.get('server') ?? '', /nginx/);

  const page = await fetch(`${BASE}/`);
  assert.equal(page.status, 200);
  const other = await api();
  await other.arrayBuffer();
  assert.notEqual(other.status, 429, 'the /api/ limit is separate from the login limit');
});

test('a request flood on /api/ is limited, then service recovers', async () => {
  await waitUntilNotLimited(api);
  const responses = await Promise.all(Array.from({ length: 300 }, () => api().then(async (r) => (await r.arrayBuffer(), r.status))));
  const limited = responses.filter((s) => s === 429).length;
  const passed = responses.length - limited;
  assert.ok(passed > 0, 'some requests within the burst must still be served');
  assert.ok(limited > 0, 'a 300-request burst must trip the limit');

  assert.equal(await waitUntilNotLimited(() => fetch(`${BASE}/`)), 200, 'static files are served again after the flood');
  assert.notEqual(await waitUntilNotLimited(api), 429);
});
