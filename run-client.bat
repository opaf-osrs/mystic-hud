@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM setup.bat installs the JDK and may have set JAVA_HOME for later windows
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

where java >nul 2>nul
if errorlevel 1 (
	echo No Java found. Run setup.bat first.
	pause
	exit /b 1
)

REM This starts gradle, so it has the same Java ceiling the build does: past 22 it
REM dies on "Unsupported class file major version", which says nothing about Java to
REM anyone reading it. Say it here instead, where it can name the actual problem.
set "JMAXBUILD=22"
set "JMAJOR=0"
for /f tokens^=3 %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%~v"
if defined JVER (
	for /f "delims=." %%a in ("!JVER!") do set "JMAJOR=%%a"
	if "!JMAJOR!"=="1" set "JMAJOR=8"
)
if !JMAJOR! GTR !JMAXBUILD! (
	echo Java !JVER! is too new for the build tool, which needs Java 11 to !JMAXBUILD!.
	echo Run setup.bat, which installs one alongside without touching your Java.
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
