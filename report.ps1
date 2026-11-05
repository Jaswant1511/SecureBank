$ErrorActionPreference = "Stop"

if (Test-Path "E:\java file\jdk-25.0.2\bin\java.exe") {
    $JavaCmd = "E:\java file\jdk-25.0.2\bin\java.exe"
} else {
    $javaCmdObj = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmdObj) {
        $JavaCmd = $javaCmdObj.Source
    } else {
        Write-Error "java runtime not found on PATH or E:\java file\jdk-25.0.2."
    }
}

if (-not (Test-Path "target\classes")) {
    & .\build.ps1
}

& $JavaCmd -cp "lib\*;target\classes;." com.securebank.SecureBankMain --report
exit $LASTEXITCODE
