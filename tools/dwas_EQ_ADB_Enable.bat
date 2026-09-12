@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================
echo dwas_EQ - Wired USB ADB Dolby Bridge Enable v0.4.0
echo ============================================================
echo.

if exist "%~dp0adb.exe" (
    set "ADB=%~dp0adb.exe"
) else (
    where adb >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] adb.exe was not found.
        echo Place this BAT next to adb.exe or add Android platform-tools to PATH.
        pause
        exit /b 1
    )
    set "ADB=adb"
)

"%ADB%" start-server >nul 2>nul

set "TMP_DEV=%TEMP%\dwas_eq_devices_%RANDOM%_%RANDOM%.txt"
"%ADB%" devices >"!TMP_DEV!" 2>nul
set /a DEVICE_COUNT=0
set /a UNAUTHORIZED_COUNT=0
set "SERIAL="
for /f "usebackq skip=1 tokens=1,2" %%A in ("!TMP_DEV!") do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "SERIAL=%%A"
    )
    if "%%B"=="unauthorized" set /a UNAUTHORIZED_COUNT+=1
)
del /q "!TMP_DEV!" >nul 2>nul

if !UNAUTHORIZED_COUNT! GTR 0 (
    echo [ERROR] An unauthorized ADB device was detected.
    echo Unlock the tablet and accept the USB debugging authorization dialog, then retry.
    pause
    exit /b 2
)

if not !DEVICE_COUNT! EQU 1 (
    echo [ERROR] Exactly one authorized USB ADB device is required. Found: !DEVICE_COUNT!
    echo Disconnect extra Android devices/emulators and retry.
    "%ADB%" devices
    pause
    exit /b 3
)

echo [OK] USB ADB device: !SERIAL!

set "TMP_PM=%TEMP%\dwas_eq_pm_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell pm path kr.dwas.dwas_EQ >"!TMP_PM!" 2>nul
set "APK_PATH="
for /f "usebackq tokens=1,* delims=:" %%A in ("!TMP_PM!") do (
    if "%%A"=="package" if not defined APK_PATH set "APK_PATH=%%B"
)
del /q "!TMP_PM!" >nul 2>nul

if not defined APK_PATH (
    echo [ERROR] kr.dwas.dwas_EQ is not installed on the tablet.
    echo Install dwas_EQ.apk first, then rerun this BAT.
    pause
    exit /b 4
)

echo [OK] Installed APK: !APK_PATH!

"%ADB%" -s "!SERIAL!" shell pm grant kr.dwas.dwas_EQ android.permission.DUMP >nul 2>nul
set "TMP_DUMP=%TEMP%\dwas_eq_dump_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell dumpsys package kr.dwas.dwas_EQ >"!TMP_DUMP!" 2>nul
findstr /C:"android.permission.DUMP: granted=true" "!TMP_DUMP!" >nul 2>nul
if errorlevel 1 (
    echo [WARN] android.permission.DUMP could not be verified.
    echo        This is only an optional fallback. The authenticated shell bridge will still provide active media session IDs.
) else (
    echo [OK] Audio-session discovery permission granted: android.permission.DUMP
)
del /q "!TMP_DUMP!" >nul 2>nul

"%ADB%" -s "!SERIAL!" shell pm grant kr.dwas.dwas_EQ android.permission.MODIFY_AUDIO_SETTINGS >nul 2>nul
set "TMP_AUDIO_EQ=%TEMP%\dwas_eq_audio_permission_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell dumpsys package kr.dwas.dwas_EQ >"!TMP_AUDIO_EQ!" 2>nul
findstr /C:"android.permission.MODIFY_AUDIO_SETTINGS: granted=true" "!TMP_AUDIO_EQ!" >nul 2>nul
if errorlevel 1 (
    echo [WARN] Android Equalizer audio-control permission could not be verified: android.permission.MODIFY_AUDIO_SETTINGS
    echo        The app declares this normal Android permission, but AudioEffect control is still decided by the device audio framework at runtime.
) else (
    echo [OK] Android Equalizer audio-control permission verified: android.permission.MODIFY_AUDIO_SETTINGS
)
del /q "!TMP_AUDIO_EQ!" >nul 2>nul

"%ADB%" -s "!SERIAL!" shell "OLDPID=$(pidof dwas_eq_adb 2>/dev/null); if [ -n \"$OLDPID\" ]; then kill $OLDPID 2>/dev/null; fi" >nul 2>nul
timeout /t 1 /nobreak >nul

"%ADB%" -s "!SERIAL!" shell am start -n kr.dwas.dwas_EQ/.MainActivity >nul 2>nul

set "BRIDGE_TOKEN="
set "TMP_TOKEN=%TEMP%\dwas_eq_token_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell cat /proc/sys/kernel/random/uuid >"!TMP_TOKEN!" 2>nul
set /p BRIDGE_TOKEN=<"!TMP_TOKEN!"
del /q "!TMP_TOKEN!" >nul 2>nul
if not defined BRIDGE_TOKEN (
    echo [ERROR] The tablet could not provide a secure one-time bridge token.
    echo Refusing to fall back to a weak pseudo-random token.
    pause
    exit /b 5
)

set /a BRIDGE_ATTEMPT=0
set /a MAX_BRIDGE_ATTEMPTS=8

