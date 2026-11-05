# SecureBank Test Runner for PowerShell
$ErrorActionPreference = "Stop"

if (Test-Path "E:\java file\jdk-25.0.2\bin\java.exe") {
    $javaCmd = "E:\java file\jdk-25.0.2\bin\java.exe"
} else {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $javaCmd = $javaCmd.Source
    } else {
        Write-Error "java runtime not found on PATH or E:\java file\jdk-25.0.2."
    }
}

if (-not (Test-Path "target\test-classes")) {
    & .\build.ps1
}

Write-Host "[TEST] Running SecureBank Automated Test Suite..." -ForegroundColor Cyan
& $javaCmd -cp "lib/*;target/classes;target/test-classes;." com.securebank.TestSuiteRunner
