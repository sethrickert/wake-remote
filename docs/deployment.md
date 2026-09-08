# Deployment

Use one replica only. Mount `/data` persistently. For a reverse proxy, remove the published port and join its private network; preserve the non-root user, read-only root filesystem, dropped capabilities, and no-new-privileges setting. Caddy is the simplest TLS front end; NPM, Traefik, and nginx are supported. Do not expose plaintext enrollment to the internet.

Raspberry Pi uses the same multi-arch image (`linux/arm64`). Bare-metal service definitions should run as a dedicated unprivileged user and grant write access only to the data directory.

## Serving the PWA at the origin root

The server splits `/` from `/api` internally, so the reverse proxy keeps proxying the whole origin to the container and needs no path rules.

1. Copy the built PWA (`pwa/dist`, or the `dist/` folder from the release tarball) into `server/pwa-dist/`.
2. Set `WAKE_STATIC_DIR=/srv/pwa`.
3. Recreate the container.

Compose already bind-mounts `server/pwa-dist` at `/srv/pwa` **read-only**, so the container can never rewrite what it serves. Override the source with `WAKE_STATIC_SOURCE` if the build lives elsewhere. Leaving `WAKE_STATIC_DIR` unset is supported: the API still works and the root returns a JSON 404.

Routing, in order:

| Request | Result |
|---|---|
| `GET /healthz`, `GET /api/healthz` | `200 {"status":"ok"}` |
| any `GET` under `/api/` | JSON `404` — never falls through to the SPA |
| `GET` matching a real file under `WAKE_STATIC_DIR` | that file, with its own content type |
| any other `GET` | `index.html`, so client-side routes deep-link correctly |
| `POST /api/v1/wake`, `POST /api/v1/enroll` | the API |

The root `/healthz` is deliberately kept outside `/api` so container healthchecks, NPM, and uptime monitors do not need to know about the prefix. Static paths are resolved and checked for containment inside the static root, so `..` and its percent-encoded forms cannot escape.

If the root URL shows a JSON 404 instead of the app, `WAKE_STATIC_DIR` is unset or points at a directory with no `index.html`.
