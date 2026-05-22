@echo off
setlocal

rem ====== Settings ======
set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%"

set "JAR=target\NativeImageTest-0.0.1-SNAPSHOT.jar"
set "HINTS_DIR=src\main\resources\META-INF\native-image\com.maxqiu\native-image-test"
set "AGENT_OPT=-agentlib:native-image-agent=config-merge-dir=%PROJECT_DIR%\%HINTS_DIR%,config-write-period-secs=5"
set "APP_LOG=%PROJECT_DIR%\target\agent-run.log"
set "APP_ERR=%PROJECT_DIR%\target\agent-run.err.log"
set "PID_FILE=%TEMP%\collect-hints-pid.txt"

rem ====== 1/5 build jar ======
echo.
echo === [1/5] mvn clean package -DskipTests ===
call mvn clean package -DskipTests
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)
if not exist "%JAR%" (
  echo Jar not found: %JAR%
  exit /b 1
)

rem ====== 2/5 start jar with native-image-agent ======
echo.
echo === [2/5] start jar with native-image-agent ===
if exist "%APP_LOG%"  del /q "%APP_LOG%"
if exist "%APP_ERR%"  del /q "%APP_ERR%"
if exist "%PID_FILE%" del /q "%PID_FILE%"

rem Write PID to a temp file instead of using for/f backtick, which causes
rem a pipe-inheritance deadlock: Java inherits the pipe handle from PowerShell
rem and for/f waits forever for EOF that never comes.
powershell -NoProfile -Command "(Start-Process java -ArgumentList '%AGENT_OPT%','-jar','%JAR%' -WorkingDirectory '%PROJECT_DIR%' -RedirectStandardOutput '%APP_LOG%' -RedirectStandardError '%APP_ERR%' -PassThru -WindowStyle Hidden).Id | Out-File -Encoding ASCII '%PID_FILE%' -NoNewline"

if not exist "%PID_FILE%" (
  echo Failed to launch java process.
  exit /b 1
)
set /p JAVA_PID=<"%PID_FILE%"
del /q "%PID_FILE%"

if not defined JAVA_PID (
  echo Failed to read java PID.
  exit /b 1
)
echo java pid=%JAVA_PID%

rem ====== 3/5 wait until Spring Boot started ======
echo.
echo === [3/5] wait for "Started NativeImageTestApplication" ===
set /a WAIT_SECS=0
:waitloop
if exist "%APP_LOG%" (
  findstr /c:"Started NativeImageTestApplication" "%APP_LOG%" >nul 2>&1
  if not errorlevel 1 goto started
  findstr /c:"APPLICATION FAILED TO START" "%APP_LOG%" >nul 2>&1
  if not errorlevel 1 goto failed
)
if exist "%APP_ERR%" (
  findstr /c:"Exception in thread" "%APP_ERR%" >nul 2>&1
  if not errorlevel 1 goto failed
)
if %WAIT_SECS% GEQ 120 goto timeout
timeout /t 2 /nobreak >nul
set /a WAIT_SECS+=2
goto waitloop

:failed
echo App failed to start. See %APP_LOG% / %APP_ERR%
taskkill /F /PID %JAVA_PID% >nul 2>&1
exit /b 1

:timeout
echo Timed out waiting for app startup (120s). See %APP_LOG%
taskkill /F /PID %JAVA_PID% >nul 2>&1
exit /b 1

:started
echo App is up on http://localhost:8080

rem ====== 4/5 run test-api.ps1 ======
echo.
echo === [4/5] run test-api.ps1 ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\test-api.ps1"

rem ====== 5/5 flush + stop JVM + cleanup ======
echo.
echo === [5/5] wait for final agent flush, then stop JVM ===
timeout /t 7 /nobreak >nul
taskkill /F /PID %JAVA_PID% >nul 2>&1
if exist "%HINTS_DIR%\.lock" del /q "%HINTS_DIR%\.lock"

echo.
echo Done. Hints written to:
echo   %HINTS_DIR%\reachability-metadata.json
echo App log:
echo   %APP_LOG%

endlocal
