@echo off
REM Phoenix Bot — Complete VS Code Java + Maven Setup
REM Uses Java and Maven from VS Code Extension Pack

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Phoenix Bot — VS Code Setup
echo ========================================
echo.

REM Prefer the current VS Code Java extension path; fall back to any available Red Hat Java runtime.
echo [*] Finding VS Code Java installations...

REM Check multiple possible Java versions from Red Hat Java extensions
set "JAVA_FOUND=0"
set "JAVA_HOME=C:\Users\badbo\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_FOUND=1"
    echo [+] Found Java: %JAVA_HOME%
) else (
    for /d %%i in ("C:\Users\badbo\.vscode\extensions\redhat.java-*") do (
        if exist "%%i\jre\21.0.10-win32-x86_64\bin\java.exe" (
            set "JAVA_HOME=%%i\jre\21.0.10-win32-x86_64"
            set "JAVA_FOUND=1"
            echo [+] Found Java (fallback): !JAVA_HOME!
            goto :java_found
        )
    )
)

:java_found
if %JAVA_FOUND% EQU 0 (
    echo [!] Java not found
    pause
    exit /b 1
)

REM Use project-local Maven wrapper first to avoid extension path coupling.
set "MAVEN_WRAPPER=%~dp0mvnw.cmd"
if not exist "%MAVEN_WRAPPER%" (
    set "MAVEN_WRAPPER=C:\Users\badbo\.vscode\extensions\vscjava.vscode-maven-0.45.3\resources\maven-wrapper\mvnw.cmd"
)
if not exist "%MAVEN_WRAPPER%" (
    echo [!] Maven wrapper not found
    pause
    exit /b 1
)

echo [+] Found Maven wrapper: %MAVEN_WRAPPER%
echo.

REM Set environment
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [+] Java:
"%JAVA_HOME%\bin\java.exe" -version
echo.

echo [+] Maven:
cmd /c "%MAVEN_WRAPPER%" -version
echo.

REM Navigate to project
cd /d "%~dp0"

REM Run Maven via wrapper
echo ========================================
echo  Building Phoenix Bot...
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
echo  BUILD SUCCESSFUL!
echo ========================================
echo.
echo [+] JAR created: target\CommsBot-1.0-shaded.jar
echo.
echo [+] Next steps:
echo     1. Edit BotConfig.java with your Discord token
echo     2. Run: build-complete.bat (to rebuild with token)
echo     3. Run: java -jar target\CommsBot-1.0-shaded.jar
echo.
pause
exit /b 0

