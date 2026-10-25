# ===============================================================================
# SECUREBANK -- OPERATIONAL HEALTH & STATUS MONITOR
# ===============================================================================
$ErrorActionPreference = "Continue"

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "           SECUREBANK -- OPERATIONAL HEALTH AND STATUS MONITOR                 " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $baseDir

$pidFile = Join-Path $baseDir "securebank.pid"
$pidStatus = "NOT RUNNING"
$processPid = "NONE"
$portStatus = "AVAILABLE (Port 8080 free)"
$healthStatus = "UNREACHABLE (Backend offline)"
$dbStatus = "UNKNOWN"
$activeSessions = "0"

# 1. Check PID File
if (Test-Path $pidFile) {
    $savedPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Out-String).Trim()
    if ($savedPid -match '^\d+$') {
        $proc = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
        if ($proc) {
            $pidStatus = "RUNNING (Active background process)"
            $processPid = $savedPid
        } else {
            $pidStatus = "STALE PID FILE (Process $savedPid terminated)"
        }
    }
}

# 2. Check Port 8080
$tcpConn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($tcpConn) {
    $portStatus = "LISTENING (Bound to PID $($tcpConn.OwningProcess))"
    if ($processPid -eq "NONE") {
        $processPid = "$($tcpConn.OwningProcess)"
    }
}

# 3. Query Health Endpoint
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2 -ErrorAction Stop
    if ($res -and $res.status -eq "UP") {
        $healthStatus = "ONLINE (UP - Service responding normally)"
        $dbStatus = if ($res.database -eq "CONNECTED") { "CONNECTED (ACID Engine Active)" } else { "DISCONNECTED" }
        $activeSessions = "$($res.activeSessions)"
    }
} catch {
    # Remains UNREACHABLE
}

Write-Host "[COMPONENT STATUS REPORT]" -ForegroundColor Yellow
Write-Host "-------------------------------------------------------------------------------"
Write-Host "  Application URL     : http://localhost:8080"
Write-Host "  Background Process  : $pidStatus"
Write-Host "  Process Identifier  : $processPid"
Write-Host "  TCP Port 8080       : $portStatus"
Write-Host "  Health REST Endpoint: $healthStatus"
Write-Host "  Database Engine     : $dbStatus"
Write-Host "  Active Sessions     : $activeSessions"
Write-Host "  Primary Log File    : $baseDir\logs\securebank.log"
Write-Host "  Error Diagnostics   : $baseDir\logs\securebank_err.log"
Write-Host "-------------------------------------------------------------------------------"
Write-Host ""

if ($healthStatus -like "*ONLINE*") {
    Write-Host "[SUMMARY] Status: HEALTHY -- All services operating within normal parameters." -ForegroundColor Green
} else {
    Write-Host "[SUMMARY] Status: STOPPED or INITIALIZING -- Run SecureBank.bat to launch." -ForegroundColor Yellow
}
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""
