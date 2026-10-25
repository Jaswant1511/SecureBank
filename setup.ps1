$ErrorActionPreference = "Stop"

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "              SECUREBANK CAPSTONE -- AUTOMATED ENVIRONMENT SETUP               " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan

# 1. Check Java JDK
Write-Host "[SETUP 1/6] Checking Java Environment..." -ForegroundColor Yellow
$javacCmdObj = Get-Command javac -ErrorAction SilentlyContinue
$javaCmdObj = Get-Command java -ErrorAction SilentlyContinue

if ($javacCmdObj) {
    $JavacCmd = $javacCmdObj.Source
} elseif (Test-Path "E:\java file\jdk-25.0.2\bin\javac.exe") {
    $JavacCmd = "E:\java file\jdk-25.0.2\bin\javac.exe"
}

if ($javaCmdObj) {
    $JavaCmd = $javaCmdObj.Source
} elseif (Test-Path "E:\java file\jdk-25.0.2\bin\java.exe") {
    $JavaCmd = "E:\java file\jdk-25.0.2\bin\java.exe"
}

if (-not $JavacCmd) {
    Write-Error "JDK compiler not found. Ensure Java 17+ is installed."
    exit 1
}
Write-Host " - Found compiler: $JavacCmd" -ForegroundColor Green

# 2. Check Dependencies
Write-Host "[SETUP 2/6] Verifying declared JAR dependencies in lib/..." -ForegroundColor Yellow
$jars = Get-ChildItem "lib\*.jar" -ErrorAction SilentlyContinue
foreach ($j in $jars) {
    Write-Host " - Found library: $($j.Name)" -ForegroundColor Green
}

# 3. Build Application
Write-Host "[SETUP 3/6] Compiling application source code..." -ForegroundColor Yellow
& .\build.ps1

# 4. Run Tests
Write-Host "[SETUP 4/6] Running automated test verification suite..." -ForegroundColor Yellow
& .\test.ps1

# 5. Reconcile
Write-Host "[SETUP 5/6] Verifying database schema and reconciling initial ledgers..." -ForegroundColor Yellow
& .\reconcile.ps1

Write-Host "`n[SETUP 6/6] Automated setup completed successfully!" -ForegroundColor Green
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host " Ready to launch!" -ForegroundColor White
Write-Host " - Web App & GUI: .\run.ps1" -ForegroundColor Gray
Write-Host " - Terminal CLI:  .\run.ps1 --cli" -ForegroundColor Gray
Write-Host " - Reconcile:     .\reconcile.ps1" -ForegroundColor Gray
Write-Host " - Reports:       .\report.ps1" -ForegroundColor Gray
Write-Host " - Backup:        .\backup.ps1" -ForegroundColor Gray
Write-Host "===============================================================================" -ForegroundColor Cyan
exit 0
