@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0" || (
  echo Failed to change to project directory.
  exit /b 1
)

if "%~1"=="" (
  echo Usage: run_continue_model.bat ^<model-path^> [logs^|headless]
  echo Example:
  echo   run_continue_model.bat models\runs\20260315_173500\best-model-20260315_173500-ep150-t42s.nn
  exit /b 1
)

set "NN_MODEL_PATH=%~1"
if not exist "%NN_MODEL_PATH%" (
  echo Model file not found: %NN_MODEL_PATH%
  exit /b 1
)

shift
echo Continuing training from: %NN_MODEL_PATH%
set "NN_MAVEN_EXTRA_PROPS=-Dnn.resume.training=true -Dnn.model.load.path=%NN_MODEL_PATH%"
call "%~dp0run.bat" %*
set "NN_EXIT=%ERRORLEVEL%"

endlocal & exit /b %NN_EXIT%
