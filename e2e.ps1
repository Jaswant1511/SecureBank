Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "       SECUREBANK LIVE END-TO-END CLIENT-SERVER TEST SUITE                  " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

$javaCmd = if (Test-Path "E:\java file\jdk-25.0.2\bin\java.exe") { "E:\java file\jdk-25.0.2\bin\java.exe" } elseif (Get-Command java -ErrorAction SilentlyContinue) { "java" } else {
    Write-Error "Java runtime not found on PATH or E:\java file\jdk-25.0.2"
    exit 1
}

& $javaCmd -cp "lib/*;target/classes;target/test-classes" com.securebank.automation.EndToEndLiveServerTest
exit $LASTEXITCODE
