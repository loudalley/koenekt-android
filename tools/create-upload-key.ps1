param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$privateDir = Join-Path $project '.signing'
$keystore = Join-Path $privateDir 'koenekt-upload.jks'
$properties = Join-Path $privateDir 'signing.properties'
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $properties)) {
    throw 'Upload-key files already exist. Preserve them; never silently replace a release identity.'
}
New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
$account = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $privateDir /inheritance:r /grant:r "${account}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing directory access.' }
$bytes = New-Object byte[] 36
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$secret = [Convert]::ToBase64String($bytes)
$env:KOENEKT_UPLOAD_PASSWORD = $secret
try {
    & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -keystore $keystore -storetype JKS -alias koenekt-upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Koenekt Android Upload, O=Koenekt, C=ZA' -storepass:env KOENEKT_UPLOAD_PASSWORD -keypass:env KOENEKT_UPLOAD_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not create upload key.' }
    "storePassword=$secret`nkeyPassword=$secret`nkeyAlias=koenekt-upload`n" | Set-Content -LiteralPath $properties -Encoding ascii
} finally { Remove-Item Env:\KOENEKT_UPLOAD_PASSWORD -ErrorAction SilentlyContinue }
Write-Output 'Created private Android upload key. Back up .signing securely; do not distribute or commit it.'
