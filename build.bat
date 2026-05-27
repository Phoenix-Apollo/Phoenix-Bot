@echo off
REM Phoenix Bot Build & Run Script
REM This script attempts to find Java and compile the project

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Phoenix Bot — Build & Deploy Script
echo ========================================
echo.

REM Try to find Java in common locations
set JAVA_FOUND=0

echo [*] Searching for Java installations...
echo.

REM Check Program Files
if exist "C:\Program Files\Java" (
    for /d %%i in ("C:\Program Files\Java\*") do (
        if exist "%%i\bin\javac.exe" (
            set "JAVA_HOME=%%i"
            set JAVA_FOUND=1
            echo [+] Found Java at: %%i
            goto :found_java
        )
    )
)

REM Check Program Files (x86)
if exist "C:\Program Files (x86)\Java" (
    for /d %%i in ("C:\Program Files (x86)\Java\*") do (
        if exist "%%i\bin\javac.exe" (
            set "JAVA_HOME=%%i"
            set JAVA_FOUND=1
            echo [+] Found Java at: %%i
            goto :found_java
        )
    )
)

REM Check user AppData
if exist "%APPDATA%\Java" (
    for /d %%i in ("%APPDATA%\Java\*") do (
        if exist "%%i\bin\javac.exe" (
            set "JAVA_HOME=%%i"
            set JAVA_FOUND=1
            echo [+] Found Java at: %%i
            goto :found_java
        )
    )
)

REM Check PATH
echo [*] Checking system PATH...
where javac >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    for /f "delims=" %%i in ('where javac') do (
        set "JAVA_PATH=%%i"
        for %%a in (!JAVA_PATH!) do set "JAVA_PATH=!JAVA_PATH:%%~na=!"
    )
    if exist "!JAVA_PATH!..\java.exe" (
        set "JAVA_HOME=!JAVA_PATH!.."
        set JAVA_FOUND=1
        echo [+] Found Java in PATH
        goto :found_java
    )
)

:not_found_java
if %JAVA_FOUND% EQU 0 (
    echo.
    echo [!] Java NOT FOUND on this system
    echo.
    echo Please install Java 20+ from one of these sources:
    echo   1. https://www.oracle.com/java/technologies/downloads/
    echo   2. https://www.microsoft.com/openjdk
    echo   3. https://adoptium.net/
    echo.
    echo After installation:
    echo   1. Set JAVA_HOME environment variable to your JDK folder
    echo   2. Add JAVA_HOME\bin to your PATH
    echo   3. Run this script again
    echo.
    pause
    exit /b 1
)

:found_java
echo.
echo [+] Setting JAVA_HOME=%JAVA_HOME%
setx JAVA_HOME "%JAVA_HOME%" /M >nul 2>&1

echo [+] Java version:
"%JAVA_HOME%\bin\java.exe" -version
echo.

REM Check for Maven
echo [*] Checking for Maven...
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [!] Maven NOT FOUND
    echo.
    echo Please install Maven from: https://maven.apache.org/download.cgi
    echo And add the bin folder to your PATH
    echo.
    pause
    exit /b 1
)

echo [+] Maven found
mvn -version
echo.

REM Navigate to project
cd /d "%~dp0"
echo [*] Working directory: %CD%

REM Build
echo.
echo ========================================
echo  Building Phoenix Bot...
echo ========================================
echo.

call mvn clean compile -e

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Build FAILED
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Build SUCCESSFUL
echo ========================================
echo.
echo [+] You can now:
echo     1. Edit BotConfig.java with your Discord token
echo     2. Run: mvn package
echo     3. Run: java -jar target/CommsBot-1.0-shaded.jar
echo.
pause
exit /b 0

