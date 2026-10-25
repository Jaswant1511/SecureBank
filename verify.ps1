$ErrorActionPreference = "Stop"

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "            SECUREBANK CAPSTONE -- COMPLETE AUTOMATED VERIFICATION             " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan

# 1. Compile
Write-Host "[VERIFY 1/4] Compiling project..." -ForegroundColor Yellow
& .\build.ps1

# 2. Test Suite
Write-Host "[VERIFY 2/4] Executing test verification suite..." -ForegroundColor Yellow
& .\test.ps1

# 3. Reconciliation
Write-Host "[VERIFY 3/4] Executing automated transaction reconciliation check..." -ForegroundColor Yellow
& .\reconcile.ps1

# 4. Backup Verification
Write-Host "[VERIFY 4/4] Executing automated database backup and archive verification..." -ForegroundColor Yellow
& .\backup.ps1

Write-Host "`n===============================================================================" -ForegroundColor Cyan
Write-Host "[SUCCESS] ALL VERIFICATION GATES PASSED (Exit Code: 0)" -ForegroundColor Green
Write-Host "===============================================================================" -ForegroundColor Cyan
exit 0
