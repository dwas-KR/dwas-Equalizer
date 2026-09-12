<p align="center">
  <img src="assets/dwas_EQ_icon_1024.png" alt="dwas_EQ" width="160" />
</p>

<h1 align="center">dwas_EQ</h1>

<p align="center">
  Equalizer and audio-effect controller for supported Lenovo Android tablets.<br>
  레노버 Android 태블릿을 위한 이퀄라이저 및 오디오 효과 제어 앱입니다.
</p>

<p align="center">
  <a href="https://github.com/dwas-KR/dwas-Equalizer/releases"><img src="https://img.shields.io/github/v/release/dwas-KR/dwas-Equalizer?display_name=tag" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84" alt="Android 13+">
  <img src="https://img.shields.io/badge/version-0.4.0-blue" alt="Version 0.4.0">
</p>

## Overview

dwas_EQ is an independently implemented Android audio-control project for supported Lenovo tablets. It combines a device-aware equalizer path with Android standard audio effects and selects safe routes according to the detected device/audio topology.

- Application ID: `kr.dwas.dwas_EQ`
- Minimum Android version: Android 13 / API 33
- Target / compile SDK: API 37
- UI: Kotlin + Jetpack Compose
- Languages: Korean, English, Japanese, Traditional Chinese, Taiwan Chinese, Russian and Vietnamese
- Current release: `v0.4.0`

The project does **not** bundle Lenovo/Dolby proprietary APK, DEX, native library, tuning database or extracted proprietary binary.

## Main features

- 9-band equalizer: `63 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz`
- Device-aware backend selection with safe fallback routing
- Bass Boost
- Headroom / attenuation
- Limiter
- Left / right channel balance
- Virtualizer where the detected device route is verified safe
- User presets and built-in presets
- Optional wired ADB bridge for supported control paths
- Runtime capability checks and fail-closed handling for known unsafe audio routes

Preset Reverb is an experimental feature and is not treated as a required compatibility target.

## Download

Use the GitHub **Releases** page for installation files:

**https://github.com/dwas-KR/dwas-Equalizer/releases**

Recommended assets:

- `dwas_EQ-v0.4.0.apk` — Android application
- `dwas_EQ-v0.4.0-Windows-ADB-Helper.zip` — optional Windows helper scripts; requires Android Platform Tools / `adb` in PATH
- `SHA256SUMS.txt` — release checksums

Do not download an APK from the repository source tree. Release binaries are distributed from GitHub Releases.

## Supported devices

Compatibility is device- and firmware-dependent. A model being listed does not mean every optional effect is available on every firmware.

Currently analyzed device families include:

`TB320FC`, `TB322FC`, `TB323FU`, `TB331FC`, `TB335FC`, `TB336FU`, `TB361FU`, `TB365FC`, `TB371FC`, `TB373FU`, `TB375FC`, `TB376FC`, `TB378FC`, `TB390FU`, `TB520FU`, `TB522FU`, `TB710FU`.

`TB320FU` is handled as an archive/device identity alias of `TB320FC` where applicable.

See [Supported devices](docs/SUPPORTED_DEVICES.md) for current validation notes and restrictions.

## Installation

1. Download `dwas_EQ-v0.4.0.apk` from GitHub Releases.
2. Install the APK on the tablet.
3. Open dwas_EQ and review the detected device/backend state.
4. Use the Equalizer master toggle and apply the desired curve.
5. Enable only audio effects that are available for the detected device.

For the optional Windows wired ADB bridge, see [Windows ADB bridge](docs/WINDOWS_ADB_BRIDGE.md).

## Building from source

Requirements:

- JDK 17
- Android SDK API 37
- Internet access for the initial Gradle/dependency download

Windows:

```bat
gradlew.bat clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux/macOS:

```bash
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Gradle wrapper uses Gradle 9.4.1. See [Building](docs/BUILDING.md) for details.

## Safety and compatibility policy

dwas_EQ does not force every effect onto every device. When runtime evidence shows that an audio route is unavailable or unsafe, the corresponding option can be disabled instead of attempting an unverified implementation.

Examples in the current compatibility database include:

- TB371FC: Virtualizer is fail-closed because tested routes did not retain a safe audible effect.
- TB373FU / TB375FC: Headroom, Limiter and Channel Balance are disabled on the known unsafe DynamicsProcessing topology.
- TB522FU: limited captured data; runtime capability detection is used.

This policy is intended to reduce audio break-up, silent output and false-positive controls.

## Wired ADB helper

The repository contains `tools/dwas_EQ_ADB_Enable.bat` and `tools/dwas_EQ_ADB_Disable.bat`. The recommended public Release helper ZIP does **not** bundle Google's `adb.exe`; install official Android Platform Tools and ensure `adb` is available in PATH.

## Third-party notices

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Android, Lenovo and Dolby names/trademarks belong to their respective owners. dwas_EQ is an independent project and is not affiliated with or endorsed by Lenovo, Dolby Laboratories, Wavelet, or Google.

## License

No open-source license is selected in this publication package. Unless the repository owner adds a `LICENSE` file, publication of source code does not by itself grant additional copying, modification, or redistribution rights beyond rights provided by applicable law and the GitHub Terms of Service.

## Links

- Repository: https://github.com/dwas-KR/dwas-Equalizer
- Releases: https://github.com/dwas-KR/dwas-Equalizer/releases
- dwas GitHub: https://github.com/dwas-KR
