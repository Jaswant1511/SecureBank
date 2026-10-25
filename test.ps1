# SecureBank Test Runner for PowerShell
$ErrorActionPreference = "Stop"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    if (Test-Path "E:\java file\jdk-25.0.2\bin\java.exe") {
        $javaCmd = "E:\java file\jdk-25.0.2\bin\java.exe"
    } else {
        Write-Error "java runtime not found on PATH or E:\java file\jdk-25.0.2."
    }
} else {
    $javaCmd = $javaCmd.Source
}

if (-not (Test-Path "target\test-classes")) {
    & .\build.ps1
}

Write-Host "[TEST] Running SecureBank Automated Test Suite..." -ForegroundColor Cyan
& $javaCmd -cp "lib/*;target/classes;target/test-classes;." com.securebank.TestSuiteRunner
