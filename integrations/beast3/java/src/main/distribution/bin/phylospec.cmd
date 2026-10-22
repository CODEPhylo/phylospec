@echo off
setlocal DisableDelayedExpansion
rem Keep the caller's working directory so relative script and --out paths retain their meaning.
set "PHYLOSPEC_HOME=%~dp0.."
if not exist "%PHYLOSPEC_HOME%\lib\*.jar" (
    >&2 echo PhyloSpec: No runtime JARs found in "%PHYLOSPEC_HOME%\lib". Extract the complete distribution.
    exit /b 1
)

set "PHYLOSPEC_JAVA=java"
if defined JAVA_HOME (
    set "PHYLOSPEC_JAVA=%JAVA_HOME%\bin\java.exe"
    if not exist "%JAVA_HOME%\bin\java.exe" (
        >&2 echo PhyloSpec: JAVA_HOME does not contain bin\java.exe: "%JAVA_HOME%"
        exit /b 1
    )
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        >&2 echo PhyloSpec: Java 25 or newer is required. Set JAVA_HOME or put java on PATH.
        exit /b 1
    )
)

set "PHYLOSPEC_JAVA_VERSION="
for /f "tokens=1,2,3" %%A in ('""%PHYLOSPEC_JAVA%" -version 2^>^&1"') do if "%%B"=="version" set "PHYLOSPEC_JAVA_VERSION=%%~C"
if not defined PHYLOSPEC_JAVA_VERSION goto unknown_java_version
set "PHYLOSPEC_JAVA_MAJOR="
for /f "tokens=1 delims=.-" %%V in ("%PHYLOSPEC_JAVA_VERSION%") do set "PHYLOSPEC_JAVA_MAJOR=%%V"
if not defined PHYLOSPEC_JAVA_MAJOR goto unknown_java_version
for /f "delims=0123456789" %%V in ("%PHYLOSPEC_JAVA_MAJOR%") do goto unknown_java_version
if %PHYLOSPEC_JAVA_MAJOR% LSS 25 (
    >&2 echo PhyloSpec: Java 25 or newer is required; found %PHYLOSPEC_JAVA_VERSION%. Set JAVA_HOME or update java on PATH.
    exit /b 1
)

"%PHYLOSPEC_JAVA%" -cp "%PHYLOSPEC_HOME%\lib\*" runner.PhyloSpecCli %*
exit /b %errorlevel%

:unknown_java_version
>&2 echo PhyloSpec: Cannot determine the Java version. Java 25 or newer is required.
exit /b 1
