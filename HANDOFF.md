# Handoff: Cadillac CTS Dashboard

State at 2026-11-01. Everything in this repository is committed and pushed to `cadillac/main`
(`https://github.com/nazeeh-pullat/Cadillac-CTS-Carplay-`). `origin` still points at the upstream DiPlay.

## What exists

| Area | Where | Notes |
| --- | --- | --- |
| Android 5.1 app | `legacy/` (package `com.shihab.diplay.legacy`, label "Cadillac CTS Dashboard") | `LegacyActivity.kt` holds the whole UI and the connection flow |
| Engine shims for API 22 | `shared/.../compat/`, `WirelessMdnsCompatibility.kt` | service lookup, AudioTrack, platform NSD instead of JmDNS |
| Splash | `SplashAnimationView.kt`, `SplashFrames.kt` (generated), `res/drawable-nodpi/splash_sheet.png` | rebuild with `legacy/design-assets/build_splash_sheet.py` from `splash-source.gif` |
| Settings | `VideoModes.kt` (resolution, frame rate), `StartupModes.kt` (Full/Short/Off) | prefs file `legacy` |
| Theme | `CadillacBackground.kt`, `CadillacLineView.kt`, `ConnectLabel.kt` | black, crest-colour line, Apple logo in the button |
| Tablet appliance | `tablet-setup/` | kiosk and undo scripts, boot animation zip, README |
| Docs | `legacy/README.md`, `legacy/DESIGN.md`, `docs/cts/*.png` | |

## Build and test

```powershell
$env:JAVA_HOME='<repo-parent>\tools\jdk25\jdk-25.0.5+8'; $env:ANDROID_HOME='<repo-parent>\tools\android-sdk'
$env:GRADLE_USER_HOME='<repo-parent>\tools\gradle-cache'; $env:DIPLAY_AUTH_ASSETS_DIR='<repo-parent>\diplay-test\runtime-auth'
.\gradlew.bat --no-daemon -PlegacyAndroid51=true :legacy:assembleDebug :legacy:assembleDebugAndroidTest :legacy:testDebugUnitTest :legacy:lintDebug
```

Before calling a change done, also run the repository CI command from `AGENTS.md` (with `DIPLAY_AUTH_ASSETS_DIR` unset),
`python scripts/check_public_tree.py` and `git diff --check`. Last results: 25 legacy tests pass, lint clean, repo CI passes.
`adb install -r legacy\build\outputs\apk\debug\legacy-debug.apk` installs it. Toolchain and logs live next to the repo
in `Android\tools` and `Android\diplay-test` (not in git).

## Never commit

- `diplay-test/runtime-auth/offline-mfi/identity.pk8` and `certificate.p7b`: the accessory identity used for local
  authentication. `.gitignore` and `scripts/check_public_tree.py` block `.pk8`, `.p7b`, keys and APKs.
- `tablet-setup/kiosk-state.json`: a per-device backup that the undo script needs (it is git-ignored).

## Tablet (Snook C70R)

Android 5.1, MT6572/MT8312C, 1 GB RAM, 600 x 1024 portrait panel used in landscape, not rooted (`ro.secure=1`, no `su`).
ADB drops now and then; unplug and replug, unlock, accept the prompt. The kiosk script has been run on this tablet:
bars hidden (`policy_control=immersive.full=*`), lock screen off, system animations 0, 63 packages hidden. Undo with
`tablet-setup/kiosk-undo.ps1` (uses `kiosk-state.json`). Launcher3 is intentionally left as a fallback.
**No full firmware backup exists** (MediaTek read-back failed), so avoid risky system changes.

## LED ambient lights

The dashboard can drive a Bluetooth LE LED controller (the kind controlled by the iPhone Lotus Lantern app). Settings > LED strip scans for devices, saves one, tests colours and picks the controller family (ELK-BLEDOM 7E...EF or Triones/LEDBLE 56...AA; auto-detected from the writable GATT characteristic). Once a strip is saved, Home shows a text-free row of colour dots above the bottom line; with no strip saved Home stays clean. Code: `LedStrip.kt`, `LedScannerDialog.kt`. **Hardware control is untested**: the scan works, but no strip was in range during development, and controllers allow one connection at a time, so close the iPhone app first.

## Known issues and open items

- The firmware ignores `lockscreen.disabled`; the lock screen appears after sleep until Screen lock is set to None in Settings.


- Music and call audio are unverified; the microphone is disabled.
- If the app goes to the background during a session the video decoder logs "surface has been released" and retries
  until the app returns; it is benign but noisy.
- 60 fps is offered as experimental; the tablet manages about 24 fps at 800 x 480 / 30.
- The Cadillac boot animation is built but cannot be installed without root; rooting is out of scope here.
- Next ideas: auto-connect on open, verify audio, time Android's own boot, a short "keep screen on" option.
