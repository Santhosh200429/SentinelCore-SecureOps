@echo off
setlocal
if "%SENTINELCORE_URL%"=="" set /p SENTINELCORE_URL=SentinelCore backend URL: 
if "%SENTINELCORE_AGENT_TOKEN%"=="" set /p SENTINELCORE_AGENT_TOKEN=Device token: 
if "%SENTINELCORE_URL%"=="" goto :error
if "%SENTINELCORE_AGENT_TOKEN%"=="" goto :error
java -jar sentinelcore-agent-1.0.0.jar
exit /b %ERRORLEVEL%
:error
echo Missing backend URL or device token.
exit /b 1
