[CmdletBinding()]
param(
    [string] $DatabasePath,
    [switch] $Reset
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($DatabasePath)) {
    if ([string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        throw 'USERPROFILE is not set; supply -DatabasePath explicitly'
    }
    $DatabasePath = Join-Path $env:USERPROFILE '.nusynapxe\nusynapxe.db'
}

$resolvedDatabasePath = [System.IO.Path]::GetFullPath($DatabasePath)
if (Test-Path -LiteralPath $resolvedDatabasePath -PathType Container) {
    throw "The database path is a directory: $resolvedDatabasePath"
}

$gradlewPath = Join-Path $repositoryRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlewPath -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradlewPath"
}

$gradleArguments = @(
    'demoData'
    '-PdemoDataCommand=seed'
    "-PdemoDatabasePath=$resolvedDatabasePath"
    '--no-daemon'
    '--console=plain'
)
if ($Reset) {
    $gradleArguments += '-PdemoDataReset=true'
}

& $gradlewPath @gradleArguments
if ($LASTEXITCODE -ne 0) {
    throw "Demo data seeding failed with exit code $LASTEXITCODE"
}

Write-Host "Seeded demo database: $resolvedDatabasePath"
Write-Host ''
Write-Host 'Demo credentials (for showcase use only):'
Write-Host '  System Admin : admin.demo / DemoAdmin123!'
Write-Host '  Doctor       : doctor.ada / DemoDoctor123!'
Write-Host '  Doctor       : doctor.grace / DemoDoctor123!'
Write-Host '  Receptionist : reception.demo / DemoReception123!'
Write-Host ''
Write-Host 'The seeded doctors have calendar settings, a lunch break, and future appointments.'