:BRIDGE_RETRY
set /a BRIDGE_ATTEMPT+=1
if !BRIDGE_ATTEMPT! GTR !MAX_BRIDGE_ATTEMPTS! goto BRIDGE_FAILED
set /a BRIDGE_PORT=38000 + (!RANDOM! %% 1000)

"%ADB%" -s "!SERIAL!" shell "OLDPID=$(pidof dwas_eq_adb 2>/dev/null); if [ -n \"$OLDPID\" ]; then kill $OLDPID 2>/dev/null; fi" >nul 2>nul
"%ADB%" -s "!SERIAL!" shell content call --uri content://kr.dwas.dwas_EQ.wired_adb_bridge --method clear >nul 2>nul

set "TMP_ARM=%TEMP%\dwas_eq_arm_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell content call --uri content://kr.dwas.dwas_EQ.wired_adb_bridge --method arm --arg "!BRIDGE_PORT!:!BRIDGE_TOKEN!" >"!TMP_ARM!" 2>&1
findstr /C:"ok=true" "!TMP_ARM!" >nul 2>nul
if errorlevel 1 (
    echo [WARN] Bridge arm attempt !BRIDGE_ATTEMPT!/!MAX_BRIDGE_ATTEMPTS! failed.
    del /q "!TMP_ARM!" >nul 2>nul
    goto BRIDGE_RETRY
)
del /q "!TMP_ARM!" >nul 2>nul

"%ADB%" -s "!SERIAL!" shell "CLASSPATH='!APK_PATH!' app_process /system/bin --nice-name=dwas_eq_adb kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeMain --port=!BRIDGE_PORT! --token=!BRIDGE_TOKEN! >/data/local/tmp/dwas_eq_adb.log 2>&1 </dev/null &" >nul 2>nul
timeout /t 2 /nobreak >nul

set "TMP_PID=%TEMP%\dwas_eq_pid_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell pidof dwas_eq_adb >"!TMP_PID!" 2>nul
set "BRIDGE_PID="
for /f "usebackq delims=" %%P in ("!TMP_PID!") do set "BRIDGE_PID=%%P"
del /q "!TMP_PID!" >nul 2>nul
if not defined BRIDGE_PID (
    echo [WARN] Port !BRIDGE_PORT! did not produce a stable bridge process. Retrying.
    goto BRIDGE_RETRY
)

set "TMP_PING=%TEMP%\dwas_eq_ping_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell content call --uri content://kr.dwas.dwas_EQ.wired_adb_bridge --method ping >"!TMP_PING!" 2>&1
findstr /C:"ok=true" "!TMP_PING!" >nul 2>nul
if errorlevel 1 (
    echo [WARN] Loopback handshake failed on port !BRIDGE_PORT!. Retrying.
    del /q "!TMP_PING!" >nul 2>nul
    goto BRIDGE_RETRY
)
del /q "!TMP_PING!" >nul 2>nul

echo [OK] Ephemeral bridge endpoint armed: 127.0.0.1:!BRIDGE_PORT!
echo [OK] Wired ADB bridge process is running. PID: !BRIDGE_PID!
echo [OK] App-to-bridge loopback handshake verified on attempt !BRIDGE_ATTEMPT!.
echo [OK] Shell bridge audio-session discovery is available for Bass Boost/Virtualizer/Reverb.
goto BRIDGE_READY

:BRIDGE_FAILED
"%ADB%" -s "!SERIAL!" shell "OLDPID=$(pidof dwas_eq_adb 2>/dev/null); if [ -n \"$OLDPID\" ]; then kill $OLDPID 2>/dev/null; fi" >nul 2>nul
"%ADB%" -s "!SERIAL!" shell content call --uri content://kr.dwas.dwas_EQ.wired_adb_bridge --method clear >nul 2>nul
echo [ERROR] The wired ADB bridge could not acquire a working loopback port after !MAX_BRIDGE_ATTEMPTS! attempts.
echo ---------------- bridge log ----------------
"%ADB%" -s "!SERIAL!" shell cat /data/local/tmp/dwas_eq_adb.log 2>nul
echo --------------------------------------------
pause
exit /b 6

:BRIDGE_READY
"%ADB%" -s "!SERIAL!" shell am start -n kr.dwas.dwas_EQ/.MainActivity >nul 2>nul

echo.
echo Open dwas_EQ and press Refresh read-only probe if needed.
echo Diagnostics should show "Wired ADB Dolby" as connected/controllable after the app reaches the local bridge and the device allows DAP control.
echo.
echo IMPORTANT:
echo - The Wired ADB shell bridge ends after tablet reboot.
echo - Direct Dolby persistence itself does not require this BAT after reboot.
echo - Bass Boost/Virtualizer/Insert Reverb need active player-session discovery; rerun this BAT after reboot if those controls report unavailable.
echo - Rerun this BAT only when Direct Dolby cannot obtain control and you need the Wired ADB fallback.
echo - Rerun this BAT after reinstalling/updating dwas_EQ because the APK path changes.
echo - Keeping the USB cable connected is not required after the shell bridge has started,
echo   but Android/vendor process management may stop the bridge later; rerun this BAT if disconnected.
echo.
pause
exit /b 0
