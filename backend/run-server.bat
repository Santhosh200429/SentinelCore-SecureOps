@echo off
setlocal
cd /d "%~dp0"

echo ========================================================================
echo SentinelCore SecureOps - Starting Backend Server
echo ========================================================================

if not exist ".env" (
    echo [ERROR] backend\.env was not found.
    echo Copy backend\.env.example to backend\.env and configure your values.
    exit /b 1
)

echo [OK] Loading configuration from backend\.env
echo [OK] Live mode is configured in .env
findstr /B "SMTP_USERNAME=" .env >nul 2>&1
if errorlevel 1 echo [WARN] SMTP_USERNAME is not present in .env. Email delivery will be unavailable.
echo Launching Spring Boot on the configured port...

call mvnw.cmd spring-boot:run
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Backend stopped with exit code %EXIT_CODE%.
)
exit /b %EXIT_CODE%
