# Wake Remote 3.0 — Claude Build, Installation, and Test Handoff

## Mission

Reproduce every build from `main`, install the native clients, deploy a fresh server, and execute the automated and live acceptance matrix. Do not weaken the protocol or put credentials in source control. A `204` means the server transmitted a Wake-on-LAN packet; it does not prove the target booted.

## Repository map

- `server/` — Python 3.13 service, Docker/Compose configuration, tests, and terminal QR support.
- `android/` — native Java app with Android Keystore and QR enrollment.
- `windows/` — .NET 8 WPF app with DPAPI, tray action, login option, hotkey, and Inno Setup.
- `pwa/` — React/Vite PWA using Web Crypto and IndexedDB.
- `shared/test-vectors.json` — authoritative byte-for-byte signing fixture.
- `.github/workflows/` — pull-request CI and tagged-release publication.

## Protocol invariants

The compact UTF-8 body is `{"target":"<alias>"}`. Join `POST`, `/v1/wake`, Unix timestamp seconds, fresh lowercase nonce hex, and lowercase SHA-256 body hex with LF characters and no trailing LF. Compute lowercase HMAC-SHA256 hex using the raw 32-byte key represented by the 64-character enrollment value. Preserve `X-Key-Id`, `X-Timestamp`, `X-Nonce`, and `X-Signature` exactly.

Authenticate before applying the legitimate-key limiter. Nonces are single-use; never retry an already signed request. Clients reference aliases only. MAC addresses and destinations remain server-defined.

## Required tools

Install only when missing:

- Git and Docker Engine/Desktop with Compose v2
- Python 3.13+ (`Python.Python.3.13` in winget)
- JDK 17, Android SDK 34/platform-tools, and preferably Android Studio
- .NET 8 SDK and Windows Desktop runtime
- Inno Setup 6 (`JRSoftware.InnoSetup` in winget)
- Node.js 22+, npm, and a Chromium browser

Do not paste or log secrets. Keep keystores, passwords, signing keys, and generated server data outside Git.

## Reproduce all builds

Run from the repository root.

### Server

```powershell
$env:PYTHONPATH = "$PWD\server"
py -3.13 -m unittest discover -s server\tests
docker build -t wake-remote-server:3.0.0 server
docker compose -f server\docker-compose.yml config
```

Expected: four tests pass. When Buildx is available, additionally build `linux/amd64` and `linux/arm64`.

### Android

```powershell
cd android
.\gradlew.bat test lint assembleDebug assembleRelease --no-daemon
```

Outputs are `app/build/outputs/apk/debug/app-debug.apk` and, without signing variables, `app/build/outputs/apk/release/app-release-unsigned.apk`. Production signing uses `WAKE_STORE_FILE`, `WAKE_STORE_PASSWORD`, `WAKE_KEY_ALIAS`, and `WAKE_KEY_PASSWORD` in the local process or GitHub secrets.

### Windows

```powershell
cd windows
dotnet restore WakeRemote.Tests\WakeRemote.Tests.csproj --configfile NuGet.Config
dotnet test WakeRemote.Tests\WakeRemote.Tests.csproj -c Release --no-restore
dotnet publish WakeRemote\WakeRemote.csproj -c Release --no-restore
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" installer.iss
```

Outputs are the self-contained portable executable under `WakeRemote/bin/Release/net8.0-windows/win-x64/publish/` and `artifacts/WakeRemote-Setup-3.0.0-x64.exe`. Verify FileVersion/ProductVersion 3.0.0, `Assets/app.ico` on the app, and `Assets/setup.ico` on the installer.

### PWA

```powershell
cd pwa
npm ci
npm test
npm run lint
npm run build
```

Expected output is `pwa/dist/`, including `manifest.webmanifest`, `sw.js`, hashed CSS/JS, and both icon sizes. Serve it over HTTPS for installation and camera access.

## Deploy a fresh server

1. Set `WAKE_SERVER_URL` to the externally reachable HTTPS origin.
2. Start with a new `wake-data` named volume and set `WAKE_TARGETS_JSON` to the real targets.
3. Run `docker compose up -d --build` from `server/`.
4. Confirm first run creates `wake.key`, prints a 10-minute enrollment URI, and renders its QR in logs.
5. Replace the sample target JSON with the real alias, label, MAC, destination, and UDP port; restart.
6. LAN-resident mode uses a subnet broadcast address. Remote+tunnel mode uses unicast and requires a static router IP/MAC binding.
7. Put HTTPS in front of port 8080. Restrict `WAKE_ALLOWED_ORIGINS` to the production PWA origin. Keep exactly one replica.

