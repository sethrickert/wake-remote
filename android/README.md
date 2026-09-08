# WakeRemote for Android

WakeRemote is a focused Android client for the Apex Wake-on-LAN service. The everyday screen has one action and no scroll container; it rearranges into a two-panel layout in landscape, on tablets, and in wide multi-window sizes.

## Install and enroll

1. Copy `WakeRemote.apk` to an Android 6.0 or newer device and open it.
2. If Android asks, allow the file-opening app to install unknown apps.
3. On the one-time **Secure enrollment** screen, paste the 64-character hexadecimal HMAC key created during the VPS setup.
4. Tap **Save secure key**. The key is imported into Android Keystore and cannot be read back through the app.
5. On the main screen, tap **Wake main PC**.

The endpoint and target are intentionally fixed:

- `POST https://wol.apextechlabs.com/api/v1/wake`
- `{"target":"main-pc"}`

Open **Setup** to replace or remove the installed key. WakeRemote never downloads a key from the server and does not contain one in its source code or APK.

## What a result means

- **Wake signal sent** means the server accepted the authenticated request and sent the Wake-on-LAN packet. It does not prove that the PC is online yet.
- **Authentication failed** means the installed key, device time, or server key ID does not match.
- **Please wait** is the server rate limit. The app prevents another tap until the cooldown expires.
- **Service could not reach your home network** points to the VPS-to-home WireGuard path or the listener configuration.

Each tap creates a new 128-bit random nonce and signs the exact request with HMAC-SHA256. Traffic uses certificate-validated HTTPS only; cleartext networking is disabled.

## Build from Android Studio

Open this folder, allow Gradle to sync, select the `app` run configuration, and press **Run**. The project uses Java 17, Android Gradle Plugin 8.4.1, compile/target SDK 34, and minimum SDK 23.

For a terminal build with Android Studio's bundled JDK 17:

```powershell
./gradlew.bat test lint assembleDebug
```

The generated debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs use a development signing key and are appropriate for personal testing. Use a private release signing key before wider distribution.

## Acceptance checks

- Enroll the real key and send once on home Wi-Fi.
- Send once over cellular with Wi-Fi disabled.
- Temporarily enroll an incorrect key and confirm that authentication fails.
- Tap rapidly and confirm that the server rate limit produces a visible cooldown.
- Turn on airplane mode and confirm that the app reports a connection problem without crashing.
- Verify the final wake behavior while `main-pc` is actually asleep; the backend handoff noted that this physical test was still pending.
