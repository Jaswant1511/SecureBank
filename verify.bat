@echo off
setlocal

echo ===============================================================================
echo            SECUREBANK CAPSTONE -- COMPLETE AUTOMATED VERIFICATION
echo ===============================================================================

rem 1. Compile
echo [VERIFY 1/4] Compiling project...
call build.bat
if %errorlevel% neq 0 (
    echo [ERROR] Verification build failed!
    exit /b %errorlevel%
)

rem 2. Test Suite
echo [VERIFY 2/4] Executing test verification suite (Unit, Integration, Security, Concurrency, Automation)...
call test.bat
if %errorlevel% neq 0 (
    echo [ERROR] Verification tests failed!
    exit /b %errorlevel%
)

rem 3. Reconciliation
echo [VERIFY 3/4] Executing automated transaction reconciliation check...
call reconcile.bat
if %errorlevel% neq 0 (
    echo [ERROR] Reconciliation audit failed!
    exit /b %errorlevel%
)

rem 4. Backup & Disaster Recovery Verification
echo [VERIFY 4/4] Executing automated database backup and archive verification...
call backup.bat
if %errorlevel% neq 0 (
    echo [ERROR] Backup verification failed!
    exit /b %errorlevel%
)

echo ===============================================================================
echo [SUCCESS] ALL VERIFICATION GATES PASSED (Exit Code: 0)
echo ===============================================================================
exit /b 0
