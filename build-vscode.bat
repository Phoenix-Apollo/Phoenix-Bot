@echo off
REM Use VS Code's bundled Java for building
REM This assumes you have the Extension Pack for Java installed in VS Code

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Phoenix Bot — VS Code Build Script
echo ========================================
echo.

REM VS Code typically stores Java in one of these locations:
REM 1. %LOCALAPPDATA%\Programs\Microsoft VS Code\jdks
REM 2. %LOCALAPPDATA%\jdks
REM 3. Via redhat.java extension

set "VS_CODE_PATH=%LOCALAPPDATA%\Programs\Microsoft VS Code"
set "VSCODE_JAVA_PATH=%LOCALAPPDATA%\jdks"

echo [*] Checking VS Code Java...

REM Check if VS Code's Java exists
if exist "%VS_CODE_PATH%" (
    echo [+] VS Code found at: %VS_CODE_PATH%
)

if exist "%VSCODE_JAVA_PATH%" (
    echo [+] VS Code JDKs folder found
    dir /b "%VSCODE_JAVA_PATH%"
)

REM Try to use VS Code's integrated terminal Java
for /f "delims=" %%i in ('where javac 2^>nul') do (
    set "JAVAC_PATH=%%i"
    echo [+] javac found at: %%i
)

if defined JAVAC_PATH (
    echo [+] Using Java from PATH
    call java -version
    call mvn clean compile
    exit /b 0
)

echo.
echo [!] Java not found via PATH or VS Code
echo.
echo To fix:
echo   1. Open VS Code
echo   2. Install: Extension Pack for Java (Microsoft)
echo   3. It will automatically download and configure Java
echo   4. Close and reopen terminal
echo   5. Run this script again
echo.
pause
exit /b 1

