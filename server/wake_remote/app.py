from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import logging
import mimetypes
import os
import socket
import sys
import threading
import time
import urllib.parse
import urllib.request
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .config import Settings, Target, load_settings
from .tokens import TokenStore, enrollment_uri

LOG = logging.getLogger("wake-remote")

API_PREFIX = "/api"
WAKE_PATH = f"{API_PREFIX}/v1/wake"
ENROLL_PATH = f"{API_PREFIX}/v1/enroll"
HEALTH_PATHS = ("/healthz", f"{API_PREFIX}/healthz")

# Explicit types rather than trusting the host's mimetypes registry, which on some
# platforms maps .js to text/plain and would break module scripts.
STATIC_TYPES = {
    ".css": "text/css; charset=utf-8",
    ".html": "text/html; charset=utf-8",
    ".ico": "image/x-icon",
    ".js": "text/javascript; charset=utf-8",
    ".json": "application/json",
    ".map": "application/json",
    ".mjs": "text/javascript; charset=utf-8",
    ".png": "image/png",
    ".svg": "image/svg+xml",
    ".txt": "text/plain; charset=utf-8",
    ".webmanifest": "application/manifest+json",
    ".webp": "image/webp",
    ".woff2": "font/woff2",
}


def resolve_static(root: str, url_path: str) -> str | None:
    """Map a URL path to a real file inside root, or None.

    Fails closed on traversal: the candidate is fully resolved (following symlinks)
    and must still be contained by the resolved root. Note urlsplit() does not decode
    percent-escapes, so %2e%2e%2f arrives encoded and must be unquoted before the
    containment check rather than after.
    """
    if not root:
        return None
    try:
        root_real = os.path.realpath(root)
        relative = urllib.parse.unquote(url_path).lstrip("/")
        if "\x00" in relative:
            return None
        candidate = os.path.realpath(os.path.join(root_real, relative))
    except (OSError, ValueError):
        return None
    if candidate != root_real and not candidate.startswith(root_real + os.sep):
        return None
    if os.path.isdir(candidate):
        candidate = os.path.join(candidate, "index.html")
    return candidate if os.path.isfile(candidate) else None


class FixedWindowLimiter:
    def __init__(self, count: int, seconds: int):
        self.count, self.seconds = count, seconds
        self.events: dict[str, deque[float]] = defaultdict(deque)
        self.lock = threading.Lock()

    def allow(self, key: str, now: float | None = None) -> tuple[bool, int]:
        now = time.time() if now is None else now
        with self.lock:
            events = self.events[key]
            while events and events[0] <= now - self.seconds:
                events.popleft()
            if len(events) >= self.count:
                return False, max(1, int(self.seconds - (now - events[0]) + .999))
            events.append(now)
            return True, 0


