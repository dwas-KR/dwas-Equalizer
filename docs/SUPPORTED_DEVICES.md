# Supported devices and validation status

This document summarizes the device compatibility state represented by dwas_EQ v0.4.0. Audio behavior can differ by ROM, Android version, vendor audio configuration and active playback route.

| Model | Current status | Important notes |
|---|---|---|
| TB320FC | Real-device validated | Equalizer, Headroom, Limiter, Balance, Bass Boost and Virtualizer validated. `TB320FU` may appear as an archive/device identity alias. |
| TB322FC | Real-device validated | Core EQ and standard effects validated. |
| TB323FU | Real-device validated | Core EQ and standard effects validated. |
| TB331FC | Reference capture | Android 13 / DAX 13 topology captured; runtime checks remain important. |
| TB335FC | Real-device validated | Public Bass/Virtualizer routes are treated as runtime false positives and use the dwas_EQ fallback route. |
| TB336FU | Real-device validated | Same measured strength-effect false-positive family as TB335FC; dwas_EQ fallback validated. |
| TB361FU | Partial revalidation state | Equalizer is confirmed audible; strength-effect fallback remains subject to real-device revalidation on the current profile. |
| TB365FC | Real-device validated | Equalizer, core effects, Bass Boost and Virtualizer fallback validated. |
| TB371FC | Restricted / current validation target | Android EQ fallback is used for EQ; Virtualizer is disabled. Build Fix 89 addresses Bass Boost + Headroom shared-core collision and EQ master-off persistence, but final audible real-device revalidation is still required. |
| TB373FU | Safety-restricted | Android Equalizer, Bass Boost and Virtualizer routes remain available; Headroom, Limiter and Balance are disabled on the known unsafe DynamicsProcessing topology. |
| TB375FC | Safety-restricted | Same measured peridot safety policy as TB373FU. |
| TB376FC | Real-device validated route | Playback-session Standard FX route validated. |
| TB378FC | Real-device validated route | Playback-session Standard FX route validated. |
| TB390FU | Captured / runtime-guided | Full-session route is represented; runtime checks are retained. |
| TB520FU | Real-device validated | Equalizer and standard effects validated with proxy-aware fallback behavior. |
| TB522FU | Runtime capability detection | The captured dataset is minimal; dwas_EQ does not assume full Device DAP support. |
| TB710FU | Real-device validated | Equalizer and standard effects validated; Spatializer evidence is not treated as proof of an active route. |

## General rules

- Compatibility is based on measured audio topology and runtime behavior rather than model name alone where possible.
- Known unsafe or non-functional routes are fail-closed.
- Preset Reverb is experimental and is not part of the required compatibility target.
- External audio routes can change which effects are safe or audible.
