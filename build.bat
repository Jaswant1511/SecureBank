@echo off
setlocal enabledelayedexpansion

echo ===============================================================================
echo             SECUREBANK CAPSTONE — COMPILATION AND BUILD ENGINE                   
echo ===============================================================================

rem Locate javac: Prioritize JDK 25
if exist "E:\java file\jdk-25.0.2\bin\javac.exe" (
    set "JAVAC_CMD=E:\java file\jdk-25.0.2\bin\javac.exe"
) else (
    where javac >nul 2>nul
    if %errorlevel% equ 0 (
        set JAVAC_CMD=javac
    ) else (
        echo [ERROR] JDK compiler 'javac' was not found on PATH or E:\java file\jdk-25.0.2.
        echo Please ensure Java 25+ JDK is installed.
        exit /b 1
    )
)

echo [BUILD] Using Java Compiler: %JAVAC_CMD%

rem Create build output directories
if not exist "target\classes" mkdir "target\classes"
if not exist "target\test-classes" mkdir "target\test-classes"

echo [BUILD] Compiling root and modular main source files...
dir /s /b "src\main\java\*.java" > "target\sources_main.txt"
"%JAVAC_CMD%" -encoding UTF-8 -d "target\classes" -cp "lib\*" @"target\sources_main.txt"
if %errorlevel% neq 0 (
    echo [ERROR] Main source compilation failed!
    exit /b %errorlevel%
)

echo [BUILD] Copying resources...
if exist "src\main\resources" xcopy /E /I /Y "src\main\resources\*" "target\classes\" >nul

echo [BUILD] Compiling automated test suite...
dir /s /b "src\test\java\*.java" > "target\sources_test.txt"
"%JAVAC_CMD%" -encoding UTF-8 -d "target\test-classes" -cp "lib\*;target\classes" @"target\sources_test.txt"
if %errorlevel% neq 0 (
    echo [ERROR] Test suite compilation failed!
    exit /b %errorlevel%
)

echo [SUCCESS] Compilation completed successfully! All classes generated in target/
echo ===============================================================================
exit /b 0
