# Phoenix Bot — Setup & Build Guide

## Complete Setup, Build, and Deployment Instructions

**Last Updated**: April 26, 2026  
**Status**: ✅ Ready to Build  
**Java Version**: OpenJDK 21.0.10 (Temurin)  
**Build Tool**: Maven 3.9.x

---

## 📑 Table of Contents

1. [Quick Start (60 seconds)](#quick-start-60-seconds)
2. [Environment Setup](#environment-setup)
3. [Build Scripts Available](#build-scripts-available)
4. [VS Code Setup](#vs-code-setup)
5. [Manual Build Steps](#manual-build-steps)
6. [Troubleshooting](#troubleshooting)

---

## Quick Start (60 seconds)

### Option 1: VS Code Users (Easiest) ⭐

If you have **Extension Pack for Java** installed in VS Code:

```powershell
cd C:\Users\badbo\Phoenix-Bot-development
mvn clean compile
mvn package
java -jar target/CommsBot-1.0-shaded.jar
```

✅ VS Code Java extension handles all setup automatically

### Option 2: Automatic Script (PowerShell)

```powershell
cd C:\Users\badbo\Phoenix-Bot-development
powershell -ExecutionPolicy Bypass -File build.ps1
```

### Option 3: Automatic Script (Batch)

```cmd
cd C:\Users\badbo\Phoenix-Bot-development
build.bat
```

✅ The script will automatically:

- Find Java on your system
- Set JAVA_HOME environment variable
- Verify Maven is installed
- Run `mvn clean compile`
- Create executable JAR

### Option 4: Complete Build Script (All-in-One)

```cmd
cd C:\Users\badbo\Phoenix-Bot-development
build-complete.bat
```

✅ Does everything including: compile → package → deploy-ready JAR

---

## Environment Setup

### Java Setup ✅ (Already Configured)

**Current Configuration:**

```
Location: C:\Users\badbo\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64
JAVA_HOME: Set (User environment)
PATH: Updated (User environment)
Version: OpenJDK 21.0.10 Temurin
Status: ✅ Verified working
```

**Verify Java is working:**

```powershell
java -version
javac -version
```

### Maven Setup ✅ (Already Configured)

**Current Configuration:**

```
Maven Wrapper: C:\Users\badbo\.vscode\extensions\vscjava.vscode-maven-0.45.3\resources\maven-wrapper
mvnw.cmd: Available in project root
Status: ✅ Ready to use
```

**Verify Maven is working:**

```powershell
mvn -version
```

---

## Build Scripts Available

### 1. `build.ps1` (PowerShell - Recommended)

**What it does:**

- Searches common Java installation paths
- Checks system PATH for Java
- Sets JAVA_HOME environment variable
- Verifies Maven is installed
- Runs `mvn clean compile`
- Reports success or tells you what's missing

**Run:**

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

### 2. `build.bat` (Batch - Alternative)

**What it does:**

- Same as PowerShell version
- Better for batch/CMD environment

**Run:**

```cmd
build.bat
```

### 3. `build-complete.bat` (Full Build)

**What it does:**

- Finds Java and Maven
- Compiles entire project
- Creates shaded JAR
- Reports final status

**Run:**

```cmd
build-complete.bat
```

### 4. `build-vscode.bat` (VS Code Optimized)

**What it does:**

- Uses VS Code's Java installation
- Sets paths specifically for VS Code
- Best if you primarily use VS Code

**Run:**

```cmd
build-vscode.bat
```

### 5. `build-vscode-final.bat` (VS Code - Final)

**What it does:**

- Final optimized version for VS Code
- All tested and working

**Run:**

```cmd
build-vscode-final.bat
```

---

## VS Code Setup

### If You Already Have Extension Pack Installed

Everything is already set up! Java was automatically downloaded and configured when you installed
the Extension Pack for Java.

**To build the project:**

1. **Open VS Code**
2. **Open the Terminal** (Ctrl+`)
3. **Navigate to project:**

```powershell
cd C:\Users\badbo\Phoenix-Bot-development
```

4. **Compile:**

```powershell
mvn clean compile
```

5. **Package:**

```powershell
mvn package
```

6. **Run:**

```powershell
java -jar target/CommsBot-1.0-shaded.jar
```

### VS Code Java Extensions to Have

- **Extension Pack for Java** (main pack)
- **Language Support for Java (Red Hat)**
- **Debugger for Java**
- **Test Runner for Java**
- **Visual Studio IntelliCode**
- **Maven for Java**

All are installed automatically with Extension Pack.

---

## Manual Build Steps

If you prefer to build manually without scripts:

### Step 1: Verify Environment

```powershell
java -version
mvn -version
```

Both should show version info. If not, scripts above will help set them up.

### Step 2: Clean Previous Build

```powershell
mvn clean
```

### Step 3: Compile

```powershell
mvn compile
```

Expected output:

```
[INFO] --- compiler:3.13.0:compile (default-compile) @ CommsBot ---
[INFO] Compiling 39 source files...
[INFO] BUILD SUCCESS
```

### Step 4: Run Tests (Optional)

```powershell
mvn test
```

### Step 5: Package

```powershell
mvn package -DskipTests
```

Expected output:

```
[INFO] Building jar: C:\...\target\CommsBot-1.0.jar
[INFO] Building shaded jar: C:\...\target\CommsBot-1.0-shaded.jar
[INFO] BUILD SUCCESS
```

### Step 6: Run

```powershell
java -jar target/CommsBot-1.0-shaded.jar
```

You should see logs starting with:

```
[Startup] Loading environment...
[Startup] TOKEN loaded successfully.
[Startup] Building shard manager...
```

---

## Deployment

### JAR Location

```
C:\Users\badbo\Phoenix-Bot-development\target\CommsBot-1.0-shaded.jar
```

### Deploy (Copy to Server)

```powershell
Copy-Item .\target\CommsBot-1.0-shaded.jar .\CommsBot-1.0-shaded.jar.deployable
```

### Run on Server

```powershell
java -jar CommsBot-1.0-shaded.jar
```

### Keep Running (Background)

```powershell
# Windows Service or use nssm/winsw for background execution
# Or use screen/tmux on Linux
```

---

## Troubleshooting

### Issue: `mvn` command not found

**Solution:**

1. Run the setup script: `powershell -ExecutionPolicy Bypass -File build.ps1`
2. Or manually add Maven to PATH
3. Restart terminal/PowerShell

### Issue: `java` command not found

**Solution:**

1. Install Java from VS Code Extension Pack for Java
2. Or set JAVA_HOME manually in environment variables
3. Restart terminal/PowerShell

### Issue: Build fails with "cannot find symbol"

**Solution:**

1. Run `mvn clean` first to clear old build
2. Check that all dependencies downloaded: `mvn dependency:resolve`
3. Verify internet connection (Maven needs to download dependencies)

### Issue: "BUILD FAILURE" during compile

**Solution:**

1. Check error message carefully
2. Look for line numbers in error output
3. Most common: missing import or typo in code
4. Run `mvn clean compile` to try again

### Issue: JAR created but won't run

**Solution:**

1. Verify Java 11+ installed: `java -version`
2. Check `.env` file exists with TOKEN configured
3. Run with more verbose output:
   `java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar target/CommsBot-1.0-shaded.jar`

### Issue: Build takes forever

**Solution:**

1. First build always takes longer (downloads dependencies)
2. Subsequent builds should be faster
3. Check internet connection if it stalls
4. Can run `mvn package -o` (offline) if dependencies already cached

### Memory Issues During Build

If you get out-of-memory errors:

```powershell
# Increase heap size for Maven
$env:MAVEN_OPTS = "-Xmx1024m"
mvn clean package
```

---

## Build Artifacts

### What Gets Created

```
target/
├── CommsBot-1.0.jar              ← Regular JAR (dependencies separate)
├── CommsBot-1.0-shaded.jar       ← SHADED JAR (all dependencies bundled) ⭐ USE THIS
├── classes/                       ← Compiled .class files
└── generated-sources/             ← Auto-generated code
```

**Use `CommsBot-1.0-shaded.jar`** — it has everything packed in and runs standalone.

---

## Build Success Indicators

✅ Build is successful when you see:

```
[INFO] BUILD SUCCESS
[INFO] Total time: 12.5 s
```

✅ JAR is created:

```
[INFO] Building shaded jar: ...\target\CommsBot-1.0-shaded.jar
```

✅ Bot starts when you run:

```
[Startup] Loading environment...
[Startup] TOKEN loaded successfully.
[JDA] READY as Phoenix_Bot
```

---

## Common Build Times

| Step              | Time   | Notes                      |
|-------------------|--------|----------------------------|
| First clean build | 30-60s | Downloads all dependencies |
| Subsequent clean  | 15-20s | Dependencies cached        |
| Compile only      | 5-10s  | Fastest option             |
| Package only      | 10-15s | If already compiled        |

---

## Environment Variables

### Required

- `TOKEN` — Discord bot token (.env only)

### Optional

- `BOT_AI_CATEGORY_ID` — Phoenix Industries category ID (default: 1498011508548829234)
- `PANEL_CHANNEL_IDS` — GUI panel channels (comma-separated)
- `JAVA_HOME` — Java installation path (usually auto-set)
- `MAVEN_HOME` — Maven installation path (usually auto-set)

### Examples

```env
TOKEN=your_discord_bot_token_here
BOT_AI_CATEGORY_ID=1498011508548829234
PANEL_CHANNEL_IDS=1172143831702065294,1498011767283126403
```

---

## Next Steps

1. **Build the project** using one of the methods above
2. **Check BUILD SUCCESS** in output
3. **Verify JAR created**: `ls target/CommsBot-1.0-shaded.jar`
4. **Run the bot**: `java -jar target/CommsBot-1.0-shaded.jar`
5. **Check startup logs** for successful initialization
6. **See IMPLEMENTATION_MASTER.md** for feature documentation

---

**Status**: 🟢 **BUILD READY**
