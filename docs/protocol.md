# Protocol

`POST /v1/wake` sends compact UTF-8 JSON `{"target":"<alias>"}`. The canonical string is `POST`, `/v1/wake`, Unix seconds, lowercase random nonce hex, and lowercase SHA-256 body hex joined by LF with no trailing LF. `X-Signature` is lowercase hex HMAC-SHA256 using the raw bytes represented by the 64-character key. Headers are `X-Key-Id`, `X-Timestamp`, `X-Nonce`, and `X-Signature`.

`POST /v1/enroll` accepts `{"token":"..."}`. Tokens are single-use and expire after 10 minutes. QR payloads use `wakeremote://enroll?v=1&url=<encoded origin>&t=<token>`. See `shared/test-vectors.json` for the authoritative known-answer fixture.
