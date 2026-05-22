@echo off
setlocal

rem ====== Settings ======
set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
cd /d "%PROJECT_DIR%"

set "IMAGE=native-image-test:0.0.1-SNAPSHOT"
set "CONTAINER=native-image-test"
set "LOG_TMP=%TEMP%\native-test-container.log"

rem ====== 1/4 build native image (Paketo buildpack) ======
echo.
echo === [1/4] mvn spring-boot:build-image -Pnative ===
call mvn spring-boot:build-image -Pnative -DskipTests
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

rem ====== 2/4 start container ======
echo.
echo === [2/4] docker run %IMAGE% ===
docker rm -f %CONTAINER% >nul 2>&1
docker run -d -p 8080:8080 --name %CONTAINER% %IMAGE%
if errorlevel 1 (
  echo Failed to start container.
  exit /b 1
)

rem ====== 3/4 wait for Spring Boot ready ======
echo.
echo === [3/4] wait for "Started NativeImageTestApplication" ===
set /a WAIT_SECS=0
:waitloop
docker logs %CONTAINER% > "%LOG_TMP%" 2>&1
findstr /c:"Started NativeImageTestApplication" "%LOG_TMP%" >nul 2>&1
if not errorlevel 1 goto started
findstr /c:"APPLICATION FAILED TO START" "%LOG_TMP%" >nul 2>&1
if not errorlevel 1 goto app_failed
if %WAIT_SECS% GEQ 60 goto timeout
timeout /t 2 /nobreak >nul
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

rem ====== 4/4 run test-api.ps1 ======
echo.
echo === [4/4] run test-api.ps1 ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_DIR%\test-api.ps1"

rem ====== cleanup ======
echo.
echo === cleanup: stop and remove container ===
docker stop %CONTAINER% >nul 2>&1
docker rm  %CONTAINER% >nul 2>&1
if exist "%LOG_TMP%" del /q "%LOG_TMP%"
echo Container removed.

endlocal
