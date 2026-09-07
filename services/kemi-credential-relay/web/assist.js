"use strict";

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();
const fragment = new URLSearchParams(location.hash.slice(1));
const sessionId = fragment.get("s") || "";
const writeToken = fragment.get("w") || "";
const receiverPublicKey = fragment.get("k") || "";
const verificationCode = fragment.get("c") || "------";
const accountLabel = fragment.get("a") || "";
const routePrefix = location.pathname.endsWith("/assist")
  ? location.pathname.slice(0, -"/assist".length)
  : "/kemi-assist";

history.replaceState(null, "", location.pathname);

const form = document.getElementById("form");
const input = document.getElementById("credential");
const submit = document.getElementById("submit");
const status = document.getElementById("status");
const verification = document.getElementById("verification");
const account = document.getElementById("account");

verification.textContent = /^\d{6}$/.test(verificationCode) ? verificationCode : "------";

function fromBase64Url(value) {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/");
  const padding = "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(normalized + padding);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function toBase64Url(value) {
  let binary = "";
  for (const byte of new Uint8Array(value)) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function setStatus(message, kind) {
  status.textContent = message;
  status.className = `status ${kind || ""}`;
}

function validSessionParameters() {
  return /^[A-Za-z0-9_-]{32,64}$/.test(sessionId)
    && /^[A-Za-z0-9_-]{32,128}$/.test(writeToken)
    && /^[A-Za-z0-9_-]{80,256}$/.test(receiverPublicKey)
    && /^\d{6}$/.test(verificationCode);
}

async function encryptCredential(credential) {
  const receiverKey = await crypto.subtle.importKey(
    "spki",
    fromBase64Url(receiverPublicKey),
    { name: "ECDH", namedCurve: "P-256" },
    false,
    [],
  );
  const senderKeys = await crypto.subtle.generateKey(
    { name: "ECDH", namedCurve: "P-256" },
    true,
    ["deriveBits"],
  );
  const sharedSecret = await crypto.subtle.deriveBits(
    { name: "ECDH", public: receiverKey },
    senderKeys.privateKey,
    256,
  );
  const keyMaterial = await crypto.subtle.importKey("raw", sharedSecret, "HKDF", false, ["deriveKey"]);
  const salt = await crypto.subtle.digest("SHA-256", textEncoder.encode(`kemi-credential-relay-v1|${sessionId}`));
  const encryptionKey = await crypto.subtle.deriveKey(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt,
      info: textEncoder.encode("KEMI credential transfer v1"),
    },
    keyMaterial,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt"],
  );
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const plaintext = textEncoder.encode(JSON.stringify({ authorizationCode: credential }));
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv: nonce,
      additionalData: textEncoder.encode(`v1|${sessionId}`),
      tagLength: 128,
    },
    encryptionKey,
    plaintext,
  );
  plaintext.fill(0);
  const senderPublicKey = await crypto.subtle.exportKey("spki", senderKeys.publicKey);
  return {
    version: 1,
    senderPublicKey: toBase64Url(senderPublicKey),
    nonce: toBase64Url(nonce),
    ciphertext: toBase64Url(ciphertext),
  };
}

if (accountLabel) {
  try {
    account.textContent = `当前账号：${textDecoder.decode(fromBase64Url(accountLabel))}`;
    account.hidden = false;
  } catch {
    account.hidden = true;
  }
}

if (!validSessionParameters() || !window.isSecureContext || !crypto.subtle) {
  form.hidden = true;
  setStatus("二维码无效、已过期，或当前浏览器不支持安全加密。请返回平板重新生成。", "error");
} else {
  input.focus();
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const credential = input.value;
  if (!credential) return;
  submit.disabled = true;
  input.disabled = true;
  setStatus("正在本机加密…");
  try {
    const payload = await encryptCredential(credential);
    input.value = "";
    const response = await fetch(`${routePrefix}/v1/sessions/${encodeURIComponent(sessionId)}/payload`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "X-KEMI-Write-Token": writeToken,
      },
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error(`relay_${response.status}`);
    form.hidden = true;
    setStatus("已安全传送，请回到平板继续登录。", "success");
  } catch {
    submit.disabled = false;
    input.disabled = false;
    setStatus("传送失败。请检查网络，或在平板重新生成二维码。", "error");
  }
});
