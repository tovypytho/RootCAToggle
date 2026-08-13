@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 8.9 is required on Windows. GitHub Actions is already configured to install it automatically.
exit /b 1
