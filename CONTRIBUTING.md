# Contributing

Before proposing a change:

1. Keep device-specific behavior behind measured capability/topology checks where possible.
2. Do not bundle Lenovo/Dolby proprietary APK, DEX, native libraries, tuning databases or extracted proprietary binaries.
3. Do not copy implementation code or DSP algorithms from third-party equalizer applications.
4. Preserve fail-closed behavior for routes marked unsafe or unavailable.
5. Run the relevant unit tests and Android lint/build checks before opening a pull request.
6. Do not commit signing keys, secrets, local SDK paths, APK outputs or private logs.

Please describe the device model, Android version, ROM type and real-device validation evidence for audio-routing changes.
