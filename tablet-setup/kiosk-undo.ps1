<#
.SYNOPSIS
  Reverses kiosk-setup.ps1 using the values it saved in kiosk-state.json.
.PARAMETER Adb
  Path to adb.exe (default: adb on PATH).
#>
param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$statePath = Join-Path $PSScriptRoot 'kiosk-state.json'
if (-not (Test-Path $statePath)) { throw "No saved state at $statePath; nothing to undo." }
$state = Get-Content $statePath -Raw | ConvertFrom-Json

foreach ($package in @($state.hidden)) {
    $result = (& $Adb shell pm unhide --user 0 $package) -join ' '
    Write-Host "unhidden $package ($result)"
}
foreach ($entry in ($state.settings.PSObject.Properties | Where-Object { $_.Name -match '^(global|secure|system)/' })) {
    $namespace, $key = $entry.Name -split '/', 2
    if ($entry.Value -eq 'null' -or [string]::IsNullOrEmpty($entry.Value)) { & $Adb shell settings delete $namespace $key | Out-Null }
    else { & $Adb shell settings put $namespace $key $entry.Value | Out-Null }
    Write-Host "restored $($entry.Name) = $($entry.Value)"
}
Write-Host 'Restored. If you set the dashboard as the Home app, choose another Home app in Settings > Apps > Cadillac CTS Dashboard > Clear defaults, then reboot.'
