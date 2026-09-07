from __future__ import annotations

import json
import os
import re
import secrets
from dataclasses import dataclass
from pathlib import Path

HEX_KEY = re.compile(r"^[0-9a-f]{64,}$")


@dataclass(frozen=True)
class Target:
    alias: str
    label: str
    address: str
    mac: str
    port: int = 9


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    key_id: str
    secret: bytes
    server_url: str
    targets: dict[str, Target]
    bind: str
    port: int
    allow_insecure_enrollment: bool
    notify_webhook_url: str
    notify_telegram_token: str
    notify_telegram_chat_id: str
    notify_ntfy_url: str
    notify_auth_failures: bool
    allowed_origins: tuple[str, ...]
    notify_template: str = "Wake Remote: {event} ({target})"
    notify_telegram_parse_mode: str = ""


def _write_private(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o400)
    with os.fdopen(fd, "w", encoding="ascii") as handle:
        handle.write(value + "\n")


def load_settings() -> tuple[Settings, bool]:
    data_dir = Path(os.environ.get("WAKE_DATA_DIR", "/data"))
    key_file = Path(os.environ.get("WAKE_KEY_FILE", str(data_dir / "wake.key")))
    generated = False
    if not key_file.exists():
        _write_private(key_file, secrets.token_hex(32))
        generated = True
    key_hex = key_file.read_text(encoding="ascii").strip().lower()
    if not HEX_KEY.fullmatch(key_hex):
        raise SystemExit("FATAL: signing key must be lowercase hex and at least 256 bits")

    targets_json = os.environ.get("WAKE_TARGETS_JSON", "")
    if targets_json:
        raw = json.loads(targets_json)
    else:
        config_file = Path(os.environ.get("WAKE_CONFIG_FILE", str(data_dir / "targets.json")))
        if not config_file.exists():
            example = {"targets": [{"alias": "main-pc", "label": "Main PC", "address": "192.168.1.255", "mac": "00:11:22:33:44:55", "port": 9}]}
            config_file.parent.mkdir(parents=True, exist_ok=True)
            config_file.write_text(json.dumps(example, indent=2) + "\n", encoding="utf-8")
            try:
                os.chmod(config_file, 0o600)
            except OSError:
                pass
        raw = json.loads(config_file.read_text(encoding="utf-8"))
    targets: dict[str, Target] = {}
    for item in raw.get("targets", []):
        alias = str(item["alias"])
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,62}", alias):
            raise SystemExit(f"FATAL: invalid target alias: {alias}")
        mac = str(item["mac"]).lower()
        if not re.fullmatch(r"(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", mac):
            raise SystemExit(f"FATAL: invalid MAC for {alias}")
        targets[alias] = Target(alias, str(item.get("label", alias)), str(item["address"]), mac, int(item.get("port", 9)))
    if not targets:
        raise SystemExit("FATAL: configure at least one target")

    return Settings(
        data_dir=data_dir, key_id=os.environ.get("WAKE_KEY_ID", "main"), secret=bytes.fromhex(key_hex),
        server_url=os.environ.get("WAKE_SERVER_URL", "http://localhost:8080").rstrip("/"), targets=targets,
        bind=os.environ.get("WAKE_BIND", "0.0.0.0"), port=int(os.environ.get("WAKE_PORT", "8080")),
        allow_insecure_enrollment=os.environ.get("WAKE_ALLOW_INSECURE_ENROLLMENT", "false").lower() == "true",
        notify_webhook_url=os.environ.get("WAKE_NOTIFY_WEBHOOK_URL", ""),
        notify_telegram_token=os.environ.get("WAKE_NOTIFY_TELEGRAM_TOKEN", ""),
        notify_telegram_chat_id=os.environ.get("WAKE_NOTIFY_TELEGRAM_CHAT_ID", ""),
        notify_ntfy_url=os.environ.get("WAKE_NOTIFY_NTFY_URL", ""),
        notify_auth_failures=os.environ.get("WAKE_NOTIFY_AUTH_FAILURES", "false").lower() == "true",
        allowed_origins=tuple(x.strip() for x in os.environ.get("WAKE_ALLOWED_ORIGINS", "*").split(",") if x.strip()),
        notify_template=os.environ.get("WAKE_NOTIFY_TEMPLATE", "Wake Remote: {event} ({target})"),
        notify_telegram_parse_mode=os.environ.get("WAKE_NOTIFY_TELEGRAM_PARSE_MODE", ""),
    ), generated
