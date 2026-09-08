$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repo
$release = @{}
Get-Content 'desktop-windows\version.properties' | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $release[$matches[1]]=$matches[2] } }
$version = $release.versionName
if ($version -notmatch '^\d+\.\d+\.\d+$' -or [int]$release.versionCode -le 0) { throw 'Invalid release version' }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-24' }
if (-not (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) { throw 'JDK with jpackage required' }
$destination = Join-Path $repo "desktop-windows\dist\$version"
if (Test-Path $destination) { throw 'Release directory already exists; use a new version' }
& .\gradlew.bat -p desktop-windows :app:test :app:packageExe --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Native tests or build failed' }
$packages = @(Get-ChildItem "desktop-windows\app\build\compose\binaries\$version\main\exe" -Filter '*.exe')
if ($packages.Count -ne 1) { throw 'Expected exactly one Windows installer' }
New-Item -ItemType Directory -Path $destination | Out-Null
$installer = Join-Path $destination ($packages[0].BaseName + '-Setup.exe')
Copy-Item -LiteralPath $packages[0].FullName -Destination $installer
$item = Get-Item -LiteralPath $installer
$metadata = [ordered]@{
 app_name=([regex]::Unescape($release.appName)); package_name=$release.packageName; os_type='windows'; architecture='x64'
 version_name=$version; version_code=[int]$release.versionCode
 artifact=$installer; file_size_bytes=$item.Length; sha256=(Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant()
 list_in_store=$false; force_update=$false; source_commit=(git rev-parse HEAD)
 signature=(Get-AuthenticodeSignature -LiteralPath $installer).Status.ToString()
}
$metadata | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $destination 'release.json')
$metadata | ConvertTo-Json
