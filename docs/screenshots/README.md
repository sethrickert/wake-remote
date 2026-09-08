# Screenshots

Every image here is captured from the **fixed** 1.0.2 builds. The pre-1.0.2 screenshots
were deleted rather than carried forward, because they documented the broken state.

| File | What it shows | Captured with |
|---|---|---|
| `android-home.png` | Home, not enrolled: red status dot, and no wake icon on the primary action | Pixel 2 API 30 emulator, signed release APK, `adb exec-out screencap` |
| `android-enroll.png` | Enrollment: QR primary, manual entry as a link, service/target from stored state | same |
| `windows.png` | The main window, launched from a folder containing only the exe | `CopyFromScreen` over the window rect |
| `windows-tray.png` | The tray icon in the Windows 11 notification overflow, 5x nearest-neighbour | located via UI Automation, then cropped |
| `pwa-mobile.png` | Mobile view at 430x932 | `node qa-screenshot.mjs`, Playwright + Edge, DPR 2 |
| `pwa-enroll.png` | PWA enrollment screen | same |
| `pwa-desktop.png` | Desktop view at 1440x900 | same |

## Regenerating

PWA, against the built app served at a root by the server itself:

```bash
cd pwa && npm run build
PYTHONPATH=../server WAKE_STATIC_DIR=$PWD/dist WAKE_PORT=8098 WAKE_DATA_DIR=/tmp/wake \
  python -m wake_remote.app &
node qa-screenshot.mjs http://127.0.0.1:8098/ ../docs/screenshots
```

Android, with an emulator or device attached:

```bash
./gradlew -p android assembleRelease
adb install -r android/app/build/outputs/apk/release/WakeRemote.apk
adb exec-out screencap -p > docs/screenshots/android-home.png
```

## TODO: still worth capturing on real hardware

Deliberately **not** committed, rather than committed misleadingly:

- **Android QR scanner, on a real device.** The flow is verified working on the
  emulator, but the emulator camera renders a synthetic colour-block test pattern that
  would read as a rendering fault in documentation. Capture it pointed at a real
  enrollment QR.
- **Android home in the enrolled/ready state**, showing the blue dot and the "Wake
  computer" action. Needs a real enrollment against a live server.
- **A wake in flight, and the "Wake signal sent" state**, on any client.
- **The Windows installer wizard.** It cannot be built on the dev machine (Inno Setup
  is not installed there); only CI produces it.
