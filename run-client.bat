@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM the build targets Java 11 and gradle needs a JDK to run at all, so check for one
REM before doing anything else. an existing JAVA_HOME wins if it looks usable.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

set "JMAJOR=0"
for /f tokens^=3 %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
	set "JVER=%%~v"
)
if defined JVER (
	for /f "delims=." %%a in ("!JVER!") do set "JMAJOR=%%a"
	REM 1.8.0_xxx style means 8
	if "!JMAJOR!"=="1" set "JMAJOR=8"
)

if !JMAJOR! GEQ 11 goto :run

if "!JMAJOR!"=="0" (
	echo No Java was found on this machine.
) else (
	echo Java !JVER! was found, but this needs 11 or newer.
)
echo.

where winget >nul 2>nul
if errorlevel 1 (
	echo winget is not available here, so it cannot install Java for you.
	echo Grab Microsoft OpenJDK 11 from:
	echo    https://learn.microsoft.com/java/openjdk/download
	echo Install it, then run this again.
	echo.
	pause
	exit /b 1
)

choice /c YN /n /m "Install Microsoft OpenJDK 11 now? [Y/N] "
if errorlevel 2 (
	echo Nothing installed. This cannot run without a JDK.
	pause
	exit /b 1
)

echo Installing Microsoft OpenJDK 11, this takes a couple of minutes...
winget install --id Microsoft.OpenJDK.11 -e --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
	echo winget could not install it. Try the manual download above.
	pause
	exit /b 1
)

REM winget updates the machine PATH but not this already-running window, so point at
REM the fresh install directly rather than making the user reopen the terminal
for /d %%d in ("%ProgramFiles%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME for /d %%d in ("%ProgramFiles(x86)%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME (
	echo Installed, but the install folder could not be found.
	echo Close this window, open a new one, and run this again.
	pause
	exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using !JAVA_HOME!
echo.

:run
echo Starting RuneLite developer client for mystic-hud...
call "%~dp0gradlew.bat" run -PmainClass=com.pluginideahub.mystichud.MysticHudTestClient --args="--debug --developer-mode"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" echo RuneLite developer client exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
