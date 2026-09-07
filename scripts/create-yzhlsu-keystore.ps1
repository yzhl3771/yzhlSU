param(
    [string]$OutputDirectory = ".private-yzhlsu",
    [string]$Alias = "yzhlsu"
)

$ErrorActionPreference = "Stop"

$keytool = Get-Command keytool -ErrorAction Stop
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($resolvedOutput) | Out-Null

$keystorePath = Join-Path $resolvedOutput "yzhlsu.jks"
$secretsPath = Join-Path $resolvedOutput "github-secrets.txt"

if (Test-Path -LiteralPath $keystorePath) {
    throw "Refusing to overwrite existing keystore: $keystorePath"
}
if (Test-Path -LiteralPath $secretsPath) {
    throw "Refusing to overwrite existing secret file: $secretsPath"
}

function New-RandomSecret {
    $bytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

$storePassword = New-RandomSecret
$keyPassword = New-RandomSecret

& $keytool.Source -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -storetype JKS `
    -keystore $keystorePath `
    -storepass $storePassword `
    -keypass $keyPassword `
    -dname "CN=yzhlSU Manager, OU=Personal Build, O=yzhlSU"

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE"
}

$keystoreBase64 = [Convert]::ToBase64String(
    [System.IO.File]::ReadAllBytes($keystorePath)
)

$secretLines = @(
    "KEYSTORE=$keystoreBase64",
    "KEYSTORE_PASSWORD=$storePassword",
    "KEY_ALIAS=$Alias",
    "KEY_PASSWORD=$keyPassword"
)
[System.IO.File]::WriteAllLines($secretsPath, $secretLines)

Write-Host "Created: $keystorePath"
Write-Host "Created: $secretsPath"
Write-Host "Add the four values from github-secrets.txt as GitHub Actions repository secrets."
Write-Warning "Keep both files private and backed up. Losing the key prevents APK upgrades."
