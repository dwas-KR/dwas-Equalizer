@echo off
setlocal
call "%~dp0tools\dwas_EQ_Android_SDK_Setup.bat"
if errorlevel 1 exit /b 1
call "%~dp0gradlew.bat" clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
exit /b %ERRORLEVEL%
