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

REM full path, not just "powershell": this script exists because the machine's PATH
REM is already unreliable, so do not lean on it any more than necessary
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "!PS!" set "PS=powershell"

REM ---------------------------------------------------------------- java --
REM gradle needs a JDK to run at all and the plugin targets Java 11.
call :findjava
if !JMAJOR! GEQ 11 (
	echo [1/2] Java !JVER! found, nothing to install.
	goto :warm
)

if "!JMAJOR!"=="0" (
	echo [1/2] No Java on the PATH.
) else (
	echo [1/2] Java !JVER! is on the PATH, but this needs 11 or newer.
)

REM a hand-installed JDK usually ends up on neither the PATH nor JAVA_HOME, so it
REM looks missing when it is sitting right there. look before installing a second.
echo       Looking for a JDK already installed...
set "FOUNDJDK="
REM via a file, not a backtick: for /f cannot parse a command whose executable is
REM quoted, and it silently yields nothing rather than complaining.
set "JDKLIST=%TEMP%\mystichud-jdk.txt"
"!PS!" -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk.ps1" > "!JDKLIST!" 2>nul
REM powershell also likes to print its "try the new cross-platform" nag on stdout, so
REM only accept a line that really is a JDK rather than whatever came out last
for /f "usebackq delims=" %%p in ("!JDKLIST!") do (
	if exist "%%p\bin\javac.exe" set "FOUNDJDK=%%p"
)
del "!JDKLIST!" >nul 2>nul
if defined FOUNDJDK (
	set "JAVA_HOME=!FOUNDJDK!"
	echo       Found one, nothing to install.
	goto :javadone
)
echo       None installed.
echo.

REM two ways to get a JDK. winget is the tidy one, the installer straight from
REM Microsoft is the one that always works. try them in that order.
where winget >nul 2>nul
if not errorlevel 1 goto :viawinget

echo       Windows Package Manager ^(winget^) is missing. Trying to set it up.

REM usually it is already on the machine and just not registered for this user,
REM which costs nothing to try and needs no download
"!PS!" -NoProfile -ExecutionPolicy Bypass -Command ^
	"try { Add-AppxPackage -RegisterByFamilyName -MainPackage Microsoft.DesktopAppInstaller_8wekyb3d8bbwe -ErrorAction Stop } catch { exit 1 }" >nul 2>nul
where winget >nul 2>nul
if not errorlevel 1 (
	echo       winget re-registered.
	goto :viawinget
)

echo       Downloading App Installer from Microsoft.
set "TMPDIR=%TEMP%\mystichud-setup"
if not exist "%TMPDIR%" mkdir "%TMPDIR%"
curl -L -s -o "%TMPDIR%\vclibs.appx" "https://aka.ms/Microsoft.VCLibs.x64.14.00.Desktop.appx"
curl -L -s -o "%TMPDIR%\winget.msixbundle" "https://aka.ms/getwinget"
"!PS!" -NoProfile -ExecutionPolicy Bypass -Command ^
	"try { Add-AppxPackage -Path '%TMPDIR%\vclibs.appx' -ErrorAction SilentlyContinue; Add-AppxPackage -Path '%TMPDIR%\winget.msixbundle' -ErrorAction Stop } catch { exit 1 }" >nul 2>nul
where winget >nul 2>nul
if not errorlevel 1 (
	echo       winget installed.
	goto :viawinget
)

echo       winget could not be set up. Installing Java directly instead,
echo       which does the same job without it.
goto :viamsi

:viawinget
echo       Installing Microsoft OpenJDK 11 with winget.
echo       Windows will ask permission.
echo.
winget install --id Microsoft.OpenJDK.11 -e --accept-package-agreements --accept-source-agreements
call :findjava
if !JMAJOR! GEQ 11 goto :javadone
REM winget writes the machine PATH but not this already-open window
call :locatejdk
if defined JAVA_HOME goto :javadone
echo.
echo       winget did not manage it. Falling back to the direct installer.

:viamsi
REM permanent Microsoft link, always the current 11 LTS, so it cannot go stale
set "TMPDIR=%TEMP%\mystichud-setup"
if not exist "%TMPDIR%" mkdir "%TMPDIR%"
echo       Downloading Microsoft OpenJDK 11...
curl -L -# -o "%TMPDIR%\jdk11.msi" "https://aka.ms/download-jdk/microsoft-jdk-11-windows-x64.msi"
if not exist "%TMPDIR%\jdk11.msi" (
	echo.
	echo Could not download it. Check the internet connection, or install
	echo Microsoft OpenJDK 11 by hand from:
	echo     https://learn.microsoft.com/java/openjdk/download
	echo.
	pause
	exit /b 1
)
echo       Installing. Windows will ask permission.
msiexec /i "%TMPDIR%\jdk11.msi" /qn /norestart ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome
call :locatejdk
if not defined JAVA_HOME (
	echo.
	echo Java installed but the folder could not be found. Close this window,
	echo open a new one and run this again.
	pause
	exit /b 1
)

:javadone
if defined JAVA_HOME (
	REM !! not %% : inside a block %JAVA_HOME% expands when the block is PARSED, which
	REM is before any of this set it, so it would quietly put an empty path on PATH
	set "PATH=!JAVA_HOME!\bin;!PATH!"
	REM remember it for future windows, which is the whole point: a hand-installed JDK
	REM that nothing points at is the state this script exists to fix. only JAVA_HOME,
	REM never PATH, since setx truncates long PATH values.
	setx JAVA_HOME "!JAVA_HOME!" >nul
	echo       Java is at !JAVA_HOME!
	echo       JAVA_HOME set for future windows.
)
call :findjava
if !JMAJOR! LSS 11 (
	echo.
	echo Java still is not usable from here. Close this window, open a new one
	echo and run this again.
	pause
	exit /b 1
)

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
exit /b 0

REM --------------------------------------------------------------- helpers --
:findjava
REM sets JVER and JMAJOR from whatever java is reachable. JMAJOR is 0 if none.
if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" set "PATH=!JAVA_HOME!\bin;!PATH!"
set "JVER="
set "JMAJOR=0"
for /f tokens^=3 %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%~v"
if defined JVER (
	for /f "delims=." %%a in ("!JVER!") do set "JMAJOR=%%a"
	REM 1.8.0_xxx style numbering means 8
	if "!JMAJOR!"=="1" set "JMAJOR=8"
)
exit /b 0

:locatejdk
REM finds a freshly installed jdk 11 that is not on this window's PATH yet
for /d %%d in ("%ProgramFiles%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME for /d %%d in ("%ProgramFiles(x86)%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-11*") do set "JAVA_HOME=%%d"
exit /b 0
