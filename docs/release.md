# Release verification

Tagged CI builds the signed Android APK, self-contained Windows portable EXE and installer, PWA archive, and multi-architecture server image. Before publishing, verify signatures, version metadata, installer/app icons, SHA-256 hashes, clean installation, cold launch, enrollment, a real sleep-state wake over cellular, and all response states. Repository signing secrets are never available to pull requests.
