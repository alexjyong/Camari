# Local build script for camari-obs-plugin (Windows)
#
# Usage:
#   .\build.ps1              # Release build
#   .\build.ps1 -Debug       # Debug build
#   .\build.ps1 -Test        # Build and run unit tests
#   .\build.ps1 -Clean       # Delete build directory first
#   .\build.ps1 -Clean -Test -Debug   # Combine as needed

param(
    [switch]$Debug,
    [switch]$Test,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$BuildDir  = Join-Path $ScriptDir "build"
$Config    = if ($Debug) { "Debug" } else { "Release" }

if ($Clean) {
    Write-Host "Cleaning $BuildDir..."
    Remove-Item -Recurse -Force $BuildDir -ErrorAction SilentlyContinue
}

$ExtraFlags = @()
if ($Test) {
    $ExtraFlags += "-DBUILD_TESTING=ON"
}

Write-Host "Configuring ($Config)..."
cmake -B $BuildDir -S $ScriptDir -DCMAKE_BUILD_TYPE=$Config @ExtraFlags
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Building..."
cmake --build $BuildDir --config $Config --parallel
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Test) {
    Write-Host "Running tests..."
    ctest --test-dir $BuildDir -C $Config --output-on-failure
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Done. Plugin: $BuildDir\$Config\camari-obs-plugin.dll"
