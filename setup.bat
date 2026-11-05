@echo off
setlocal

echo ===============================================================================
echo              SECUREBANK CAPSTONE -- AUTOMATED ENVIRONMENT SETUP
echo ===============================================================================

rem 1. Check Java JDK Prerequisite
echo [SETUP 1/6] Checking Java Environment...
if exist "E:\java file\jdk-25.0.2\bin\javac.exe" (
    set "JAVAC_CMD=E:\java file\jdk-25.0.2\bin\javac.exe"
    set "JAVA_CMD=E:\java file\jdk-25.0.2\bin\java.exe"
) else (
    where javac >nul 2>nul
    if %errorlevel% equ 0 (
        set "JAVAC_CMD=javac"
        set "JAVA_CMD=java"
    ) else (
        echo [ERROR] JDK was not found. Please install JDK 25+ or configure PATH.
        exit /b 1
    )
)
echo  - Found compiler: %JAVAC_CMD%

rem 2. Validate declared dependencies
echo [SETUP 2/6] Verifying declared JAR dependencies in lib/...
if not exist "lib" (
    echo [ERROR] lib/ directory missing!
    exit /b 1
)
for %%F in (mysql-connector-j-*.jar h2-*.jar jbcrypt-*.jar junit-platform-console-standalone-*.jar) do (
    if exist "lib\%%F" echo  - Found library: %%F
)

rem 3. Build Application
echo [SETUP 3/6] Compiling application source code...
call build.bat
if %errorlevel% neq 0 (
    echo [ERROR] Build compilation failed.
    exit /b %errorlevel%
)

rem 4. Execute Full Automated Test Suite
echo [SETUP 4/6] Running automated test verification suite...
call test.bat
if %errorlevel% neq 0 (
    echo [ERROR] Verification test suite failed.
    exit /b %errorlevel%
)

rem 5. Execute Automated Database Ledger Reconciliation
echo [SETUP 5/6] Verifying database schema and reconciling initial ledgers...
call reconcile.bat
if %errorlevel% neq 0 (
    echo [ERROR] Initial reconciliation audit failed.
    exit /b %errorlevel%
)

rem 6. Complete
echo [SETUP 6/6] Automated setup completed successfully!
echo ===============================================================================
echo  Ready to launch!
echo  - To start Web App + GUI:    run.bat
echo  - To start Authenticated CLI: run.bat --cli
echo  - To run tests:               test.bat
echo  - To audit ledgers:           reconcile.bat
echo  - To generate reports:        report.bat
echo  - To backup database:         backup.bat
echo ===============================================================================
exit /b 0
