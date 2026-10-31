# Tablet setup for the Cadillac CTS Dashboard

This folder turns the Snook C70R (Android 5.1, MediaTek MT6572, 1 GB RAM) into a dashboard appliance.
The dashboard app itself already does the app-side work: it can be chosen as the Home app, it opens
when the tablet finishes booting, and it hides the Android bars while it runs.

## What the app does by itself (no scripts)

- **Home app:** the dashboard registers as a Home app. Press Home on the tablet and choose
  *Cadillac CTS Dashboard* > *Always*; from then on Home always opens it.
- **Starts at boot:** a boot receiver opens the dashboard as soon as Android finishes booting.
- **Own splash:** the Cadillac animation plays when the dashboard opens.

## What needs the scripts (system settings, run by you)

`kiosk-setup.ps1` changes system settings and hides apps. It is not run automatically because it
touches system and lock-screen settings; read it, then run it yourself:

```powershell
# 1. basic clean-up, then reboot and check Wi-Fi, Bluetooth, hotspot and the dashboard
.\tablet-setup\kiosk-setup.ps1 -Adb ..\tools\platform-tools\adb.exe
# 2. only if step 1 is fine: also hide Google Play services and a few vendor packages
.\tablet-setup\kiosk-setup.ps1 -Adb ..\tools\platform-tools\adb.exe -Aggressive
# undo everything it did
.\tablet-setup\kiosk-undo.ps1 -Adb ..\tools\platform-tools\adb.exe
```

| Goal | What the script does | Limit |
| --- | --- | --- |
| Status and bottom bar hidden by default, still usable | `policy_control = immersive.full=*`; swipe from the edge reveals them | They are hidden, not removed |
| No notifications | Hides the apps that post them, turns off heads-up and lock-screen notifications | The pull-down shade still exists when you swipe |
| No lock screen | `lockscreen.disabled = 1` and no lock-screen notifications | **This firmware ignores the setting**: after sleep the clock and status bar still appear. Set Settings > Security > Screen lock > None yourself (and raise the screen timeout) |
| No home screen needed | You choose the dashboard as Home (see above) | Launcher3 stays installed as a fallback |
| Debloat | `pm hide` for about 60 apps and services you do not need | Nothing is deleted; hidden apps can be restored |

## Boot animation

`bootanimation/bootanimation.zip` is a Cadillac boot animation built from the splash GIF in the
tablet's own format (600 x 1024, stored zip, plays once then holds the last frame). It is **not
installed**: `/system/media/bootanimation.zip` is read-only and root-owned, and this tablet is not
rooted. Installing it needs root or a custom ROM (for example `adb root` / a remount, then copy the
file over the stock one). The stock file is saved as `diplay-test/bootanimation-original` on the
development PC. Rebuild it with `python tablet-setup/make_bootanimation.py`.

## Risks to know about

- **There is no full firmware backup** of this tablet (the MediaTek read-back never worked), so a
  change that stops Android from booting cannot be fixed by restoring a backup. That is why the
  script only hides apps (reversible while Android still boots), keeps the core system packages and
  Launcher3, and splits the riskier packages behind `-Aggressive`.
- Hiding Google Play services (`-Aggressive`) can affect location and any Google-dependent app. The
  dashboard does not need it.
- If the tablet no longer boots after a change, boot to the stock recovery from the boot menu and
  use *wipe cache partition* first; a factory reset is the last resort and erases the tablet.

## Startup time

- **App:** Settings > Startup animation: Full (about 5 s), Short (about 2 s) or Off (straight to the Connect button). The splash sheet is decoded on a background thread, so the window opens in under a second either way.
- **Android:** the biggest gains are fewer apps starting at boot (the script's hide lists) and system animations set to 0, which the script now also does. Set the dashboard as the Home app so it opens as soon as the system is ready, without waiting for the boot-completed broadcast.
