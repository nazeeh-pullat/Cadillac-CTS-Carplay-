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

Settings has a Video section: resolution presets (640 � 384, 800 � 480 default, 1024 � 600 native, 1280 � 720 experimental) and frame rate (20, 24, 30 fps). Choices apply the next time CarPlay connects; touch is normalized, so every size maps correctly.

Theme: pure black surfaces with a thin line in the crest colours (red, gold, silver, blue) near the bottom of Home and the splash. A faint sheen drifts along it and the line only animates while visible. The splash plays the owner-supplied GIF once, pre-scaled 2x with Lanczos for sharpness, then fades into Home. The Connect button reads Connect with the Apple logo and shows a turning arc with Connecting while the hotspot and iPhone link come up. Changing resolution or frame rate offers an app restart so the next connection uses it. Home also carries a thin champagne clock, a small link-status pill, an icon-only Lighting button (top left) and the
settings gear (top right). The Lighting page tints the supplied line art with the chosen colour, and pages ease in with
a short fade and slide. After the screen timeout Home fades to the centred logo (1.45x) under a 98 % black overlay.
The logos are the supplied high-resolution PNGs; only the splash still plays the GIF.

While CarPlay is connected or connecting, a small red icon-only box beside the Return button disconnects it. Settings > Startup animation chooses Full, Short or Off for the next launch.
