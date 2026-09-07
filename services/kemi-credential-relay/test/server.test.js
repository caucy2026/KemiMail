"use strict";

const assert = require("node:assert/strict");
const { afterEach, test } = require("node:test");
const crypto = require("node:crypto");
const { createRelayServer, hashToken } = require("../server");

const running = [];

afterEach(async () => {
  await Promise.all(running.splice(0).map((relay) => relay.close()));
});

async function startRelay(options = {}) {
  const relay = createRelayServer({ logger: { error() {} }, ...options });
  await new Promise((resolve) => relay.server.listen(0, "127.0.0.1", resolve));
  running.push(relay);
  const address = relay.server.address();
  return { relay, baseUrl: `http://127.0.0.1:${address.port}/kemi-assist` };
}

function randomToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString("base64url");
}

async function createSession(baseUrl) {
  const sessionId = randomToken(24);
  const writeToken = randomToken();
  const readToken = randomToken();
  const response = await fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      version: 1,
      sessionId,
      writeTokenHash: hashToken(writeToken),
      readTokenHash: hashToken(readToken),
    }),
  });
  assert.equal(response.status, 201);
  return { sessionId, writeToken, readToken };
}

test("serves the phone page with strict security headers", async () => {
  const { baseUrl } = await startRelay();
  const response = await fetch(`${baseUrl}/assist`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-security-policy"), /default-src 'none'/);
  assert.equal(response.headers.get("referrer-policy"), "no-referrer");
  assert.match(await response.text(), /手机辅助输入授权码/);
});

test("stores and returns only the encrypted envelope", async () => {
  const { baseUrl } = await startRelay();
  const session = await createSession(baseUrl);
  const pending = await fetch(`${baseUrl}/v1/sessions/${session.sessionId}/payload`, {
    headers: { "X-KEMI-Read-Token": session.readToken },
  });
  assert.equal(pending.status, 204);

  const payload = {
    version: 1,
    senderPublicKey: randomToken(80),
    nonce: randomToken(12),
    ciphertext: randomToken(64),
  };
  const upload = await fetch(`${baseUrl}/v1/sessions/${session.sessionId}/payload`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-KEMI-Write-Token": session.writeToken,
    },
    body: JSON.stringify(payload),
  });
  assert.equal(upload.status, 204);

  const download = await fetch(`${baseUrl}/v1/sessions/${session.sessionId}/payload`, {
    headers: { "X-KEMI-Read-Token": session.readToken },
  });
  assert.equal(download.status, 200);
  assert.deepEqual(await download.json(), payload);
});

test("rejects unauthorized access and a second upload", async () => {
  const { baseUrl } = await startRelay();
  const session = await createSession(baseUrl);
  const payload = {
    version: 1,
    senderPublicKey: randomToken(80),
    nonce: randomToken(12),
    ciphertext: randomToken(64),
  };
  const unauthorized = await fetch(`${baseUrl}/v1/sessions/${session.sessionId}/payload`, {
    headers: { "X-KEMI-Read-Token": randomToken() },
  });
  assert.equal(unauthorized.status, 401);

  for (const expectedStatus of [204, 409]) {
    const response = await fetch(`${baseUrl}/v1/sessions/${session.sessionId}/payload`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "X-KEMI-Write-Token": session.writeToken,
      },
      body: JSON.stringify(payload),
    });
    assert.equal(response.status, expectedStatus);
  }
});

test("deletes a completed session and hides expired sessions", async () => {
  let currentTime = 1_000;
  const { baseUrl } = await startRelay({
    sessionTtlMs: 100,
    clock: { now: () => currentTime },
  });
  const first = await createSession(baseUrl);
  const deletion = await fetch(`${baseUrl}/v1/sessions/${first.sessionId}`, {
    method: "DELETE",
    headers: { "X-KEMI-Read-Token": first.readToken },
  });
  assert.equal(deletion.status, 204);
  const deleted = await fetch(`${baseUrl}/v1/sessions/${first.sessionId}/payload`, {
    headers: { "X-KEMI-Read-Token": first.readToken },
  });
  assert.equal(deleted.status, 404);

  const second = await createSession(baseUrl);
  currentTime += 101;
  const expired = await fetch(`${baseUrl}/v1/sessions/${second.sessionId}/payload`, {
    headers: { "X-KEMI-Read-Token": second.readToken },
  });
  assert.equal(expired.status, 404);
});
