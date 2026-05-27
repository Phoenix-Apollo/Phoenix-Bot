# Phoenix Bot — Build & Deploy Script (PowerShell)
# Searches for Java, sets up environment, and builds the project

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Phoenix Bot — Build & Deploy Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$javaFound = $false
$javaHome = $null

Write-Host "[*] Searching for Java installations..." -ForegroundColor Yellow
Write-Host ""

# Check Program Files
$javaDirs = @(
    "C:\Program Files\Java",
    "C:\Program Files (x86)\Java",
    "$env:APPDATA\Java",
    "C:\jdk*",
    "C:\Java*"
)

foreach ($dir in $javaDirs) {
    if (Test-Path $dir) {
        Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $javacPath = Join-Path $_.FullName "bin\javac.exe"
            if (Test-Path $javacPath) {
                $javaHome = $_.FullName
                $javaFound = $true
                Write-Host "[+] Found Java at: $javaHome" -ForegroundColor Green
            }
        }
    }
}

# Check PATH
if (-not $javaFound) {
    Write-Host "[*] Checking system PATH..." -ForegroundColor Yellow
    $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($javacCmd) {
        $javaHome = Split-Path (Split-Path $javacCmd.Source -Parent) -Parent
        $javaFound = $true
        Write-Host "[+] Found Java in PATH: $javaHome" -ForegroundColor Green
    }
}

if (-not $javaFound) {
    Write-Host ""
    Write-Host "[!] Java NOT FOUND on this system" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Java 20+ from one of these sources:" -ForegroundColor Yellow
    Write-Host "  1. https://www.oracle.com/java/technologies/downloads/" -ForegroundColor White
    Write-Host "  2. https://www.microsoft.com/openjdk" -ForegroundColor White
    Write-Host "  3. https://adoptium.net/" -ForegroundColor White
    Write-Host ""
    Write-Host "After installation:" -ForegroundColor Yellow
    Write-Host "  1. Set JAVA_HOME environment variable to your JDK folder" -ForegroundColor White
    Write-Host "  2. Add JAVA_HOME\bin to your PATH" -ForegroundColor White
    Write-Host "  3. Run this script again" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "[+] Setting JAVA_HOME=$javaHome" -ForegroundColor Green
[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
$env:JAVA_HOME = $javaHome

Write-Host "[+] Java version:" -ForegroundColor Green
& "$javaHome\bin\java.exe" -version
Write-Host ""

# Check for Maven
Write-Host "[*] Checking for Maven..." -ForegroundColor Yellow
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[!] Maven NOT FOUND" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Maven from: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    Write-Host "And add the bin folder to your PATH" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "[+] Maven found" -ForegroundColor Green
mvn -version
Write-Host ""

# Navigate to project
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectDir
Write-Host "[*] Working directory: $(Get-Location)" -ForegroundColor Yellow
Write-Host ""

# Build
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Building Phoenix Bot..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

mvn clean compile -e

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[!] Build FAILED" -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Build SUCCESSFUL" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "[+] Next steps:" -ForegroundColor Green
Write-Host "    1. Edit BotConfig.java with your Discord token" -ForegroundColor White
Write-Host "    2. Run: mvn package" -ForegroundColor White
Write-Host "    3. Run: java -jar target/CommsBot-1.0-shaded.jar" -ForegroundColor White
Write-Host ""
Read-Host "Press Enter to exit"
exit 0

