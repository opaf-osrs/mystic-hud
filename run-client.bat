@echo off
setlocal
cd /d "%~dp0"

REM setup.bat installs the JDK and may have set JAVA_HOME for later windows
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

where java >nul 2>nul
if errorlevel 1 (
	echo No Java found. Run setup.bat first.
	pause
	exit /b 1
)

echo Starting RuneLite developer client for mystic-hud...
call "%~dp0gradlew.bat" run -PmainClass=com.pluginideahub.mystichud.MysticHudTestClient --args="--debug --developer-mode"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" echo RuneLite developer client exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
