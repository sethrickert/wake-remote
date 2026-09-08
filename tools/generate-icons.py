#!/usr/bin/env python3
"""Regenerate every platform icon from the 512x512 masters in /images.

The masters are the single source of truth:

    images/icon.png         the app mark  -> launchers, windows, taskbar, tray, favicon
    images/setup-icon.png   the app mark with a download badge -> the installer ONLY
    images/apex-shield.png  the shield    -> footer attribution ONLY

Run this after changing any master, then commit the generated files:

    python -m venv .venv && .venv/Scripts/pip install Pillow
    .venv/Scripts/python tools/generate-icons.py

Pillow is a build-time tool for this script alone. It is deliberately NOT added to
server/requirements.txt: the server stays stdlib-only.
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "images"
BACKGROUND = (12, 12, 12, 255)  # #0C0C0C, the Wake Remote surface colour

written: list[str] = []


def load(name: str) -> Image.Image:
    path = IMAGES / name
    if not path.exists():
        sys.exit(f"missing master: {path}")
    return Image.open(path).convert("RGBA")


def save(image: Image.Image, path: Path, **kwargs) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".png":
        kwargs.setdefault("optimize", True)
    image.save(path, **kwargs)
    written.append(str(path.relative_to(ROOT)).replace("\\", "/"))


def fit(source: Image.Image, size: int, scale: float = 1.0) -> Image.Image:
    """Render source centred on a transparent size x size canvas at the given scale."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = max(1, round(size * scale))
    resized = source.resize((art, art), Image.LANCZOS)
    canvas.paste(resized, ((size - art) // 2, (size - art) // 2), resized)
    return canvas


def on_background(source: Image.Image, size: int, scale: float = 1.0) -> Image.Image:
    """Same as fit(), but flattened onto the opaque brand background."""
    canvas = Image.new("RGBA", (size, size), BACKGROUND)
    art = max(1, round(size * scale))
    resized = source.resize((art, art), Image.LANCZOS)
    canvas.paste(resized, ((size - art) // 2, (size - art) // 2), resized)
    return canvas


def main() -> int:
    icon = load("icon.png")
    setup = load("setup-icon.png")
    shield = load("apex-shield.png")

    # ---- Android -----------------------------------------------------------
    android = ROOT / "android/app/src/main/res"
    # Legacy launcher bitmaps. The mark is a circular badge, so the round variant
    # is drawn full-bleed and the square one gets a little breathing room.
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(fit(icon, size, 0.92), android / f"mipmap-{density}/ic_launcher.png")
        save(fit(icon, size, 1.0), android / f"mipmap-{density}/ic_launcher_round.png")

    # Adaptive foreground: 432x432 (108dp @ xxxhdpi). Only the inner 66% is
    # guaranteed visible after the launcher applies its mask, so the art is inset
    # to that safe zone rather than filling the canvas.
    save(fit(icon, 432, 0.66), android / "drawable-nodpi/ic_launcher_foreground.png")

    # In-app artwork. The old 1024px master was decoded in full to draw ~128dp.
    save(fit(icon, 384), android / "drawable-nodpi/wake_remote_logo.png")
    save(fit(shield, 96), android / "drawable-nodpi/apex_shield.png")

    # ---- Windows -----------------------------------------------------------
    win = ROOT / "windows/WakeRemote/Assets"
    ico_sizes = [(s, s) for s in (16, 20, 24, 32, 40, 48, 64, 128, 256)]
    # The previous .ico files held a single 256x256 entry at 4bpp/16 colours, so
    # Windows downscaled 16-colour art for the tray and taskbar. These are 32-bit
    # with every size Windows asks for.
    save(icon.resize((256, 256), Image.LANCZOS), win / "app.ico", sizes=ico_sizes)
    save(setup.resize((256, 256), Image.LANCZOS), win / "setup.ico", sizes=ico_sizes)
    save(icon, win / "icon.png")
    save(shield.resize((256, 256), Image.LANCZOS), win / "apex-shield.png")

    # ---- PWA ---------------------------------------------------------------
    pwa = ROOT / "pwa/public"
    save(fit(icon, 192), pwa / "icon-192.png")
    save(fit(icon, 512), pwa / "icon-512.png")
    # Maskable icons are cropped to a shape by the launcher; only the inner 80%
    # circle is safe, and the canvas must be opaque or Android letterboxes it.
    save(on_background(icon, 512, 0.72), pwa / "icon-maskable-512.png")
    # iOS ignores manifest icons for Add to Home Screen and does not composite
    # transparency, so this one is flattened too.
    save(on_background(icon, 180, 0.92), pwa / "apple-touch-icon.png")
    save(fit(icon, 512), pwa / "icon.webp", quality=88, method=6)
    save(fit(shield, 256), pwa / "apex-shield.webp", quality=88, method=6)
    save(icon.resize((256, 256), Image.LANCZOS), pwa / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])

    print(f"wrote {len(written)} files:")
    for name in written:
        print("  ", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
