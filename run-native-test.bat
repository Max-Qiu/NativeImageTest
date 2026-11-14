@echo off
setlocal

rem ====== Settings ======
set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%"

set "IMAGE=native-image-test:0.0.1-SNAPSHOT"
set "CONTAINER=native-image-test"
set "LOG_TMP=%TEMP%\native-test-container.log"

rem ====== Check Docker and the existing local image ======
docker info >nul 2>&1
if errorlevel 1 (
  echo Docker is not available. Start Docker and try again.
  exit /b 1
)
docker image inspect "%IMAGE%" >nul 2>&1
if errorlevel 1 (
  echo Local image not found: %IMAGE%
  echo Run build-native-image.bat first.
  exit /b 1
)
if not exist "%PROJECT_DIR%\target" mkdir "%PROJECT_DIR%\target"
if errorlevel 1 exit /b 1

rem ====== 1/3 start container ======
echo.
echo === [1/3] docker run %IMAGE% ===
docker rm -f %CONTAINER% >nul 2>&1
docker run -d -p 8080:8080 --name %CONTAINER% %IMAGE%
if errorlevel 1 (
  echo Failed to start container.
  exit /b 1
)

rem ====== 2/3 wait for Spring Boot ready ======
echo.
echo === [2/3] wait for "Started NativeImageTestApplication" ===
set /a WAIT_SECS=0
:waitloop
docker logs %CONTAINER% > "%LOG_TMP%" 2>&1
findstr /c:"Started NativeImageTestApplication" "%LOG_TMP%" >nul 2>&1
if not errorlevel 1 goto started
findstr /c:"APPLICATION FAILED TO START" "%LOG_TMP%" >nul 2>&1
if not errorlevel 1 goto app_failed
if %WAIT_SECS% GEQ 60 goto timeout
powershell -NoProfile -Command "Start-Sleep -Seconds 2"
set /a WAIT_SECS+=2
goto waitloop

:app_failed
echo App failed to start. Container logs:
docker logs %CONTAINER%
docker rm -f %CONTAINER% >nul 2>&1
exit /b 1

:timeout
echo Timed out (60s). Container logs:
docker logs %CONTAINER%
docker rm -f %CONTAINER% >nul 2>&1
exit /b 1

:started
echo App is up on http://localhost:8080 (waited %WAIT_SECS%s)
set "TEST_EXIT=0"

rem ====== 3/3 run API scenarios and behavior assertions ======
echo.
echo === [3/3] run API scenarios and behavior assertions ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\test-api.ps1"
if errorlevel 1 (
  set "TEST_EXIT=1"
  goto cleanup
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\test-native-behavior.ps1"
if errorlevel 1 (
  set "TEST_EXIT=1"
  goto cleanup
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\test-validation.ps1"
if errorlevel 1 set "TEST_EXIT=1"

rem ====== cleanup ======
:cleanup
echo.
echo === cleanup: stop and remove container ===
docker logs %CONTAINER% > "%PROJECT_DIR%\target\native-container.log" 2>&1
docker stop %CONTAINER% >nul 2>&1
docker rm  %CONTAINER% >nul 2>&1
if exist "%LOG_TMP%" del /q "%LOG_TMP%"
echo Container removed.

endlocal & exit /b %TEST_EXIT%
