[CmdletBinding()]
param(
    [string] $DatabasePath,
    [switch] $Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($DatabasePath)) {
    $DatabasePath = Join-Path $repositoryRoot 'build\demo\nusynapxe-demo.db'
}

$resolvedDatabasePath = [System.IO.Path]::GetFullPath($DatabasePath)
if (Test-Path -LiteralPath $resolvedDatabasePath -PathType Container) {
    throw "The database path is a directory: $resolvedDatabasePath"
}

$databaseFiles = @(
    $resolvedDatabasePath
    "$resolvedDatabasePath-wal"
    "$resolvedDatabasePath-shm"
    "$resolvedDatabasePath-journal"
)
$existingFiles = @($databaseFiles | Where-Object { Test-Path -LiteralPath $_ })
if ($existingFiles.Count -gt 0 -and -not $Force) {
    throw "Refusing to delete an existing database. Re-run with -Force: $resolvedDatabasePath"
}

$gradlewPath = Join-Path $repositoryRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlewPath -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradlewPath"
}

$gradleArguments = @(
    'demoData'
    "-PdemoDataCommand=reset"
    "-PdemoDatabasePath=$resolvedDatabasePath"
    '-PdemoDataForce=true'
    '--no-daemon'
    '--console=plain'
)
& $gradlewPath @gradleArguments
if ($LASTEXITCODE -ne 0) {
    throw "Demo database reset failed with exit code $LASTEXITCODE"
}

Write-Host "Reset demo database: $resolvedDatabasePath"
