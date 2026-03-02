@echo off
setlocal

REM Dedicated headless training launcher:
REM - no UI
REM - no file logs
REM - still trains and saves best model checkpoints
call "%~dp0run.bat" headless

endlocal