class WakeService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.nonces: dict[str, float] = {}
        self.nonce_lock = threading.Lock()
        self.authenticated = FixedWindowLimiter(5, 60)
        self.unauthenticated = FixedWindowLimiter(30, 60)
        self.enrollment = FixedWindowLimiter(10, 60)
        self.tokens = TokenStore(settings.data_dir / "enroll-tokens.json")

    def mint_token(self, ttl: int = 600) -> tuple[str, str]:
        token = self.tokens.mint(ttl)
        return token, enrollment_uri(self.settings.server_url, token)

    def consume_token(self, token: str) -> bool:
        return self.tokens.consume(token)

    def verify(self, headers, body: bytes) -> tuple[bool, str]:
        key_id = headers.get("X-Key-Id", "")
        timestamp = headers.get("X-Timestamp", "")
        nonce = headers.get("X-Nonce", "")
        signature = headers.get("X-Signature", "")
        if key_id != self.settings.key_id or not timestamp.isdigit() or not (32 <= len(nonce) <= 128):
            return False, key_id or "unknown"
        try:
            if abs(int(time.time()) - int(timestamp)) > 60 or bytes.fromhex(nonce).hex() != nonce:
                return False, key_id
        except ValueError:
            return False, key_id
        canonical = "\n".join(("POST", WAKE_PATH, timestamp, nonce, hashlib.sha256(body).hexdigest()))
        expected = hmac.new(self.settings.secret, canonical.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, signature.lower()):
            return False, key_id
        now = time.time()
        with self.nonce_lock:
            self.nonces = {n: expiry for n, expiry in self.nonces.items() if expiry > now}
            if nonce in self.nonces:
                return False, key_id
            self.nonces[nonce] = now + 120
        return True, key_id

    @staticmethod
    def send_magic(target: Target) -> None:
        mac = bytes.fromhex(target.mac.replace(":", ""))
        packet = b"\xff" * 6 + mac * 16
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            for _ in range(3):
                sock.sendto(packet, (target.address, target.port))

    def notify(self, event: str, target: str, label: str | None = None) -> None:
        threading.Thread(target=self._notify, args=(event, target, label or target), daemon=True).start()

    def _notify(self, event: str, target: str, label: str) -> None:
        message = (self.settings.notify_template
                   .replace("\\n", "\n")
                   .replace("{event}", event)
                   .replace("{label}", label)
                   .replace("{target}", target))
        requests: list[urllib.request.Request] = []
        if self.settings.notify_webhook_url:
            requests.append(urllib.request.Request(self.settings.notify_webhook_url, json.dumps({"event": event, "target": target}).encode(), {"Content-Type": "application/json"}))
        if self.settings.notify_ntfy_url:
            requests.append(urllib.request.Request(self.settings.notify_ntfy_url, message.encode(), {"Title": "Wake Remote"}))
        if self.settings.notify_telegram_token and self.settings.notify_telegram_chat_id:
            url = f"https://api.telegram.org/bot{self.settings.notify_telegram_token}/sendMessage"
            params = {"chat_id": self.settings.notify_telegram_chat_id, "text": message}
            if self.settings.notify_telegram_parse_mode:
                params["parse_mode"] = self.settings.notify_telegram_parse_mode
            data = urllib.parse.urlencode(params).encode()
            requests.append(urllib.request.Request(url, data))
        for request in requests:
            try:
                urllib.request.urlopen(request, timeout=4).close()
            except Exception:
                LOG.warning("notification delivery failed", extra={"method": "NOTIFY", "path": event, "status": 502})


