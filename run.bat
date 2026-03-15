@echo off
setlocal EnableDelayedExpansion

REM Always run from this script's directory (handles spaces/OneDrive paths).
cd /d "%~dp0" || (
  echo Failed to change to project directory.
  exit /b 1
)

REM Require Java 21 or newer. Prefer JAVA_HOME_21/JAVA_HOME_25 if present, then fall back to JAVA_HOME.
set "NN_ORIG_JAVA_HOME=%JAVA_HOME%"
set "NN_JAVA_CANDIDATE="
if defined JAVA_HOME_25 (
  if exist "%JAVA_HOME_25%\bin\java.exe" (
    set "NN_JAVA_CANDIDATE=%JAVA_HOME_25%"
  )
)
if not defined NN_JAVA_CANDIDATE if defined JAVA_HOME_21 (
  if exist "%JAVA_HOME_21%\bin\java.exe" (
    set "NN_JAVA_CANDIDATE=%JAVA_HOME_21%"
  )
)
if not defined NN_JAVA_CANDIDATE if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "NN_JAVA_CANDIDATE=%JAVA_HOME%"
  )
)

if defined NN_JAVA_CANDIDATE (
  set "JAVA_HOME=%NN_JAVA_CANDIDATE%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found.
  echo Install JDK 21 and set JAVA_HOME_21 ^(recommended^) or JAVA_HOME.
  exit /b 1
)

set "NN_JAVA_VERSION="
for /f "tokens=3 delims= " %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
  set "NN_JAVA_VERSION=%%~i"
  goto :version_found
)

:version_found
set "NN_JAVA_VERSION=!NN_JAVA_VERSION:"=!"
for /f "tokens=1 delims=." %%i in ("!NN_JAVA_VERSION!") do set "NN_JAVA_MAJOR=%%i"

if !NN_JAVA_MAJOR! LSS 21 (
  echo This project requires Java 21 or newer.
  echo Current java version: !NN_JAVA_VERSION!
  echo Set JAVA_HOME to a JDK 21+ install path, for example:
  echo   setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
  if defined NN_ORIG_JAVA_HOME (
    set "JAVA_HOME=%NN_ORIG_JAVA_HOME%"
  )
  exit /b 1
)

echo Using Java !NN_JAVA_VERSION!

REM Prefer Maven Wrapper when available; fall back to system Maven.
set "NN_JVM_ARGS="
set "NN_APP_ARGS="
set "NN_HEAP_ARGS=-Xms1g -Xmx6g"
set "NN_MODEL_JVM_ARGS="
set "NN_EXTRA_JVM_ARGS="
set "NN_MAVEN_PROPS="
set "NN_MAVEN_JAVA_OPTS=--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
if defined NN_JAVA_EXTRA_ARGS (
  set "NN_EXTRA_JVM_ARGS=%NN_JAVA_EXTRA_ARGS%"
)
if defined NN_MAVEN_EXTRA_PROPS (
  set "NN_MAVEN_PROPS=%NN_MAVEN_EXTRA_PROPS%"
)
if /I "%~1"=="logs" (
  set "NN_JVM_ARGS=-Dnn.headless=true -Djava.awt.headless=true"
  set "NN_APP_ARGS=logs"
  echo Running in logs-only mode ^(headless, no UI^)...
)
if /I "%~1"=="headless" (
  set "NN_JVM_ARGS=-Dnn.headless=true -Djava.awt.headless=true -Dnn.filelogs=false"
  set "NN_APP_ARGS=headless"
  echo Running in headless mode ^(no UI, no file logs, saves model^)...
)
if /I "%~1"=="play" (
  set "NN_JVM_ARGS=-Dnn.mode=play"
  set "NN_APP_ARGS=play"
  echo Running in play mode ^(inference only, no training^)...
)
if /I "%~1"=="playlogs" (
  set "NN_JVM_ARGS=-Dnn.mode=play -Dnn.headless=true -Djava.awt.headless=true"
  set "NN_APP_ARGS=play logs"
  echo Running in play+logs mode ^(inference only, headless^)...
)

if /I not "%~1"=="play" if /I not "%~1"=="playlogs" (
  for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "NN_RUN_TS=%%i"
  set "NN_MODEL_JVM_ARGS=-Dnn.run.ts=!NN_RUN_TS!"
  echo Model output dir: models/runs/!NN_RUN_TS!\
)

if defined NN_JAVA_HEAP (
  set "NN_HEAP_ARGS=%NN_JAVA_HEAP%"
)

if defined NN_JVM_ARGS (
  set "NN_JVM_ARGS=%NN_JVM_ARGS% %NN_HEAP_ARGS% %NN_MODEL_JVM_ARGS%"
) else (
  set "NN_JVM_ARGS=%NN_HEAP_ARGS% %NN_MODEL_JVM_ARGS%"
)
if defined NN_EXTRA_JVM_ARGS (
  set "NN_JVM_ARGS=%NN_JVM_ARGS% %NN_EXTRA_JVM_ARGS%"
)

REM Java 23 warns about native access and Unsafe usage inside Maven's own libraries.
REM Scope the compatibility flags to this script so the project can run cleanly.
if defined MAVEN_OPTS (
  set "MAVEN_OPTS=%MAVEN_OPTS% %NN_MAVEN_JAVA_OPTS%"
) else (
  set "MAVEN_OPTS=%NN_MAVEN_JAVA_OPTS%"
)

if exist "mvnw.cmd" (
  call mvnw.cmd -q clean compile exec:java %NN_MAVEN_PROPS% -Dexec.jvmArgs="%NN_JVM_ARGS%" -Dexec.args="%NN_APP_ARGS%"
) else (
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo Maven was not found.
    echo Install Maven or add mvnw.cmd to this project.
    exit /b 1
  )
  call mvn -q clean compile exec:java %NN_MAVEN_PROPS% -Dexec.jvmArgs="%NN_JVM_ARGS%" -Dexec.args="%NN_APP_ARGS%"
)

if errorlevel 1 (
  echo Build or run failed.
  exit /b 1
)

endlocal
