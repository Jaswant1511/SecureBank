# SecureBank Build Script for PowerShell
$ErrorActionPreference = "Stop"

if (Test-Path "E:\java file\jdk-25.0.2\bin\javac.exe") {
    $javacCmd = "E:\java file\jdk-25.0.2\bin\javac.exe"
} else {
    $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($javacCmd) {
        $javacCmd = $javacCmd.Source
    } else {
        Write-Error "Java 25+ javac compiler not found on PATH or E:\java file\jdk-25.0.2."
    }
}

Write-Host "[BUILD] Using Java Compiler: $javacCmd" -ForegroundColor Cyan

New-Item -ItemType Directory -Force -Path "target\classes", "target\test-classes" | Out-Null

$mainSources = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
Write-Host "[BUILD] Compiling main application sources..." -ForegroundColor Yellow
& $javacCmd -encoding UTF-8 -d "target\classes" -cp "lib/*" $mainSources

if (Test-Path "src\main\resources") {
    Copy-Item -Path "src\main\resources\*" -Destination "target\classes\" -Recurse -Force
}

$testSources = Get-ChildItem -Path "src\test\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
Write-Host "[BUILD] Compiling automated test sources..." -ForegroundColor Yellow
& $javacCmd -encoding UTF-8 -d "target\test-classes" -cp "lib/*;target/classes" $testSources

Write-Host "[SUCCESS] Compilation completed successfully!" -ForegroundColor Green
