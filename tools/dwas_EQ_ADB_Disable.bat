@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================
echo dwas_EQ - Wired USB ADB Dolby Bridge Disable v0.4.0
echo ============================================================
echo.

if exist "%~dp0adb.exe" (
    set "ADB=%~dp0adb.exe"
) else (
    where adb >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] adb.exe was not found.
        pause
        exit /b 1
    )
    set "ADB=adb"
)

set "TMP_DEV=%TEMP%\dwas_eq_devices_%RANDOM%_%RANDOM%.txt"
"%ADB%" devices >"!TMP_DEV!" 2>nul
set /a DEVICE_COUNT=0
set "SERIAL="
for /f "usebackq skip=1 tokens=1,2" %%A in ("!TMP_DEV!") do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "SERIAL=%%A"
    )
)
del /q "!TMP_DEV!" >nul 2>nul

if not !DEVICE_COUNT! EQU 1 (
    echo [ERROR] Exactly one authorized USB ADB device is required. Found: !DEVICE_COUNT!
    pause
    exit /b 1
)

"%ADB%" -s "!SERIAL!" shell "PID=$(pidof dwas_eq_adb 2>/dev/null); if [ -n \"$PID\" ]; then kill $PID 2>/dev/null; fi" >nul 2>nul
timeout /t 1 /nobreak >nul
"%ADB%" -s "!SERIAL!" shell content call --uri content://kr.dwas.dwas_EQ.wired_adb_bridge --method clear >nul 2>nul
"%ADB%" -s "!SERIAL!" shell rm -f /data/local/tmp/dwas_eq_adb.log >nul 2>nul

"%ADB%" -s "!SERIAL!" shell pm revoke kr.dwas.dwas_EQ android.permission.DUMP >nul 2>nul
"%ADB%" -s "!SERIAL!" shell pm revoke kr.dwas.dwas_EQ android.permission.MODIFY_AUDIO_SETTINGS >nul 2>nul

set "TMP_PID=%TEMP%\dwas_eq_pid_%RANDOM%_%RANDOM%.txt"
"%ADB%" -s "!SERIAL!" shell pidof dwas_eq_adb >"!TMP_PID!" 2>nul
set "BRIDGE_PID="
for /f "usebackq delims=" %%P in ("!TMP_PID!") do set "BRIDGE_PID=%%P"
del /q "!TMP_PID!" >nul 2>nul

if defined BRIDGE_PID (
    echo [ERROR] Bridge is still running: !BRIDGE_PID!
    pause
    exit /b 2
)

echo [OK] dwas_EQ wired ADB bridge stopped, bridge log removed, and optional control grants revoked.
pause
exit /b 0
