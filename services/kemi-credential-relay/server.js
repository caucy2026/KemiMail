#!/usr/bin/env node

"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const PROTOCOL_VERSION = 1;
const ROUTE_PREFIX = "/kemi-assist";
const DEFAULT_SESSION_TTL_MS = 5 * 60 * 1000;
const DEFAULT_MAX_SESSIONS = 1_000;
const MAX_BODY_BYTES = 16 * 1024;
const RATE_WINDOW_MS = 60 * 1000;
const DEFAULT_REQUESTS_PER_MINUTE = 120;
const DEFAULT_CREATES_PER_MINUTE = 12;
const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{32,64}$/;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{32,128}$/;
const TOKEN_HASH_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const BASE64URL_PATTERN = /^[A-Za-z0-9_-]+$/;
const PAYLOAD_ROUTE = new RegExp(`^${ROUTE_PREFIX}/v1/sessions/([A-Za-z0-9_-]{32,64})/payload$`);
const SESSION_ROUTE = new RegExp(`^${ROUTE_PREFIX}/v1/sessions/([A-Za-z0-9_-]{32,64})$`);

const staticAssets = new Map([
  [`${ROUTE_PREFIX}/assist`, ["assist.html", "text/html; charset=utf-8"]],
  [`${ROUTE_PREFIX}/assist/app.js`, ["assist.js", "text/javascript; charset=utf-8"]],
  [`${ROUTE_PREFIX}/assist/style.css`, ["assist.css", "text/css; charset=utf-8"]],
]);

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function json(response, status, body, extraHeaders = {}) {
  const data = Buffer.from(JSON.stringify(body));
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": data.length,
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    ...extraHeaders,
  });
  response.end(data);
}

function empty(response, status, extraHeaders = {}) {
  response.writeHead(status, {
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    ...extraHeaders,
  });
  response.end();
}

function errorBody(code) {
  return { version: PROTOCOL_VERSION, ok: false, error: code };
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    if (!String(request.headers["content-type"] || "").toLowerCase().startsWith("application/json")) {
      const error = new Error("unsupported_media_type");
      error.statusCode = 415;
      reject(error);
      return;
    }

    const chunks = [];
    let received = 0;
    request.on("data", (chunk) => {
      received += chunk.length;
      if (received > MAX_BODY_BYTES) {
        const error = new Error("payload_too_large");
        error.statusCode = 413;
        request.destroy(error);
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        const error = new Error("invalid_json");
        error.statusCode = 400;
        reject(error);
      }
    });
    request.on("error", reject);
  });
}

function hashToken(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("base64url");
}

function tokenMatches(value, expectedHash) {
  if (!TOKEN_PATTERN.test(value || "") || !TOKEN_HASH_PATTERN.test(expectedHash || "")) {
    return false;
  }
  const actual = Buffer.from(hashToken(value), "base64url");
  const expected = Buffer.from(expectedHash, "base64url");
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
}

function validEncryptedPayload(value) {
  if (!value || value.version !== PROTOCOL_VERSION
      || typeof value.senderPublicKey !== "string"
      || typeof value.nonce !== "string"
      || typeof value.ciphertext !== "string") {
    return false;
  }
  return value.senderPublicKey.length >= 80 && value.senderPublicKey.length <= 256
    && value.nonce.length >= 16 && value.nonce.length <= 32
    && value.ciphertext.length >= 24 && value.ciphertext.length <= 12_000
    && BASE64URL_PATTERN.test(value.senderPublicKey)
    && BASE64URL_PATTERN.test(value.nonce)
    && BASE64URL_PATTERN.test(value.ciphertext);
}

