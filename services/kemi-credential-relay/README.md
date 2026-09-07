# KEMI Credential Relay

This service relays a single end-to-end encrypted email app password from a phone browser to KEMI Mail. It never
receives the plaintext credential or the tablet private key. Sessions are held in memory, expire after five minutes,
allow one upload, and are deleted by the tablet after successful decryption.

The QR fragment contains the short-lived write token and tablet public key. URL fragments are not sent in HTTP
requests. The tablet keeps a separate read token that is never placed in the QR code. The browser encrypts with
P-256 ECDH, HKDF-SHA-256, and AES-256-GCM before uploading the envelope.

Run locally with Node.js 22.13 or newer:

```sh
npm test
KEMI_CREDENTIAL_RELAY_HOST=127.0.0.1 KEMI_CREDENTIAL_RELAY_PORT=8789 npm start
```

Production runs behind an HTTPS reverse proxy. Port `8789` must not be published directly to the internet. The
service deliberately has no analytics, third-party resources, credential logging, database, or administrator API.

## Trust boundaries and abuse controls

- Native provider device authorization doesn't use this service. The tablet communicates directly with the provider's
  device and token endpoints.
- The fallback page asks only for a provider-generated email app password and explicitly warns against entering the
  primary email password.
- The HTTPS gateway can observe IP addresses and ciphertext size, but the relay doesn't log request paths, tokens,
  account labels, or payload bodies. The relay has neither the tablet private key nor the read token.
- A write-token holder can submit at most one envelope. A read-token holder can retrieve the same encrypted envelope
  until the tablet deletes the session. Both capabilities expire with the in-memory session.
- The six-digit safety code helps the user detect scanning or page substitution. AES-GCM authenticates the session ID
  as additional data, and invalid ciphertext is rejected by the tablet.
- The gateway overwrites forwarded client-address headers, limits request and connection rates, rejects unrelated
  routes, and keeps the plaintext Node port off the public host network.

For the production topology, startup sequence, health checks, protocol-level verification, recovery procedures, and
Android-side troubleshooting, see the
[KEMI Credential Relay Deployment and Troubleshooting Runbook](../../docs/developer/kemi-credential-relay-runbook.md).
