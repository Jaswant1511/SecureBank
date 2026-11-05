@echo off
setlocal

rem Locate java: Prioritize JDK 25
if exist "E:\java file\jdk-25.0.2\bin\java.exe" (
    set "JAVA_CMD=E:\java file\jdk-25.0.2\bin\java.exe"
) else (
    where java >nul 2>nul
    if %errorlevel% equ 0 (
        set JAVA_CMD=java
    ) else (
        echo [ERROR] Java runtime was not found on PATH or E:\java file\jdk-25.0.2.
        exit /b 1
    )
)

if not exist "target\test-classes" (
    echo [NOTICE] Build output missing. Running build.bat first...
    call build.bat
    if %errorlevel% neq 0 exit /b %errorlevel%
)

echo [TEST] Executing Comprehensive Automated Test Suite...
"%JAVA_CMD%" -cp "lib\*;target\classes;target\test-classes;." com.securebank.TestSuiteRunner
exit /b %errorlevel%
