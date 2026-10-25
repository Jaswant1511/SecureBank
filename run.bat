@echo off
setlocal

rem Locate java
where java >nul 2>nul
if %errorlevel% equ 0 (
    set JAVA_CMD=java
) else (
    if exist "E:\java file\jdk-25.0.2\bin\java.exe" (
        set "JAVA_CMD=E:\java file\jdk-25.0.2\bin\java.exe"
    ) else (
        echo [ERROR] Java runtime was not found on PATH or E:\java file\jdk-25.0.2.
        exit /b 1
    )
)

if not exist "target\classes" (
    echo [NOTICE] Application not built yet. Running build.bat first...
    call build.bat
    if %errorlevel% neq 0 exit /b %errorlevel%
)

echo [LAUNCH] Starting SecureBank Core Banking System...
"%JAVA_CMD%" -cp "lib\*;target\classes;." com.securebank.SecureBankMain %*
exit /b %errorlevel%
