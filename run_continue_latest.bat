@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0" || (
  echo Failed to change to project directory.
  exit /b 1
)

set "NN_LATEST_MODEL="
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "$preferred = Get-ChildItem -Path 'models' -Recurse -Filter 'best-model-*.nn' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if ($preferred) { $preferred.FullName } else { Get-ChildItem -Path 'models' -Filter '*.nn' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName }"`) do (
  set "NN_LATEST_MODEL=%%i"
)

if not defined NN_LATEST_MODEL (
  echo No model files were found in the models folder.
  exit /b 1
)

echo Continuing training from: !NN_LATEST_MODEL!
set "NN_MAVEN_EXTRA_PROPS=-Dnn.resume.training=true -Dnn.model.load.path=!NN_LATEST_MODEL!"
call "%~dp0run.bat" %*
set "NN_EXIT=%ERRORLEVEL%"

endlocal & exit /b %NN_EXIT%
