# ===============================================================================
# SECUREBANK -- ONE-CLICK BACKGROUND LAUNCH ENGINE
# ===============================================================================
$ErrorActionPreference = "Continue"

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "           SECUREBANK -- ENTERPRISE DIGITAL BANKING WEB PLATFORM               " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $baseDir

function Open-SecureBankBrowser {
    Write-Host "[BROWSER] Launching Google Chrome..." -ForegroundColor Cyan
    $chromePaths = @(
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )
    $chromeCmd = $null
    foreach ($cp in $chromePaths) {
        if (Test-Path $cp) {
            $chromeCmd = $cp
            break
        }
    }

    if ($chromeCmd) {
        Start-Process -FilePath $chromeCmd -ArgumentList "http://localhost:8080"
    } else {
        Write-Host "[NOTICE] Google Chrome not found at standard path. Launching default system browser..." -ForegroundColor Yellow
        Start-Process "http://localhost:8080"
    }

    Write-Host ""
    Write-Host "===============================================================================" -ForegroundColor Green
    Write-Host "  SecureBank is running in background. No console window remains open." -ForegroundColor Green
    Write-Host "  - To check server status : Run SecureBank-Status.bat" -ForegroundColor White
    Write-Host "  - To stop the server     : Run Stop-SecureBank.bat" -ForegroundColor White
    Write-Host "===============================================================================" -ForegroundColor Green
    Write-Host ""
    Start-Sleep -Seconds 2
    exit 0
}

# 1. Locate javaw.exe (Windows Java GUI launcher without console window)
$javawCmd = $null
$candidates = @(
    "E:\java file\jdk-25.0.2\bin\javaw.exe",
    "$env:JAVA_HOME\bin\javaw.exe"
)
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) {
        $javawCmd = $c
        break
    }
}
if (-not $javawCmd) {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javawCmd = $cmd.Source }
}
if (-not $javawCmd) {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javawCmd = $cmd.Source }
}

if (-not $javawCmd) {
    Write-Host "[ERROR] Java Runtime (javaw.exe or java.exe) was not found." -ForegroundColor Red
    Write-Host "Please ensure Java 17+ JDK is installed and JAVA_HOME is configured."
    Read-Host "Press Enter to exit..."
    exit 1
}

Write-Host "[INIT] Located Java Runtime: $javawCmd" -ForegroundColor DarkGray

# 2. Check if SecureBank is already running
$pidFile = Join-Path $baseDir "securebank.pid"
if (Test-Path $pidFile) {
    $existingPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Out-String).Trim()
    if ($existingPid -match '^\d+$') {
        $proc = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
        if ($proc) {
            # Check if /api/health responds
            $health = try {
                (Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2).status
            } catch { "" }
            if ($health -eq "UP") {
                Write-Host "[INFO] SecureBank is already running in background (PID: $existingPid)." -ForegroundColor Green
                Write-Host "[INFO] Directing to web interface..." -ForegroundColor Green
                Open-SecureBankBrowser
            }
        }
    }
    Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
}

# 3. Check if Port 8080 is in use by an unrelated application
$tcpConn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($tcpConn) {
    $owningPid = $tcpConn.OwningProcess
    # Check if this owning process is actually SecureBank
    $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $owningPid" -ErrorAction SilentlyContinue).CommandLine
    if ($cmdline -like "*BankWebServer*" -or $cmdline -like "*SecureBank*") {
        Write-Host "[INFO] SecureBank is already active on port 8080 (PID: $owningPid)." -ForegroundColor Green
        Set-Content -Path $pidFile -Value $owningPid
        Open-SecureBankBrowser
    } else {
        Write-Host "[ERROR] Port 8080 is already in use by process PID: $owningPid." -ForegroundColor Red
        Write-Host "Please free port 8080 or stop the conflicting application before starting SecureBank."
        Read-Host "Press Enter to exit..."
        exit 1
    }
}

# 4. Ensure logs directory exists
$logsDir = Join-Path $baseDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# 5. Check if project is compiled
$classFile = Join-Path $baseDir "target\classes\com\securebank\ui\web\BankWebServer.class"
if (-not (Test-Path $classFile)) {
    Write-Host "[BUILD] Application classes not found. Invoking build engine..." -ForegroundColor Yellow
    & "$baseDir\build.bat"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Build failed. Cannot start application." -ForegroundColor Red
        Read-Host "Press Enter to exit..."
        exit 1
    }
}

# 6. Start Java backend silently in background using javaw.exe
Write-Host "[LAUNCH] Starting SecureBank backend engine in background (console hidden)..." -ForegroundColor Cyan

$proc = Start-Process -FilePath $javawCmd `
    -ArgumentList @("-cp", "lib/*;target/classes;.", "com.securebank.ui.web.BankWebServer", "--no-browser") `
    -WorkingDirectory $baseDir `
    -WindowStyle Hidden `
    -PassThru

if (-not $proc -or -not $proc.Id) {
    Write-Host "[ERROR] Failed to start Java backend process." -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

Set-Content -Path $pidFile -Value $proc.Id
Write-Host "[LAUNCH] Process initiated successfully (PID: $($proc.Id))." -ForegroundColor Green

# 7. Wait for backend to be healthy (polling http://localhost:8080/api/health)
Write-Host "[STATUS] Waiting for SecureBank core engine to become ready..." -ForegroundColor Yellow
$ready = $false
for ($i = 1; $i -le 25; $i++) {
    Start-Sleep -Milliseconds 800
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 2 -ErrorAction Stop
        if ($res.status -eq "UP") {
            $ready = $true
            break
        }
    } catch {
        # continue waiting
    }
}

if (-not $ready) {
    Write-Host "[ERROR] SecureBank failed to become ready within 20 seconds." -ForegroundColor Red
    Write-Host "Please inspect diagnostic logs at logs\securebank.log and logs\securebank_err.log." -ForegroundColor Yellow
    Read-Host "Press Enter to exit..."
    exit 1
}

Write-Host "[READY] Backend is ONLINE and healthy at http://localhost:8080." -ForegroundColor Green
Write-Host "[READY] Diagnostics logged to: logs\securebank.log" -ForegroundColor DarkGray

# 8. Open Google Chrome automatically
Open-SecureBankBrowser
