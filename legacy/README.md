# Experimental Android 5.1 tablet host

This module reuses DiPlay's CarPlay engine with a small platform Android UI for
32-bit Android 5.1 tablets. It is a development backport, not a supported DiPlay
release. App launch alone does not establish successful CarPlay operation.

The Cadillac CTS Dashboard layer (one-tap Connect over the tablet hotspot, auto-connect and retries, LED lighting page, screen
timeout) is described in the repository [README](../README.md) and [HANDOFF](../HANDOFF.md). The notes below are the original
engine-port scope and are partly superseded by it.

## Scope

- Wired USB and experimental wireless over the existing Wi-Fi LAN, with explicit Start and Stop controls.
- 800 × 480 H.264 video at 30 fps; touch and downlink audio.
- Microphone, Wi-Fi Direct, dashboard integration, and auto-start are not enabled.
- Pairing state stays in the app's private storage; Android backup is disabled.
- The package ID is `com.shihab.diplay.legacy`, separate from the original app.

An iPhone and a data-capable USB OTG adapter are needed. The tablet must be the
USB host. Accept the tablet's USB access and local connection/VPN prompts and
the iPhone's Trust prompt. A USB hub may be needed for simultaneous charging;
charging behavior depends on the tablet and adapter.

For wireless, connect both devices to the same Wi-Fi and pair the iPhone in Android
Bluetooth settings. Tap **Start wireless**, select the paired iPhone, and enter
the current network's password (leave it blank only for an open network). The
app does not create a hotspot or change the tablet's Wi-Fi connection. Router
client isolation can prevent devices on the same SSID from communicating.
Wi-Fi credentials are stored only in the app's private preferences and are not
written to diagnostics. The **Pair iPhone** button opens Android Bluetooth settings.

## Build

Use the JDK and Android SDK versions specified by the repository CI workflow.
Set `ANDROID_HOME` (or `local.properties`), then run:

```text
gradlew.bat -PlegacyAndroid51=true :legacy:assembleDebug :legacy:testDebugUnitTest
```

The property lowers the shared engine and native code to API 22, builds the ARMv7
native ABI, excludes the unused Android Auto UI, and enables Java library
desugaring. Without this property, the existing shared engine keeps its original
API 24 minimum and its original Android Auto dependency.

As with upstream, source-only builds contain no accessory authentication assets.
For a local standalone test, `DIPLAY_AUTH_ASSETS_DIR` may point to an external
directory containing the explicitly provisioned `offline-mfi/identity.pk8` and
`offline-mfi/certificate.p7b` files. Do not commit those files, signing keys, or
device pairing records. The original project notices and experimental accessory
identity limitations still apply.

## Compatibility changes

The engine uses string-based system-service lookup on API 22 and preserves the
class-based lookup on API 23+. Audio playback uses the API 21 attributes
constructor on Lollipop, its blocking three-argument write method, and the
configured buffer capacity where newer AudioTrack introspection is unavailable.
Newer routing callbacks remain gated to their supported Android versions.
Existing-LAN wireless on API 22 uses Android's system NSD responder, avoiding
the multicast-port collision observed with separate JmDNS listeners on this tablet.

## Validation

Wireless authentication, Bluetooth startup, Wi-Fi handoff, live Maps video, and
touch were verified on a Snook C70R running Android 5.1 with the owner's paired
iPhone. The observed renderer rate was approximately 17–22 fps and app PSS was
approximately 31 MB during that session. These observations do not establish
compatibility with other tablets or long-session reliability. Music/call audio
has not been verified, and microphone support is disabled.

See the local build/test logs and `diplay-test/WIRELESS-VALIDATION.txt` beside
the checkout for the detailed validation record.
