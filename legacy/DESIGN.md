# Tablet connection screen

UI review follows [Unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky
([@heydamir](https://x.com/heydamir)), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The guidance is adapted to native Android views and this Android 5.1 tablet.

Home is named Cadillac CTS Dashboard. It contains the centered crest and script, one champagne Connect to iPhone button and a Settings gear at the top right. Connect uses the
saved paired iPhone, Bluetooth, and the tablet's own WPA2 hotspot (SSID Cadillac CTS) by default. Mode
selection and password dialogs are kept out of this default flow. Settings holds
pairing, wired/alternative wireless modes, hotspot password, status, and Stop.

The hotspot worker disables an existing AP, waits for Android to report it fully
off, then starts the configured AP and waits for readiness. Stop cancels pending
startup so it cannot turn the hotspot on after cancellation. Blocking radio waits
run on a worker thread, never the UI thread.

Cadillac's crest and script wordmark are bundled as small native vectors; sources
and conversion details are in [design-assets/SOURCES.md](design-assets/SOURCES.md).
The launcher icon uses the crest on black. A short crest/script startup splash
holds for 650ms and fades for 180ms. Settings fades in over 140ms; button feedback
uses Android's native ripple. No additional fonts, UI libraries, looping animation,
or online asset fetching is needed.

Spacing uses a shared dp scale; touch targets grow with text. Settings scrolls on
compact screens and at larger font sizes.

The video surface always fills the window. The connection dashboard is an overlay
that disappears on session activation and returns after disconnect or failure.
Android Back reveals Settings during a session without disconnecting; Return to
CarPlay hides it again. Sticky immersive mode hides both Android bars and is
reapplied after window focus returns. Android permits a temporary edge swipe to
reveal its navigation controls.

Settings has a Video section: resolution presets (640 × 384, 800 × 480 default, 1024 × 600 native, 1280 × 720 experimental) and frame rate (20, 24, 30 fps). Choices apply the next time CarPlay connects; touch is normalized, so every size maps correctly.