function createRelayServer(options = {}) {
  const sessionTtlMs = positiveInteger(
    options.sessionTtlMs || process.env.KEMI_CREDENTIAL_SESSION_TTL_MS,
    DEFAULT_SESSION_TTL_MS,
  );
  const maxSessions = positiveInteger(
    options.maxSessions || process.env.KEMI_CREDENTIAL_MAX_SESSIONS,
    DEFAULT_MAX_SESSIONS,
  );
  const requestsPerMinute = positiveInteger(
    options.requestsPerMinute || process.env.KEMI_CREDENTIAL_REQUESTS_PER_MINUTE,
    DEFAULT_REQUESTS_PER_MINUTE,
  );
  const createsPerMinute = positiveInteger(
    options.createsPerMinute || process.env.KEMI_CREDENTIAL_CREATES_PER_MINUTE,
    DEFAULT_CREATES_PER_MINUTE,
  );
  const logger = options.logger || console;
  const clock = options.clock || Date;
  const sessions = new Map();
  const requestBuckets = new Map();
  const createBuckets = new Map();

  function now() {
    return clock.now();
  }

  function pruneSessions() {
    const currentTime = now();
    for (const [sessionId, session] of sessions) {
      if (session.expiresAt <= currentTime) {
        sessions.delete(sessionId);
      }
    }
  }

  function clientAddress(request) {
    return String(request.headers["x-real-ip"] || request.socket.remoteAddress || "unknown");
  }

  function consumeRate(bucketMap, key, limit) {
    const currentTime = now();
    const bucket = bucketMap.get(key);
    if (!bucket || currentTime - bucket.startedAt >= RATE_WINDOW_MS) {
      bucketMap.set(key, { startedAt: currentTime, count: 1 });
      return true;
    }
    bucket.count += 1;
    return bucket.count <= limit;
  }

  function serveStatic(response, pathname) {
    const asset = staticAssets.get(pathname);
    if (!asset) return false;
    const [fileName, contentType] = asset;
    const data = fs.readFileSync(path.join(__dirname, "web", fileName));
    response.writeHead(200, {
      "Content-Type": contentType,
      "Content-Length": data.length,
      "Cache-Control": "no-store",
      "Content-Security-Policy": "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
      "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
      "X-Frame-Options": "DENY",
    });
    response.end(data);
    return true;
  }

  async function createSession(request, response) {
    const address = clientAddress(request);
    if (!consumeRate(createBuckets, address, createsPerMinute)) {
      json(response, 429, errorBody("rate_limited"), { "Retry-After": "60" });
      return;
    }
    pruneSessions();
    if (sessions.size >= maxSessions) {
      json(response, 503, errorBody("relay_busy"), { "Retry-After": "5" });
      return;
    }
    const body = await readJson(request);
    if (!body || body.version !== PROTOCOL_VERSION
        || !SESSION_ID_PATTERN.test(body.sessionId || "")
        || !TOKEN_HASH_PATTERN.test(body.writeTokenHash || "")
        || !TOKEN_HASH_PATTERN.test(body.readTokenHash || "")) {
      json(response, 400, errorBody("invalid_request"));
      return;
    }
    if (sessions.has(body.sessionId)) {
      json(response, 409, errorBody("session_conflict"));
      return;
    }
    const expiresAt = now() + sessionTtlMs;
    sessions.set(body.sessionId, {
      writeTokenHash: body.writeTokenHash,
      readTokenHash: body.readTokenHash,
      expiresAt,
      payload: null,
    });
    json(response, 201, {
      version: PROTOCOL_VERSION,
      ok: true,
      expiresAt,
    });
  }

  async function putPayload(request, response, sessionId) {
    pruneSessions();
    const session = sessions.get(sessionId);
    if (!session) {
      json(response, 404, errorBody("session_not_found"));
      return;
    }
    const token = String(request.headers["x-kemi-write-token"] || "");
    if (!tokenMatches(token, session.writeTokenHash)) {
      json(response, 401, errorBody("unauthorized"));
      return;
    }
    if (session.payload !== null) {
      json(response, 409, errorBody("payload_already_submitted"));
      return;
    }
    const body = await readJson(request);
    if (!validEncryptedPayload(body)) {
      json(response, 400, errorBody("invalid_payload"));
      return;
    }
    session.payload = Object.freeze({
      version: PROTOCOL_VERSION,
      senderPublicKey: body.senderPublicKey,
      nonce: body.nonce,
      ciphertext: body.ciphertext,
    });
    empty(response, 204);
  }

  function getPayload(request, response, sessionId) {
    pruneSessions();
    const session = sessions.get(sessionId);
    if (!session) {
      json(response, 404, errorBody("session_not_found"));
      return;
    }
    const token = String(request.headers["x-kemi-read-token"] || "");
    if (!tokenMatches(token, session.readTokenHash)) {
      json(response, 401, errorBody("unauthorized"));
      return;
    }
    if (session.payload === null) {
      empty(response, 204);
      return;
    }
    json(response, 200, session.payload);
  }

  function deleteSession(request, response, sessionId) {
    pruneSessions();
    const session = sessions.get(sessionId);
    if (!session) {
      empty(response, 204);
      return;
    }
    const token = String(request.headers["x-kemi-read-token"] || "");
    if (!tokenMatches(token, session.readTokenHash)) {
      json(response, 401, errorBody("unauthorized"));
      return;
    }
    sessions.delete(sessionId);
    empty(response, 204);
  }

  const server = http.createServer(async (request, response) => {
    try {
      const pathname = new URL(request.url, "http://relay.invalid").pathname;
      const address = clientAddress(request);
      if (!consumeRate(requestBuckets, address, requestsPerMinute)) {
        json(response, 429, errorBody("rate_limited"), { "Retry-After": "60" });
        return;
      }

      if (request.method === "GET" && serveStatic(response, pathname)) return;
      if (request.method === "GET" && pathname === `${ROUTE_PREFIX}/health`) {
        json(response, 200, { version: PROTOCOL_VERSION, ok: true });
        return;
      }
      if (request.method === "POST" && pathname === `${ROUTE_PREFIX}/v1/sessions`) {
        await createSession(request, response);
        return;
      }

      const payloadMatch = pathname.match(PAYLOAD_ROUTE);
      if (payloadMatch && request.method === "PUT") {
        await putPayload(request, response, payloadMatch[1]);
        return;
      }
      if (payloadMatch && request.method === "GET") {
        getPayload(request, response, payloadMatch[1]);
        return;
      }

      const sessionMatch = pathname.match(SESSION_ROUTE);
      if (sessionMatch && request.method === "DELETE") {
        deleteSession(request, response, sessionMatch[1]);
        return;
      }
      json(response, 404, errorBody("not_found"));
    } catch (error) {
      const statusCode = Number(error.statusCode) || 500;
      if (statusCode >= 500) logger.error(`credential relay request failed: ${error.message}`);
      if (!response.headersSent) {
        json(response, statusCode, errorBody(statusCode >= 500 ? "internal_error" : error.message));
      } else {
        response.destroy();
      }
    }
  });

  const maintenanceTimer = setInterval(pruneSessions, Math.min(sessionTtlMs, 60_000));
  maintenanceTimer.unref();

  return {
    server,
    close: () => new Promise((resolve, reject) => {
      clearInterval(maintenanceTimer);
      server.close((error) => error ? reject(error) : resolve());
    }),
  };
}

if (require.main === module) {
  const host = process.env.KEMI_CREDENTIAL_RELAY_HOST || "127.0.0.1";
  const port = positiveInteger(process.env.KEMI_CREDENTIAL_RELAY_PORT, 8789);
  const relay = createRelayServer();
  relay.server.listen(port, host, () => {
    console.info(`KEMI credential relay listening on http://${host}:${port}`);
  });

  const shutdown = () => relay.close().finally(() => process.exit(0));
  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);
}

module.exports = {
  PROTOCOL_VERSION,
  ROUTE_PREFIX,
  createRelayServer,
  hashToken,
};
