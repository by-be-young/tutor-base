@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "WRAPPER_DIR=%~dp0"
set "PROPERTIES_FILE=%WRAPPER_DIR%.mvn\wrapper\maven-wrapper.properties"
if not exist "%PROPERTIES_FILE%" (
  echo Missing Maven Wrapper properties: %PROPERTIES_FILE% 1>&2
  exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /b /c:"distributionUrl=" "%PROPERTIES_FILE%"') do set "DISTRIBUTION_URL=%%B"
if "!DISTRIBUTION_URL!"=="" (
  echo distributionUrl is not configured in %PROPERTIES_FILE% 1>&2
  exit /b 1
)

for %%I in ("!DISTRIBUTION_URL!") do set "ARCHIVE_NAME=%%~nxI"
set "MAVEN_HOME_NAME=!ARCHIVE_NAME:-bin.zip=!"
if "!MAVEN_USER_HOME!"=="" (
  set "MAVEN_CACHE_ROOT=%USERPROFILE%\.m2"
) else (
  set "MAVEN_CACHE_ROOT=%MAVEN_USER_HOME%"
)
set "CACHE_ROOT=%MAVEN_CACHE_ROOT%\wrapper\dists\%MAVEN_HOME_NAME%"
set "MAVEN_HOME=%CACHE_ROOT%\%MAVEN_HOME_NAME%"

if not exist "!MAVEN_HOME!\bin\mvn.cmd" (
  if not exist "!CACHE_ROOT!" mkdir "!CACHE_ROOT!"
  set "ARCHIVE_PATH=!CACHE_ROOT!\!ARCHIVE_NAME!"
  if not exist "!ARCHIVE_PATH!" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '!DISTRIBUTION_URL!' -OutFile '!ARCHIVE_PATH!'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '!ARCHIVE_PATH!' -DestinationPath '!CACHE_ROOT!'"
)

call "!MAVEN_HOME!\bin\mvn.cmd" %*
endlocal & exit /b %ERRORLEVEL%
