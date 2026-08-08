@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo   Mystic HUD setup
echo ============================================
echo.
echo This installs what the client needs and downloads everything up front,
echo so the first launch is not a long silent wait. Run it once.
echo.

REM ---------------------------------------------------------------- java --
REM gradle needs a JDK to run at all and the plugin targets Java 11.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

set "JMAJOR=0"
for /f tokens^=3 %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%~v"
if defined JVER (
	for /f "delims=." %%a in ("!JVER!") do set "JMAJOR=%%a"
	REM 1.8.0_xxx style numbering means 8
	if "!JMAJOR!"=="1" set "JMAJOR=8"
)

if !JMAJOR! GEQ 11 (
	echo [1/2] Java !JVER! found, nothing to install.
	goto :warm
)

if "!JMAJOR!"=="0" (
	echo [1/2] No Java on this machine.
) else (
	echo [1/2] Java !JVER! found, but this needs 11 or newer.
)

where winget >nul 2>nul
if errorlevel 1 (
	echo.
	echo Windows Package Manager ^(winget^) is not available here, so Java cannot
	echo be installed automatically. Download Microsoft OpenJDK 11 from:
	echo.
	echo     https://learn.microsoft.com/java/openjdk/download
	echo.
	echo Install it, then run this again.
	echo.
	pause
	exit /b 1
)

echo       Installing Microsoft OpenJDK 11. Windows will ask permission.
echo.
winget install --id Microsoft.OpenJDK.11 -e --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
	echo.
	echo That did not install. Try the manual download above.
	pause
	exit /b 1
)

REM winget writes the machine PATH but not this already-open window, so find the
REM install and use it directly for the rest of this run
for /d %%d in ("%ProgramFiles%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME for /d %%d in ("%ProgramFiles(x86)%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME (
	echo.
	echo Java installed, but the folder could not be found. Close this window,
	echo open a new one and run this again.
	pause
	exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM remember it for future windows. only JAVA_HOME, never PATH: setx truncates
REM long PATH values and that is not worth risking on someone else's machine.
setx JAVA_HOME "%JAVA_HOME%" >nul
echo       Installed to %JAVA_HOME%

:warm
echo.
echo [2/2] Downloading Gradle and the client libraries, then building.
echo       First time this takes a few minutes. Later runs are instant.
echo.
call "%~dp0gradlew.bat" build
if errorlevel 1 (
	echo.
	echo The build failed. Send the messages above to Caleb.
	pause
	exit /b 1
)

echo.
echo ============================================
echo   Done. Start the client with run-client.bat
echo ============================================
echo.
pause
