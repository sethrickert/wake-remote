# Wake Remote server

A stdlib-only Python service that verifies HMAC-SHA256 signed requests and emits the
Wake-on-LAN magic packet on the target's network. It also serves the installable PWA at
the origin root, so one container provides both the app and the API.

This archive contains:

```
wake_remote/         the service package
dist/                the built PWA (serve it with WAKE_STATIC_DIR)
Dockerfile
docker-compose.yml
requirements.txt     segno, used only to draw the enrollment QR in the terminal
SERVER-README.md
```

## Requirements

Wake-on-LAN needs an always-on device **on the target's LAN** to emit the packet: a
Raspberry Pi, NAS, container-capable router, an always-on PC, or an off-site host joined
to the LAN by WireGuard/Tailscale. There is no client-only workaround.

## Run it

```bash
tar xzf wake-remote-server-<version>.tar.gz
cd wake-remote-server-<version>
cp -r dist pwa-dist                 # what the compose file bind-mounts
WAKE_SERVER_URL=https://wol.example.com \
WAKE_STATIC_DIR=/srv/pwa \
docker compose up -d --build
docker compose logs wake-remote
```

Or pull the published image instead of building:

```bash
docker pull ghcr.io/sethrickert/wake-remote-server:<tag>
```

For bare metal, install Python 3.13+, set `WAKE_DATA_DIR`, and run
`python -m wake_remote.app`.

The first start generates a persistent `wake.key` with restrictive permissions and prints
a 10-minute enrollment URI plus a terminal QR. Replace the sample `WAKE_TARGETS_JSON`
before a real wake.

## Routing

The server splits `/` from `/api` itself, so a reverse proxy forwards the whole origin
and needs no path rules.

| Request | Result |
|---|---|
| `GET /healthz`, `GET /api/healthz` | `200 {"status":"ok"}` |
| any `GET` under `/api/` | JSON `404`, never the SPA |
| `GET` matching a file under `WAKE_STATIC_DIR` | that file |
| any other `GET` | `index.html`, so client-side routes deep-link |
| `POST /api/v1/wake` | signed wake, `204` on success |
| `POST /api/v1/enroll` | single-use token exchange |

`/healthz` is deliberately outside `/api` so container healthchecks and uptime monitors
do not need to know about the prefix.

**If the root URL returns a JSON 404, `WAKE_STATIC_DIR` is unset** or points at a
directory with no `index.html`.

## Configuration

| Setting | Default | Purpose |
|---|---|---|
| `WAKE_DATA_DIR` | `/data` | Persistent config and generated key directory |
| `WAKE_KEY_FILE` | `/data/wake.key` | Signing-key path |
| `WAKE_CONFIG_FILE` | `/data/targets.json` | Target list |
| `WAKE_TARGETS_JSON` | empty | Inline target list; takes precedence over the file |
| `WAKE_KEY_ID` | `main` | Client key identifier, sent as `X-Key-Id` |
| `WAKE_SERVER_URL` | `http://localhost:8080` | Origin embedded in enrollment links |
| `WAKE_STATIC_DIR` | empty | Directory holding the built PWA; empty disables static serving |
| `WAKE_BIND` / `WAKE_PORT` | `0.0.0.0` / `8080` | Listener |
| `WAKE_ALLOW_INSECURE_ENROLLMENT` | `false` | Explicit LAN HTTP opt-in |
| `WAKE_ALLOWED_ORIGINS` | `*` | Comma-separated CORS origins; restrict in production |
| `WAKE_NOTIFY_WEBHOOK_URL` | empty | Generic JSON webhook |
| `WAKE_NOTIFY_TELEGRAM_TOKEN` / `WAKE_NOTIFY_TELEGRAM_CHAT_ID` | empty | Telegram notification |
| `WAKE_NOTIFY_TELEGRAM_PARSE_MODE` | empty | `Markdown` or `HTML` for Telegram |
| `WAKE_NOTIFY_NTFY_URL` | empty | ntfy topic URL |
| `WAKE_NOTIFY_TEMPLATE` | `Wake Remote: {event} ({target})` | Notification text; `{event}`, `{target}`, `{label}` |
| `WAKE_NOTIFY_AUTH_FAILURES` | `false` | Alert on failed authentication |

Target keys are `alias`, `label`, `address`, `mac`, and optional `port` (default 9).

## Operating notes

Run **one replica only**: nonce replay protection and rate limiting are in-memory, so a
second replica would accept a replayed request. Mount `/data` persistently. Keep the
non-root user, read-only root filesystem, dropped capabilities and no-new-privileges
settings; static files are mounted read-only so the container cannot rewrite what it
serves. Do not expose plaintext enrollment to the internet.

Rotate the key by overwriting a bind-mounted `wake.key` **in place** with `cp`; replacing
the inode can leave a running container on the old key until it is recreated.
