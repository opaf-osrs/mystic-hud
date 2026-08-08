@echo off
setlocal
cd /d "%~dp0"
echo Starting RuneLite developer client for mystic-hud...
call "%~dp0gradlew.bat" run -PmainClass=com.pluginideahub.mystichud.MysticHudTestClient --args="--debug --developer-mode"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" echo RuneLite developer client exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