Verify UID/GID 10001, read-only rootfs, all capabilities dropped, no-new-privileges, and no public host port when connected directly to a reverse proxy network.

## Install clients

### Android

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.apextechlabs.wakeremote
adb shell monkey -p com.apextechlabs.wakeremote -c android.intent.category.LAUNCHER 1
```

Use a production-signed APK for distribution. Scan the server QR. Camera permission must appear only after selecting Scan. Deny it once and confirm manual enrollment remains usable.

### Windows

Run `WakeRemote-Setup-3.0.0-x64.exe`, or launch the portable executable. Enrollment metadata may be ordinary JSON, but the key must be DPAPI-protected for the current user. Verify window/taskbar icon, tray Wake action, login option, and `Ctrl+Alt+W` hotkey.

### PWA

Serve `pwa/dist` over HTTPS, open it in Chromium, and install it. QR scanning depends on camera and `BarcodeDetector`; pasting the enrollment link must remain available. Browser storage is less isolated than Android Keystore or DPAPI, so prefer native clients for higher-risk endpoints.

## Automated API matrix

| Check | Expected |
|---|---|
| `GET /healthz` | `200 {"status":"ok"}` |
| `HEAD /healthz` | `200` |
| Unsigned or bad signature | `401` |
| Valid signed wake | `204` and three 102-byte UDP packets |
| Replay same signed request | `401` |
| Unknown target | `404` |
| Sixth rapid authenticated request | `429` with `Retry-After` |
| Forced UDP failure | `503` |
| Oversized body | `413` |
| First valid enrollment exchange | `200` with key, targets, and origin |
| Reused or expired enrollment token | `401` |
| Allowed-origin CORS preflight | `204` with required headers |

Exercise webhook, Telegram, and ntfy notifications. Slow or failing notification endpoints must not delay `204`. Logs may contain method, path, and status only—never bodies, headers, signatures, tokens, or keys.

## Cross-client and hardware matrix

Run each client against the same fresh server:

1. QR enrollment succeeds and returns all targets.
2. Manual 64-hex enrollment succeeds; 24-character and non-hex input fail locally.
3. One target hides the selector; multiple targets show it.
4. Valid wake says “Wake signal sent,” never “PC is on.”
5. Wrong key displays authentication failure.
6. Rapid fresh requests display the rate-limit state.
7. Offline/airplane mode displays a network error without crashing.
8. Removing enrollment requires confirmation and removes protected local credentials.
9. Default and largest accessibility font sizes have no clipping or overflow.
10. Android QR denial, Windows tray/hotkey, and PWA camera fallback all degrade cleanly.

Finally, sleep the physical target, disable Android Wi-Fi, send over cellular, and confirm it genuinely wakes. If `204` and packets are observed but the target remains asleep, inspect BIOS/NIC settings and static ARP/IP-MAC binding.

## Release publication

Configure these GitHub secrets before tagging:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Create `v3.0.0` only after live acceptance passes. Watch `.github/workflows/release.yml`, then download the exact GitHub Release assets and independently verify hashes, APK signature/version, Windows version metadata, both icons, clean installation, and cold launch. A merged commit or green source build is not proof that downloadable assets are correct.

## Verified baseline and remaining live checks

- Server: four integration tests passed.
- Android: unit tests, lint, debug and minified release builds passed; debug APK installed and cold-launched on `Pixel_2_API_30` without a crash.
- Windows: protocol-fixture test passed; self-contained publish and Inno Setup compilation succeeded.
- PWA: fixture test, ESLint, production build, service worker, desktop rendering, enrollment navigation, and 390×844 responsive rendering passed without console errors.
- Fixture signature: `e40a07dab22ebff23eeaa158aa9f861918ba70007c7b85ba6acf5aac48823a38`.

Environment-dependent checks still requiring evidence: production Android signing, pushed multi-architecture image, real TLS deployment, configured notification destinations, camera hardware, cellular network, Windows tray/hotkey on the recipient machine, and a genuinely sleeping target.
