@echo off
setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Phoenix Bot — VS Code Java Build
echo ========================================
echo.

REM VS Code Java location
set "JAVA_HOME=C:\Users\badbo\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
if not exist "%JAVA_HOME%\bin\java.exe" (
    for /d %%i in ("C:\Users\badbo\.vscode\extensions\redhat.java-*") do (
        if exist "%%i\jre\21.0.10-win32-x86_64\bin\java.exe" (
            set "JAVA_HOME=%%i\jre\21.0.10-win32-x86_64"
            goto :java_ready
        )
    )
)
:java_ready

echo [+] Using Java from VS Code:
echo     %JAVA_HOME%
echo.

REM Verify Java exists
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [!] Java not found at: %JAVA_HOME%
    pause
    exit /b 1
)

echo [+] Java version:
"%JAVA_HOME%\bin\java.exe" -version
echo.

REM Prefer project-local Maven wrapper; fall back to extension wrapper.
set "MAVEN_WRAPPER=%~dp0mvnw.cmd"
if not exist "%MAVEN_WRAPPER%" (
    set "MAVEN_WRAPPER=C:\Users\badbo\.vscode\extensions\vscjava.vscode-maven-0.45.3\resources\maven-wrapper\mvnw.cmd"
)
if not exist "%MAVEN_WRAPPER%" (
    echo [!] Maven wrapper not found
    echo.
    pause
    exit /b 1
)

echo [+] Maven wrapper found
cmd /c "%MAVEN_WRAPPER%" -version
echo.

REM Navigate to project
cd /d "%~dp0"
echo [*] Working directory: %CD%
echo.

REM Build
echo ========================================
echo  Compiling...
echo ========================================
echo.

cmd /c "%MAVEN_WRAPPER%" clean compile -e

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Compilation FAILED
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Packaging...
echo ========================================
echo.

cmd /c "%MAVEN_WRAPPER%" package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Packaging FAILED
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  BUILD SUCCESSFUL
echo ========================================
echo.
echo [+] JAR created: target\CommsBot-1.0-shaded.jar
echo.
echo [+] Next steps:
echo     1. Edit BotConfig.java with Discord token
echo     2. Run: mvn package
echo     3. Run: java -jar target\CommsBot-1.0-shaded.jar
echo.
pause
exit /b 0
