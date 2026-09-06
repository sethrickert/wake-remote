# Deployment

Use one replica only. Mount `/data` persistently. For a reverse proxy, remove the published port and join its private network; preserve the non-root user, read-only root filesystem, dropped capabilities, and no-new-privileges setting. Caddy is the simplest TLS front end; NPM, Traefik, and nginx are supported. Do not expose plaintext enrollment to the internet.

Raspberry Pi uses the same multi-arch image (`linux/arm64`). Bare-metal service definitions should run as a dedicated unprivileged user and grant write access only to the data directory.
