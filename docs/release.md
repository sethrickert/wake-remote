# Release verification

Tagged CI builds the signed Android APK, self-contained Windows portable EXE and installer, PWA archive, and multi-architecture server image. Before publishing, verify signatures, version metadata, installer/app icons, SHA-256 hashes, clean installation, cold launch, enrollment, a real sleep-state wake over cellular, and all response states. Repository signing secrets are never available to pull requests.

## Required GitHub Actions secrets

Create and securely back up a dedicated Android upload keystore, then configure these repository secrets before pushing a production tag:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded binary contents of the keystore.
- `ANDROID_STORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing-key alias.
- `ANDROID_KEY_PASSWORD`: signing-key password.

Losing the keystore or its passwords prevents future upgrades from being signed as the same Android application. Keep an encrypted backup outside GitHub and outside this repository.

## Publishing

Confirm `main` is green and all versions agree, then create and push an annotated tag such as `v3.0.0`. The release workflow publishes the client artifacts to GitHub Releases and pushes both `linux/amd64` and `linux/arm64` server images to GHCR.

Do not declare the release complete from CI alone. Download the published assets and verify their hashes, Android signature/version, Windows file and installer versions/icons, clean installs, cold launches, enrollment, and a real wake from outside the target LAN.
