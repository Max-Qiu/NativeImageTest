@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 exit /b 1

echo.
echo === Build Native Docker image ===
call mvn -Pnative clean spring-boot:build-image -DskipTests %*
if errorlevel 1 (
  echo Native image build failed.
  exit /b 1
)

echo.
echo Native Docker image built successfully.
echo Run run-native-test.bat to test the image.
endlocal & exit /b 0
