# ===============================================================================
# SECUREBANK -- SERVICE TERMINATION & SHUTDOWN ENGINE
# ===============================================================================
$ErrorActionPreference = "Continue"

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "           SECUREBANK -- SERVICE TERMINATION & SHUTDOWN ENGINE                 " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $baseDir

$pidFile = Join-Path $baseDir "securebank.pid"
$stopped = $false

# 1. Check PID File
if (Test-Path $pidFile) {
    $targetPidStr = (Get-Content $pidFile -ErrorAction SilentlyContinue | Out-String).Trim()
    if ($targetPidStr -match '^\d+$') {
        $targetPid = [int]$targetPidStr
        Write-Host "[SHUTDOWN] Located PID file (Target PID: $targetPid)..." -ForegroundColor Yellow

        $proc = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
        if ($proc) {
            # Security verification: Verify this process is really SecureBank
            $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $targetPid" -ErrorAction SilentlyContinue).CommandLine
            if ($cmdline -like "*BankWebServer*" -or $cmdline -like "*SecureBank*") {
                Write-Host "[SHUTDOWN] Terminating verified SecureBank process (PID: $targetPid)..." -ForegroundColor Yellow
                Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
                Start-Sleep -Milliseconds 500
                Write-Host "[SUCCESS] Terminated SecureBank process cleanly." -ForegroundColor Green
                $stopped = $true
            } else {
                Write-Host "[SECURITY ALERT] Target PID $targetPid does NOT match SecureBank signature." -ForegroundColor Red
                Write-Host "Command Line: $cmdline" -ForegroundColor DarkGray
                Write-Host "Aborting PID-based termination to protect unrelated applications." -ForegroundColor Red
            }
        } else {
            Write-Host "[INFO] Process $targetPid was already terminated." -ForegroundColor Gray
            $stopped = $true
        }
    }
    Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
}

# 2. Check Port 8080
$tcpConn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($tcpConn) {
    $portPid = $tcpConn.OwningProcess
    Write-Host "[SHUTDOWN] Found listener on TCP Port 8080 (PID: $portPid). Verifying signature..." -ForegroundColor Yellow
    $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $portPid" -ErrorAction SilentlyContinue).CommandLine
    if ($cmdline -like "*BankWebServer*" -or $cmdline -like "*SecureBank*") {
        Write-Host "[SHUTDOWN] Stopping verified SecureBank process on port 8080 (PID: $portPid)..." -ForegroundColor Yellow
        Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
        $stopped = $true
    } else {
        Write-Host "[SECURITY ALERT] Process $portPid on port 8080 is NOT SecureBank." -ForegroundColor Red
        Write-Host "Aborting termination to prevent disruption of other services." -ForegroundColor Red
    }
}

# 3. Verify Port 8080 is closed
Start-Sleep -Milliseconds 500
$finalCheck = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $finalCheck) {
    Write-Host ""
    Write-Host "[VERIFIED] Port 8080 is free. SecureBank backend is fully SHUT DOWN." -ForegroundColor Green
    Write-Host "===============================================================================" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "[WARNING] Port 8080 is still active by PID: $($finalCheck.OwningProcess)." -ForegroundColor Yellow
    Write-Host "===============================================================================" -ForegroundColor Cyan
}

Start-Sleep -Seconds 1
exit 0
