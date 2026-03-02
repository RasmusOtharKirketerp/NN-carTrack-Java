@echo off
setlocal EnableDelayedExpansion

REM Always run from this script's directory (handles spaces/OneDrive paths).
cd /d "%~dp0" || (
  echo Failed to change to project directory.
  exit /b 1
)

REM Prefer Maven Wrapper when available; fall back to system Maven.
set "NN_JVM_ARGS="
set "NN_APP_ARGS="
set "NN_HEAP_ARGS=-Xms1g -Xmx6g"
set "NN_MODEL_JVM_ARGS="
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
  set "NN_MODEL_JVM_ARGS=-Dnn.model.save.path=models/best-model-!NN_RUN_TS!.nn"
  echo Model output: models/best-model-!NN_RUN_TS!.nn
)

if defined NN_JAVA_HEAP (
  set "NN_HEAP_ARGS=%NN_JAVA_HEAP%"
)

if defined NN_JVM_ARGS (
  set "NN_JVM_ARGS=%NN_JVM_ARGS% %NN_HEAP_ARGS% %NN_MODEL_JVM_ARGS%"
) else (
  set "NN_JVM_ARGS=%NN_HEAP_ARGS% %NN_MODEL_JVM_ARGS%"
)

if exist "mvnw.cmd" (
  call mvnw.cmd -q clean compile exec:java -Dexec.jvmArgs="%NN_JVM_ARGS%" -Dexec.args="%NN_APP_ARGS%"
) else (
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo Maven was not found.
    echo Install Maven or add mvnw.cmd to this project.
    exit /b 1
  )
  call mvn -q clean compile exec:java -Dexec.jvmArgs="%NN_JVM_ARGS%" -Dexec.args="%NN_APP_ARGS%"
)

if errorlevel 1 (
  echo Build or run failed.
  exit /b 1
)

endlocal
