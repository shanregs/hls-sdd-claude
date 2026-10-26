# Starts the Android emulator, waits until it is online, then builds/installs and runs the HLS
# mobile app (docs/running-mobile.md section 3).
#   .\scripts\run-mobile.ps1                 # emulator + `npx expo start` (app already installed)
#   .\scripts\run-mobile.ps1 -Build          # emulator + `npx expo run:android` (first run, or after native changes)
#   .\scripts\run-mobile.ps1 -Avd Pixel_7    # pick an emulator (default: the first one found)
# Start the database and backend separately (scripts\start-db.ps1, scripts\run-backend.ps1).
param(
    [string]$Avd,
    [switch]$Build,
    [switch]$Wipe
)
$ErrorActionPreference = 'Stop'

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$jdk = if ($env:HLS_JDK17) { $env:HLS_JDK17 } else { 'C:\Program Files\Java\jdk-17' }
if (-not (Test-Path "$sdk\emulator\emulator.exe")) { throw "Android SDK not found at $sdk. Set ANDROID_HOME." }
if (-not (Test-Path "$jdk\bin\java.exe")) { throw "JDK 17 not found at $jdk. Set HLS_JDK17 (JDK 25 breaks the Android build)." }

# JDK 17 is required: Android Studio's bundled Java 25 fails the build.
$env:ANDROID_HOME = $sdk
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$sdk\platform-tools;$sdk\emulator;$env:Path"

if (-not $Avd) {
    $Avd = (& emulator -list-avds | Where-Object { $_ -match '\S' } | Select-Object -First 1)
    if (-not $Avd) { throw 'No emulator found. Create one in Android Studio -> Device Manager.' }
}

# Backend check (warning only).
if (-not (Test-NetConnection localhost -Port 8080 -InformationLevel Quiet -WarningAction SilentlyContinue)) { Write-Warning 'Backend is not answering on http://localhost:8080. Run scripts\start-db.ps1 and scripts\run-backend.ps1.' }

$running = (& adb devices) -match 'emulator-\d+\s+device'
if (-not $running) {
    Write-Host "Starting emulator $Avd ..."
    $emuArgs = @('-avd', $Avd, '-no-snapshot', '-no-audio', '-no-boot-anim', '-gpu', 'swiftshader_indirect',
        '-feature', '-Vulkan', '-memory', '3072', '-cores', '4')
    if ($Wipe) { $emuArgs += '-wipe-data' }
    Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList $emuArgs -WindowStyle Minimized | Out-Null

    Write-Host 'Waiting for the emulator to boot (first boot can take 3 to 5 minutes) ...'
    & adb wait-for-device
    $deadline = (Get-Date).AddMinutes(10)
    while ((& adb shell getprop sys.boot_completed 2>$null) -notmatch '1') {
        if ((Get-Date) -gt $deadline) { throw 'Emulator did not finish booting in 10 minutes.' }
        Start-Sleep -Seconds 5
    }
}
Write-Host 'Emulator is online.'

Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'mobile')
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
if (-not (Test-Path 'node_modules')) { npm install }

if ($Build) {
    Write-Host 'Building and installing the app (first build takes 10 to 20 minutes) ...'
    npx expo run:android
} else {
    Write-Host 'Starting Metro. Press "a" to open the app on the emulator.'
    npx expo start --android
}
