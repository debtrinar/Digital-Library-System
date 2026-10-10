@echo off
setlocal enabledelayedexpansion
title Lumina Online Library & PDF Reader Server

echo ==================================================================
echo   LUMINA ONLINE LIBRARY & UNIVERSAL PDF READER (JAVA SE CORE)
echo ==================================================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: 1. Detect Java Compiler & Runtime
set "JAVA_EXE="
set "JAVAC_EXE="

:: Check portable JDK in .jdk
for /d %%D in ("%SCRIPT_DIR%.jdk\*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVAC_EXE=%%D\bin\javac.exe"
        set "JAVA_EXE=%%D\bin\java.exe"
    )
)

:: Fallback to system PATH
if not defined JAVAC_EXE (
    where javac >nul 2>&1
    if not errorlevel 1 (
        set "JAVAC_EXE=javac"
        set "JAVA_EXE=java"
    )
)

:: Fallback to JAVA_HOME
if not defined JAVAC_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\javac.exe" (
            set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
            set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        )
    )
)

if not defined JAVAC_EXE (
    echo [ERROR] No Java Development Kit (JDK 17+) was detected.
    echo Please install JDK or run setup.
    pause
    exit /b 1
)

echo [OK] Using Java Compiler: "!JAVAC_EXE!"
echo [OK] Using Java Runtime:  "!JAVA_EXE!"
echo.

:: 2. Create output build folder
if not exist "bin" mkdir "bin"

:: 3. Compile Java Source
echo [*] Compiling Online Library Server...
"!JAVAC_EXE!" -encoding UTF-8 -d bin src\main\java\com\onlinelibrary\OnlineLibraryServer.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)
echo [OK] Compilation successful!
echo.

:: 4. Launch browser after a brief delay
start "" cmd /c "timeout /t 2 >nul & start http://localhost:8080"

:: 5. Start Server
echo [*] Launching Lumina Online Library on http://localhost:8080 ...
echo [*] Press Ctrl+C to stop the server at any time.
echo.
"!JAVA_EXE!" -cp bin com.onlinelibrary.OnlineLibraryServer

pause
