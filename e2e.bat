@echo off
setlocal

echo ================================================================================
echo        SECUREBANK LIVE END-TO-END CLIENT-SERVER TEST SUITE                  
echo ================================================================================

rem Locate java
where java >nul 2>nul
if %errorlevel% equ 0 (
    set JAVA_CMD=java
) else (
    if exist "E:\java file\jdk-25.0.2\bin\java.exe" (
        set "JAVA_CMD=E:\java file\jdk-25.0.2\bin\java.exe"
    ) else (
        echo [ERROR] Java runtime 'java' was not found on PATH or E:\java file\jdk-25.0.2.
        exit /b 1
    )
)

"%JAVA_CMD%" -cp "lib\*;target\classes;target\test-classes" com.securebank.automation.EndToEndLiveServerTest
exit /b %errorlevel%