def make_handler(service: WakeService):
    class Handler(BaseHTTPRequestHandler):
        server_version = "WakeRemote/3"

        def log_message(self, _format, *args):
            return

        def finish_status(self, code: int, payload: dict | None = None, headers: dict | None = None, include_body: bool = True):
            data = b"" if payload is None else json.dumps(payload, separators=(",", ":")).encode()
            self.send_response(code)
            if payload is not None:
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
            for name, value in (headers or {}).items():
                self.send_header(name, value)
            origin = self.headers.get("Origin", "")
            if "*" in service.settings.allowed_origins or origin in service.settings.allowed_origins:
                self.send_header("Access-Control-Allow-Origin", "*" if "*" in service.settings.allowed_origins else origin)
                self.send_header("Vary", "Origin")
            self.end_headers()
            if include_body and data:
                self.wfile.write(data)
            LOG.info("request", extra={"method": self.command, "path": urllib.parse.urlsplit(self.path).path, "status": code})

        def do_OPTIONS(self):
            self.finish_status(204, headers={"Access-Control-Allow-Methods": "POST, GET, HEAD, OPTIONS", "Access-Control-Allow-Headers": "Content-Type, X-Key-Id, X-Timestamp, X-Nonce, X-Signature", "Access-Control-Max-Age": "600"})

        def finish_file(self, path: str, cache: str, include_body: bool = True):
            try:
                with open(path, "rb") as handle:
                    data = handle.read()
            except OSError:
                return self.finish_status(404, {"error": "not_found"}, include_body=include_body)
            suffix = os.path.splitext(path)[1].lower()
            ctype = STATIC_TYPES.get(suffix) or mimetypes.guess_type(path)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", cache)
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            if include_body:
                self.wfile.write(data)
            LOG.info("request", extra={"method": self.command, "path": urllib.parse.urlsplit(self.path).path, "status": 200})

        def serve_get(self, include_body: bool = True):
            path = urllib.parse.urlsplit(self.path).path
            if path in HEALTH_PATHS:
                return self.finish_status(200, {"status": "ok"}, include_body=include_body)
            # Decided before the SPA fallback so a mistyped API call never silently
            # returns index.html to a client expecting JSON.
            if path == API_PREFIX or path.startswith(API_PREFIX + "/"):
                return self.finish_status(404, {"error": "not_found"}, include_body=include_body)
            root = service.settings.static_dir
            if not root:
                return self.finish_status(404, {"error": "not_found"}, include_body=include_body)
            target = resolve_static(root, path)
            if target is not None:
                # Vite emits content-hashed filenames under /assets, so those are immutable.
                immutable = path.startswith("/assets/") and os.path.basename(target) != "index.html"
                cache = "public, max-age=31536000, immutable" if immutable else "public, max-age=3600"
                if os.path.basename(target) == "index.html":
                    cache = "no-cache"
                return self.finish_file(target, cache, include_body)
            fallback = resolve_static(root, "/index.html")
            if fallback is None:
                return self.finish_status(404, {"error": "not_found"}, include_body=include_body)
            return self.finish_file(fallback, "no-cache", include_body)

        def do_HEAD(self):
            self.serve_get(include_body=False)

        def do_GET(self):
            self.serve_get(include_body=True)

        def do_POST(self):
            path = urllib.parse.urlsplit(self.path).path
            client = self.client_address[0]
            if path == ENROLL_PATH:
                allowed, retry = service.enrollment.allow(client)
                if not allowed:
                    return self.finish_status(429, {"error": "rate_limited"}, {"Retry-After": str(retry)})
                return self.handle_enroll()
            if path != WAKE_PATH:
                return self.finish_status(404, {"error": "not_found"})
            length = int(self.headers.get("Content-Length", "0") or "0")
            if length > 1024:
                return self.finish_status(413, {"error": "body_too_large"})
            body = self.rfile.read(length)
            valid, key_id = service.verify(self.headers, body)
            if not valid:
                allowed, retry = service.unauthenticated.allow(client)
                if service.settings.notify_auth_failures:
                    service.notify("authentication failed", client)
                return self.finish_status(401 if allowed else 429, {"error": "authentication_failed"}, {"Retry-After": str(retry)} if not allowed else None)
            allowed, retry = service.authenticated.allow(key_id)
            if not allowed:
                return self.finish_status(429, {"error": "rate_limited"}, {"Retry-After": str(retry)})
            try:
                request = json.loads(body)
                target = service.settings.targets.get(request.get("target"))
            except (json.JSONDecodeError, AttributeError):
                target = None
            if target is None:
                return self.finish_status(404, {"error": "target_not_found"})
            try:
                service.send_magic(target)
            except OSError:
                return self.finish_status(503, {"error": "network_unavailable"})
            service.notify("wake sent", target.alias, target.label)
            self.finish_status(204)

        def handle_enroll(self):
            if not service.settings.server_url.startswith("https://") and not service.settings.allow_insecure_enrollment:
                return self.finish_status(403, {"error": "tls_required"})
            length = int(self.headers.get("Content-Length", "0") or "0")
            if length > 1024:
                return self.finish_status(413, {"error": "body_too_large"})
            try:
                token = json.loads(self.rfile.read(length)).get("token", "")
            except (json.JSONDecodeError, AttributeError):
                token = ""
            if not service.consume_token(token):
                return self.finish_status(401, {"error": "invalid_enrollment_token"})
            self.finish_status(200, {"key_id": service.settings.key_id, "key": service.settings.secret.hex(), "targets": [{"alias": t.alias, "label": t.label} for t in service.settings.targets.values()], "server_url": service.settings.server_url})
    return Handler


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--enroll", action="store_true", help="mint and display a single-use enrollment URI")
    parser.add_argument("--ttl", type=int, default=600)
    args = parser.parse_args(argv)
    settings, generated = load_settings()
    service = WakeService(settings)
    if args.enroll:
        _, uri = service.mint_token(args.ttl)
        print(uri)
        return 0
    logging.basicConfig(level=logging.INFO, format='{"time":"%(asctime)s","method":"%(method)s","path":"%(path)s","status":%(status)s}')
    if generated:
        _, uri = service.mint_token()
        print("First-run enrollment URI (expires in 10 minutes):", uri, file=sys.stderr)
        try:
            import segno
            segno.make(uri, error="m").terminal(out=sys.stderr, compact=True)
        except ImportError:
            print("Install segno to render the enrollment QR in this terminal.", file=sys.stderr)
    ThreadingHTTPServer((settings.bind, settings.port), make_handler(service)).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
