# Windows wired ADB bridge

The optional wired ADB helper can expose the limited dwas_EQ bridge used by supported fallback paths.

## Requirements

- Windows 10/11
- Official Android Platform Tools installed
- `adb.exe` available in PATH, or placed next to the helper BAT files by the user
- USB debugging enabled on the tablet
- Exactly one authorized Android device connected during setup

## Enable

1. Connect the tablet by USB.
2. Confirm the USB debugging authorization dialog on the tablet.
3. Run `dwas_EQ_ADB_Enable.bat`.
4. Follow the messages shown by the script.
5. Open dwas_EQ and refresh the read-only probe if required.

The bridge binds to `127.0.0.1`, uses an ephemeral token and is limited to dwas_EQ's defined control/discovery path. It is not intended to expose a generic shell RPC interface.

## Disable

Run `dwas_EQ_ADB_Disable.bat`.

The script stops the dwas_EQ bridge process, clears bridge state and revokes the optional grants used by the bridge.

## Reboot / update behavior

The shell bridge ends after a tablet reboot. It may also need to be re-enabled after reinstalling/updating the app because the APK path can change.

## Public release packaging

The recommended GitHub Release helper ZIP contains only the dwas_EQ BAT files and this guide. Google Android Platform Tools binaries are intentionally not bundled in the recommended package.
