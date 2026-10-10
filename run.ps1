# Lumina Online Library & PDF Reader Launcher (PowerShell)

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "   LUMINA ONLINE LIBRARY & UNIVERSAL PDF READER (JAVA SE CORE)     " -ForegroundColor Yellow
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = $PSScriptRoot
Set-Location $ScriptDir

# 1. Locate Java Compiler and Runtime
$Javac = $null
$Java = $null

# Check portable JDK
$PortableJdk = Get-ChildItem -Path "$ScriptDir\.jdk" -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path "$($_.FullName)\bin\javac.exe" } | Select-Object -First 1
if ($PortableJdk) {
    $Javac = "$($PortableJdk.FullName)\bin\javac.exe"
    $Java = "$($PortableJdk.FullName)\bin\java.exe"
}

# Check PATH
if (-not $Javac) {
    $JavacCmd = Get-Command javac -ErrorAction SilentlyContinue
    $JavaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($JavacCmd -and $JavaCmd) {
        $Javac = "javac"
        $Java = "java"
    }
}

# Check JAVA_HOME
if (-not $Javac -and $env:JAVA_HOME) {
    if (Test-Path "$env:JAVA_HOME\bin\javac.exe") {
        $Javac = "$env:JAVA_HOME\bin\javac.exe"
        $Java = "$env:JAVA_HOME\bin\java.exe"
    }
}

if (-not $Javac) {
    Write-Host "[ERROR] No Java Development Kit (JDK) found." -ForegroundColor Red
    Write-Host "Please install JDK 17+ or ensure it is in PATH." -ForegroundColor Yellow
    Exit 1
}

Write-Host "[OK] Using Java Compiler: $Javac" -ForegroundColor Green
Write-Host "[OK] Using Java Runtime:  $Java" -ForegroundColor Green
Write-Host ""

# 2. Build output folder
if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

# 3. Compile
Write-Host "[*] Compiling OnlineLibraryServer.java..." -ForegroundColor Cyan
& $Javac -encoding UTF-8 -d bin src/main/java/com/onlinelibrary/OnlineLibraryServer.java
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    Exit 1
}
Write-Host "[OK] Compilation successful!" -ForegroundColor Green
Write-Host ""

# 4. Open Browser
Start-Process "http://localhost:8080"

# 5. Start Server
Write-Host "[*] Lumina Online Library is running on: http://localhost:8080" -ForegroundColor Yellow
Write-Host "[*] Press Ctrl+C to terminate the server." -ForegroundColor Gray
Write-Host ""

& $Java -cp bin com.onlinelibrary.OnlineLibraryServer
