@echo off
setlocal

call "%~dp0run_model_viewer.bat"
set "NN_EXIT=%ERRORLEVEL%"

endlocal & exit /b %NN_EXIT%
