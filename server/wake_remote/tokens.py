"""Persistent, multi-device enrollment token store.

Enrollment tokens are shared between the long-running server process and the
``--enroll`` CLI, which runs as a separate process. An in-memory dict cannot
satisfy that: a token minted by the CLI would never be visible to the server.

This store persists tokens to a JSON file in the data directory and guards every
read-modify-write with an advisory file lock (``fcntl.flock``) so concurrent
processes cannot clobber each other. Multiple device tokens coexist in the file;
minting one never removes another. Expired tokens are purged on every operation.
"""

from __future__ import annotations

import json
import os
import secrets
import threading
import time
import urllib.parse
from pathlib import Path

try:
    import fcntl  # POSIX only; the server targets Linux containers.
except ImportError:  # pragma: no cover - Windows bare-metal fallback
    fcntl = None


class TokenStore:
    def __init__(self, path: Path):
        self.path = path
        self._thread_lock = threading.Lock()
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def _read(self, handle) -> dict[str, float]:
        handle.seek(0)
        raw = handle.read().strip()
        if not raw:
            return {}
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            return {}
        now = time.time()
        return {t: float(exp) for t, exp in data.items() if float(exp) > now}

    def _write(self, handle, tokens: dict[str, float]) -> None:
        handle.seek(0)
        handle.truncate()
        handle.write(json.dumps(tokens))
        handle.flush()
        os.fsync(handle.fileno())

    def _open(self):
        # Open (creating if needed) without truncating; 0600 on creation.
        fd = os.open(self.path, os.O_RDWR | os.O_CREAT, 0o600)
        return os.fdopen(fd, "r+", encoding="ascii")

    def mint(self, ttl: int = 600) -> str:
        token = secrets.token_urlsafe(32)
        with self._thread_lock, self._open() as handle:
            if fcntl:
                fcntl.flock(handle, fcntl.LOCK_EX)
            tokens = self._read(handle)
            tokens[token] = time.time() + ttl
            self._write(handle, tokens)
        return token

    def consume(self, token: str) -> bool:
        if not token:
            return False
        with self._thread_lock, self._open() as handle:
            if fcntl:
                fcntl.flock(handle, fcntl.LOCK_EX)
            tokens = self._read(handle)
            present = tokens.pop(token, None) is not None
            self._write(handle, tokens)
        return present


def enrollment_uri(server_url: str, token: str) -> str:
    query = urllib.parse.urlencode({"v": "1", "url": server_url, "t": token})
    return "wakeremote://enroll?" + query
