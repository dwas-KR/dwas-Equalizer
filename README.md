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

| Item | Details |
|---|---|
| **Project** | **dwas_EQ** |
| **Description** | An independently implemented Android audio-control project for supported Lenovo tablets. dwas_EQ combines a device-aware equalizer path with Android standard audio effects and selects safe backends and fallback routes according to the detected device and audio topology. |
| **Application ID** | `kr.dwas.dwas_EQ` |
| **Android Version** | Android 13 ~ 17 / API 33 ~ 37|
| **UI Framework** | Kotlin + Jetpack Compose |
| **Languages** | Korean, English, Japanese, Traditional Chinese, Taiwan Chinese, Russian, Vietnamese |
| **Proprietary Components** | This project does **not** bundle Lenovo/Dolby proprietary APKs, DEX files, native libraries, tuning databases, or extracted proprietary binaries. |

## Supported Devices

| Device Name | Display size | Model | Application compatibility | 
|---|---|---|---|
| Legion Y700 Gen 2 (2023) | 8.8 | `TB320FC` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
| Legion Y700 Gen 4 (2025) | 8.8 | `TB322FC` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
| Legion Y700 Gen 5 (2026) | 8.8 | `TB323FU` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
| Xiaoxin Pad 11 (2025) | 11 | `TB335FC` | Chinese & Global ROMs supported (TB336FU) |
| Xiaoxin Pad Pro GT 11 | 11 | `TB710FU` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
| Xiaoxin Pad 12.1 (2025) | 12.1 | `TB365FC` | Chinese & Global ROMs supported (TB361FU) |
| Xiaoxin Pad Pro 12.7 (2023) | 12.7 | `TB371FC` | O |
| Xiaoxin Pad Pro 12.7 (2025) | 12.7 | `TB375FC` | Chinese & Global ROMs supported (TB373FU) |
| Yoga Tab Plus AI | 12.7 | `TB520FU` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
| Xiaoxin Pad Pro 13 (2026) | 13 | `TB376FC` | Chinese & Global ROMs supported (TB390FU) |
| Xiaoxin Pad Pro GT 13 (2026) | 13 | `TB378FC` | O |
| Legion Y900 13 (2026) | 13 | `TB522FU` | Chinese ROM(PRC) only (Global ROM(ROW) unverified) |
### Your assistance in testing other models would be highly appreciated.
Link: https://github.com/dwas-KR/dwas-Equalizer/issues/1

## Main Features

| Feature | Description |
|---|---|
| **9-band Equalizer** | `63 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz` |
| **Device-aware Backend Selection** | Selects an appropriate audio backend according to the detected device and audio topology. |
| **Safe Fallback Routing** | Automatically uses safer fallback paths when the preferred audio route is unavailable or known to be unsafe. |
| **Bass Boost** | Adjustable low-frequency enhancement. |
| **Headroom / Attenuation** | Provides additional headroom by attenuating the audio signal where supported. |
| **Limiter** | Helps control excessive output levels where supported. |
| **Left / Right Balance** | Adjustable left and right channel balance. |
| **Virtualizer** | Available only when the detected device and audio route have been verified as safe. |
| **Equalizer Presets** | Includes built-in presets and user-created custom presets. |
| **Wired ADB Bridge** | Optional wired ADB bridge for supported audio-control paths. |
| **Runtime Capability Detection** | Checks available audio capabilities at runtime instead of relying only on static device information. |
| **Fail-closed Safety Handling** | Known unsafe or unsupported audio routes are disabled rather than forced. |
| **Preset Reverb** | Experimental feature. It is not treated as a required compatibility target. |

## Third-party notices

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Android, Lenovo and Dolby names/trademarks belong to their respective owners. dwas_EQ is an independent project and is not affiliated with or endorsed by Lenovo, Dolby Laboratories, Wavelet, or Google.

## License

No open-source license is selected in this publication package. Unless the repository owner adds a `LICENSE` file, publication of source code does not by itself grant additional copying, modification, or redistribution rights beyond rights provided by applicable law and the GitHub Terms of Service.
