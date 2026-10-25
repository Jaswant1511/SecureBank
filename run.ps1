# SecureBank Application Launcher for PowerShell
param (
    [switch]$Cli,
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$ExtraArgs
)

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

if (-not (Test-Path "target\classes")) {
    & .\build.ps1
}

$launchArgs = @("-cp", "lib/*;target/classes;.", "com.securebank.SecureBankMain")
if ($Cli -or ($ExtraArgs -contains "--cli") -or ($ExtraArgs -contains "-c") -or ($ExtraArgs -contains "--console")) {
    $launchArgs += "--cli"
}

& $javaCmd $launchArgs
