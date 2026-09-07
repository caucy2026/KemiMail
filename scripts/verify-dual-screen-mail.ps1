[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$ApkPath = "",
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$PackageName = "com.fsck.k9.debug"
$DefaultApkDirectory = Join-Path $PSScriptRoot "..\app-k9mail\build\outputs\kemi\debug"
$DefaultApkPath = Get-ChildItem -Path $DefaultApkDirectory -Filter "KEMI-Mail-v*-debug.apk" -File `
    -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArguments
    )

    $output = & adb -s $script:TargetSerial @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: adb -s $script:TargetSerial $($AdbArguments -join ' ')`n$output"
    }

    return $output
}

function Get-ConnectedDevices {
    $lines = & adb devices -l 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read the ADB device list:`n$lines"
    }

    return @(
        $lines |
            Select-String -Pattern '^([^\s]+)\s+device(?:\s|$)' |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
    )
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found. Install Android Platform Tools and add adb to PATH."
}

if ($Serial) {
    $knownDevices = @(Get-ConnectedDevices)
    if ($knownDevices -notcontains $Serial) {
        $connectOutput = & adb connect $Serial 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to connect to $Serial`:`n$connectOutput"
        }
    }

    $script:TargetSerial = $Serial
} else {
    $knownDevices = @(Get-ConnectedDevices)
    if ($knownDevices.Count -eq 0) {
        throw "No online ADB device. Run adb connect <tablet-ip:wireless-debug-port>, or use -Serial."
    }
    if ($knownDevices.Count -gt 1) {
        throw "Multiple online devices found. Select one with -Serial: $($knownDevices -join ', ')"
    }

    $script:TargetSerial = $knownDevices[0]
}

$deviceState = (& adb -s $script:TargetSerial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") {
    throw "The target is not in the device state: $script:TargetSerial ($deviceState)"
}

if (-not $SkipInstall) {
    if (-not $ApkPath -and $null -eq $DefaultApkPath) {
        throw "No standard KEMI Debug APK found. Run ./gradlew :app-k9mail:kemiDebugApk first."
    }

    $resolvedApkPath = if ($ApkPath) { $ApkPath } else { $DefaultApkPath.FullName }
    $resolvedApkPath = (Resolve-Path -LiteralPath $resolvedApkPath).Path
    Write-Host "Installing APK without clearing application data: $resolvedApkPath"
    Invoke-Adb -AdbArguments @("install", "-r", $resolvedApkPath) | Write-Host
}

Write-Host "Launching KEMI Mail"
Invoke-Adb -AdbArguments @(
    "shell",
    "monkey",
    "-p",
    $PackageName,
    "-c",
    "android.intent.category.LAUNCHER",
    "1"
) | Write-Host

Write-Host "`nApplication version:"
Invoke-Adb -AdbArguments @("shell", "dumpsys", "package", $PackageName) |
    Select-String -Pattern 'versionCode=|versionName=' |
    ForEach-Object { $_.Line.Trim() } |
    Write-Host

Write-Host "`nDisplay capability summary:"
Invoke-Adb -AdbArguments @("shell", "dumpsys", "display") |
    Select-String -Pattern 'DisplayDeviceInfo|displayId|FLAG_PRESENTATION|1920 x 1280|1920x1280' |
    ForEach-Object { $_.Line.Trim() } |
    Select-Object -First 40 |
    Write-Host

Write-Host "`nKEMI Mail activity summary:"
Invoke-Adb -AdbArguments @("shell", "dumpsys", "activity", "activities") |
    Select-String -Pattern 'topResumedActivity|mResumedActivity|com\.fsck\.k9' |
    ForEach-Object { $_.Line.Trim() } |
    Select-Object -First 20 |
    Write-Host

Write-Host @"

Manual checks (the script does not read accounts, mail content, attachments, or the full UI tree):
[ ] Switching Immersive/Smart preserves account, folder, and selected message
[ ] List position, drawer, search, and multi-select survive recreation as expected
[ ] Home/lock resumes projection; disconnect degrades safely; reconnect offers recovery
[ ] Simplified/Traditional Chinese labels are complete and not clipped
[ ] The mode button and dialog remain usable at the largest system font size
[ ] Both screens have correct backgrounds and contrast in dark mode
[ ] TalkBack reads the mode, current selection, and upper-mirror explanation; use lower-screen mail controls

Target device: $script:TargetSerial
"@
