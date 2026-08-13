@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Mystic HUD

REM Everything is inside mystic-hud.jar, client and plugin both, so all this has to
REM do is find a Java to start it with. Only a runtime is needed, not a full JDK.

set "JAR=%~dp0mystic-hud.jar"
if not exist "!JAR!" (
	echo mystic-hud.jar is not next to this file.
	echo Keep the two together in the same folder.
	echo.
	pause
	exit /b 1
)

set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "!PS!" set "PS=powershell"

call :findjava
if !JMAJOR! GEQ 11 goto :launch

REM a hand-installed Java is usually on neither the PATH nor JAVA_HOME, so it looks
REM missing when it is sitting right there. look before installing another one.
set "JAVALIST=%TEMP%\mystichud-java.txt"
"!PS!" -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk.ps1" -RuntimeOnly > "!JAVALIST!" 2>nul
for /f "usebackq delims=" %%p in ("!JAVALIST!") do (
	if exist "%%p\bin\java.exe" set "JAVA_HOME=%%p"
)
del "!JAVALIST!" >nul 2>nul

if defined JAVA_HOME (
	set "PATH=!JAVA_HOME!\bin;!PATH!"
	setx JAVA_HOME "!JAVA_HOME!" >nul
	goto :launch
)

echo Java is needed to run this and there is none on this machine.
echo.
choice /c YN /n /m "Install it now? [Y/N] "
if errorlevel 2 exit /b 1
echo.

where winget >nul 2>nul
if errorlevel 1 goto :viamsi
winget install --id Microsoft.OpenJDK.11 -e --accept-package-agreements --accept-source-agreements
call :locatejava
if defined JAVA_HOME goto :useit

:viamsi
echo Downloading Java from Microsoft...
curl -L -# -o "%TEMP%\jdk11.msi" "https://aka.ms/download-jdk/microsoft-jdk-11-windows-x64.msi"
if not exist "%TEMP%\jdk11.msi" (
	echo Could not download it. Install Java 11 from
	echo     https://learn.microsoft.com/java/openjdk/download
	pause
	exit /b 1
)
msiexec /i "%TEMP%\jdk11.msi" /qn /norestart ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJavaHome
call :locatejava

:useit
if not defined JAVA_HOME (
	echo Java installed but could not be found. Close this window, open it again
	echo and run this once more.
	pause
	exit /b 1
)
set "PATH=!JAVA_HOME!\bin;!PATH!"
setx JAVA_HOME "!JAVA_HOME!" >nul

:launch
REM Jagex account login. the client reads .runelite\credentials.properties itself,
REM so nothing has to be passed to it. all this does is check the file is actually
REM usable and say exactly how to fix it if not, because the failure otherwise is a
REM login screen a jagex account can never get past, with no explanation.
set "CREDS=%USERPROFILE%\.runelite\credentials.properties"
set "HAVECREDS="
if exist "!CREDS!" (
	REM present but blank is a real state and is NOT the same as having a session,
	REM so look at the token itself rather than at the file existing
	for /f "usebackq eol=# tokens=1,* delims==" %%a in ("!CREDS!") do (
		if /i "%%a"=="JX_ACCESS_TOKEN" if not "%%b"=="" set "HAVECREDS=1"
	)
)

if defined HAVECREDS goto :go

echo.
echo   ------------------------------------------------------------
echo    Jagex account login is not set up yet.
echo.
echo    This runs RuneLite directly rather than through the Jagex
echo    Launcher, so it needs the launcher to save your login once.
echo.
echo    1. Open "RuneLite (configure)" from the Start menu
echo    2. In "Client arguments" put:  --insecure-write-credentials
echo    3. Save, then launch RuneLite from the Jagex Launcher once
echo    4. Close it and run this again
echo.
echo    If you use an old style username and password login instead,
echo    you can ignore all of this and just continue.
echo   ------------------------------------------------------------
echo.

set "RLCONF=%LOCALAPPDATA%\RuneLite\RuneLite.exe"
if exist "!RLCONF!" (
	choice /c YN /n /m "Open the RuneLite configure window now? [Y/N] "
	if not errorlevel 2 (
		start "" "!RLCONF!" --configure
		echo.
		echo Set the argument, save, launch once from the Jagex Launcher,
		echo then run this again.
		echo.
		pause
		exit /b 0
	)
	echo.
)

choice /c YN /n /m "Start anyway? [Y/N] "
if errorlevel 2 exit /b 0
echo.

:go
echo Starting Mystic HUD...
java -jar -ea "!JAR!"
set "CODE=%ERRORLEVEL%"
if not "%CODE%"=="0" (
	echo.
	echo Closed with code %CODE%. If that was not expected, send this window to Caleb.
	pause
)
exit /b %CODE%

REM --------------------------------------------------------------- helpers --
:findjava
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

:locatejava
for /d %%d in ("%ProgramFiles%\Microsoft\jdk-11*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-11*") do set "JAVA_HOME=%%d"
exit /b 0
