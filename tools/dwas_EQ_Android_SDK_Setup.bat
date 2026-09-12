@echo off
setlocal
set "DWAS_ANDROID_SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools" set "DWAS_ANDROID_SDK=%ANDROID_HOME%"
if not defined DWAS_ANDROID_SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools" set "DWAS_ANDROID_SDK=%ANDROID_SDK_ROOT%"
if not defined DWAS_ANDROID_SDK if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools" set "DWAS_ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined DWAS_ANDROID_SDK (
    echo [dwas_EQ] Android SDK location not found.
    echo [dwas_EQ] Install Android SDK from Android Studio or set ANDROID_HOME, then run this file again.
    exit /b 1
)
set "DWAS_ANDROID_SDK_PROP="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$p=$env:DWAS_ANDROID_SDK -replace '\\','/'; $p=$p -replace ':','\:'; [Console]::Write($p)"`) do set "DWAS_ANDROID_SDK_PROP=%%P"
if not defined DWAS_ANDROID_SDK_PROP (
    echo [dwas_EQ] Android SDK path conversion failed.
    exit /b 1
)
>"%~dp0..\local.properties" echo sdk.dir=%DWAS_ANDROID_SDK_PROP%
echo [dwas_EQ] local.properties created: %DWAS_ANDROID_SDK_PROP%
exit /b 0
