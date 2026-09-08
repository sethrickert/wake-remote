# Protocol

`POST /api/v1/wake` sends compact UTF-8 JSON `{"target":"<alias>"}`. The canonical string is `POST`, `/api/v1/wake`, Unix seconds, lowercase random nonce hex, and lowercase SHA-256 body hex joined by LF with no trailing LF. `X-Signature` is lowercase hex HMAC-SHA256 using the raw bytes represented by the 64-character key. Headers are `X-Key-Id`, `X-Timestamp`, `X-Nonce`, and `X-Signature`.

`POST /api/v1/enroll` accepts `{"token":"..."}`. Tokens are single-use and expire after 10 minutes. QR payloads use `wakeremote://enroll?v=1&url=<encoded origin>&t=<token>`, where `url` is the bare origin with no `/api` suffix — clients append the API path themselves. See `shared/test-vectors.json` for the authoritative known-answer fixture.

The signed path is part of the canonical string, so it must match byte-for-byte across the server and all three clients. A request signed against the pre-1.0.2 `/v1/wake` path is rejected with `401`.

Health checks are served at both `/healthz` and `/api/healthz`. Every other non-`/api` GET is served by the PWA when `WAKE_STATIC_DIR` is configured.
