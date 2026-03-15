@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0" || (
  echo Failed to change to project directory.
  exit /b 1
)

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
  exit /b 1
)

set "MAVEN_OPTS=%MAVEN_OPTS% --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

if not exist "target" mkdir target
if exist "mvnw.cmd" (
  call mvnw.cmd -q clean compile dependency:build-classpath -Dmdep.outputFile=target\modelviewer.classpath
) else (
  call mvn -q clean compile dependency:build-classpath -Dmdep.outputFile=target\modelviewer.classpath
)

if errorlevel 1 (
  exit /b 1
)

set /p NN_MODELVIEWER_CP=<target\modelviewer.classpath
java -cp "target\classes;%NN_MODELVIEWER_CP%" com.nncartrack.ModelViewer

if defined NN_ORIG_JAVA_HOME (
  set "JAVA_HOME=%NN_ORIG_JAVA_HOME%"
)

endlocal
