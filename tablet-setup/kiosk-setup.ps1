<#
.SYNOPSIS
  Turns the tablet into a Cadillac CTS Dashboard appliance: hidden system bars, no lock screen,
  no notification clutter and a trimmed-down set of apps. Everything is reversible (kiosk-undo.ps1).

.DESCRIPTION
  Run this yourself with the tablet connected over USB and USB debugging allowed.
  It changes Android system settings and hides system apps, so it is deliberately NOT run by the
  assistant. Nothing is deleted: apps are hidden with `pm hide`, which kiosk-undo.ps1 reverses.
  The previous values are saved to kiosk-state.json next to this script first.

  Test in two steps: run without -Aggressive, reboot, check Wi-Fi/Bluetooth/hotspot and the
  dashboard still work, and only then run again with -Aggressive.

.PARAMETER Adb
  Path to adb.exe (default: adb on PATH).
.PARAMETER Aggressive
  Also hides Google Play services and a few more vendor packages. Higher risk; do this last.
#>
param(
    [string]$Adb = 'adb',
    [switch]$Aggressive
)
$ErrorActionPreference = 'Stop'
$statePath = Join-Path $PSScriptRoot 'kiosk-state.json'

# Apps that are not needed to run the dashboard. Core system pieces (android, systemui, settings,
# bluetooth, telephony, providers, package installer, keyboard, launcher3 as a fallback) are NOT listed.
$basic = @(
  'com.android.browser','com.android.chrome','com.android.calculator2','com.android.calendar','com.android.contacts',
  'com.android.deskclock','com.android.dialer','com.android.email','com.android.exchange','com.android.fmradio',
  'com.android.gallery3d','com.android.htmlviewer','com.android.mms','com.android.music','com.android.musicfx',
  'com.android.soundrecorder','com.android.stk','com.android.vending','com.android.printspooler',
  'com.android.galaxy4','com.android.musicvis','com.android.noisefield','com.android.phasebeam','com.android.wallpaper.holospiral',
  'com.android.wallpaper.livepicker','com.android.dreams.basic','com.android.dreams.phototable',
  'com.google.android.apps.books','com.google.android.apps.cloudprint','com.google.android.apps.maps','com.google.android.apps.plus',
  'com.google.android.gm','com.google.android.googlequicksearchbox','com.google.android.street','com.google.android.talk',
  'com.google.android.videos','com.google.android.youtube','com.google.android.marvin.talkback','com.google.android.tts',
  'com.google.android.feedback','com.google.android.onetimeinitializer','com.google.android.partnersetup','com.google.android.configupdater',
  'com.mediatek.engineermode','com.mediatek.mtklogger','com.mediatek.ygps','com.mediatek.systemupdate','com.mediatek.systemupdate.sysoper',
  'com.mediatek.apst.target','com.mediatek.calendarimporter','com.mediatek.omacp','com.mediatek.schpwronoff','com.mediatek.lbs.em2.ui',
  'com.fw.upgrade','com.fw.upgrade.sysoper','com.example'
)
$more = @(
  'com.google.android.gms','com.google.android.gsf','com.google.android.gsf.login','com.google.android.backuptransport',
  'com.android.facelock','com.mediatek.filemanager','com.mediatek.dataprotection'
)
$packages = if ($Aggressive) { $basic + $more } else { $basic }

function Invoke-Adb { param([string[]]$Arguments) & $Adb @Arguments }
$devices = (Invoke-Adb @('devices')) -match "`tdevice$"
if (-not $devices) { throw 'No authorised device found. Connect the tablet and allow USB debugging.' }

$settings = @(
  @{ Namespace = 'global'; Key = 'policy_control' },
  @{ Namespace = 'global'; Key = 'heads_up_notifications_enabled' },
  @{ Namespace = 'secure'; Key = 'lockscreen.disabled' },
  @{ Namespace = 'secure'; Key = 'lock_screen_show_notifications' },
  @{ Namespace = 'secure'; Key = 'lock_screen_allow_private_notifications' }
)
$state = if (Test-Path $statePath) { Get-Content $statePath -Raw | ConvertFrom-Json } else { [pscustomobject]@{ settings = @{}; hidden = @() } }
$savedSettings = @{}
if ($state.settings) { $state.settings.PSObject.Properties | ForEach-Object { $savedSettings[$_.Name] = $_.Value } }
foreach ($item in $settings) {
    $name = "$($item.Namespace)/$($item.Key)"
    if (-not $savedSettings.ContainsKey($name)) { $savedSettings[$name] = ((Invoke-Adb @('shell','settings','get',$item.Namespace,$item.Key)) -join '').Trim() }
}
$hidden = @($state.hidden)
@{ settings = $savedSettings; hidden = $hidden } | ConvertTo-Json -Depth 4 | Set-Content $statePath -Encoding utf8
Write-Host "Saved the current values to $statePath"

# 1. System bars hidden everywhere; a swipe from the screen edge still reveals them (functionality kept).
Invoke-Adb @('shell','settings','put','global','policy_control','immersive.full=*')
# 2. No lock screen, and nothing (notifications, time, status bar) can show on one.
Invoke-Adb @('shell','settings','put','secure','lockscreen.disabled','1')
Invoke-Adb @('shell','settings','put','secure','lock_screen_show_notifications','0')
Invoke-Adb @('shell','settings','put','secure','lock_screen_allow_private_notifications','0')
# 3. No heads-up pop-ups.
Invoke-Adb @('shell','settings','put','global','heads_up_notifications_enabled','0')

# 4. Hide apps and services the dashboard does not need (reversible).
$installed = (Invoke-Adb @('shell','pm','list','packages')) | ForEach-Object { $_ -replace '^package:','' -replace '\s','' }
foreach ($package in $packages) {
    if ($installed -notcontains $package) { continue }
    $result = (Invoke-Adb @('shell','pm','hide','--user','0',$package)) -join ' '
    if ($result -match 'true') { $hidden += $package; Write-Host "hidden  $package" } else { Write-Host "skipped $package ($result)" }
}
@{ settings = $savedSettings; hidden = @($hidden | Select-Object -Unique) } | ConvertTo-Json -Depth 4 | Set-Content $statePath -Encoding utf8

Write-Host @'

Done. Next:
  1. On the tablet press Home and choose "Cadillac CTS Dashboard" > Always, so Home opens the dashboard.
  2. If the tablet has a PIN, pattern or password: Settings > Security > Screen lock > None
     (Android does not let this script remove it for you).
  3. Reboot, then check the dashboard opens by itself and Wi-Fi, Bluetooth and the hotspot still work.
Undo everything with kiosk-undo.ps1.
'@
