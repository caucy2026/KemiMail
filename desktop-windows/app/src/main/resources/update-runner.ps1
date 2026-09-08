param([Parameter(Mandatory=$true)][string]$ManifestPath)
$ErrorActionPreference = 'Stop'
$exitCode = 1
$lock = $null
$directory = Split-Path -Parent $ManifestPath
$resultPath = Join-Path $directory 'result.txt'
try {
    $m = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($m.version -notmatch '^\d+\.\d+\.\d+$' -or $m.sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid manifest' }
    $target = [IO.Path]::GetFullPath($m.target)
    $installer = [IO.Path]::GetFullPath($m.installer)
    if (-not (Test-Path -LiteralPath $target -PathType Leaf) -or [IO.Path]::GetExtension($installer) -ne '.exe') { throw 'Invalid package or target' }
    if (-not (Test-Path -LiteralPath (Join-Path (Split-Path $target) 'app') -PathType Container)) { throw 'Invalid installation' }
    # Keep the package read-locked from verification through installer completion.
    $lock = [IO.File]::Open($installer,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    if ($lock.Length -ne [long]$m.size) { throw 'Package length mismatch' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($lock))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }
    if ($hash -ne $m.sha256.ToLowerInvariant()) { throw 'Package hash mismatch' }
    [IO.File]::WriteAllText((Join-Path $directory 'ready.txt'),'ready')
    $old = Get-Process -Id $m.pid -ErrorAction SilentlyContinue
    if ($old -and -not $old.WaitForExit(60000)) { throw 'Application did not exit; installation cancelled' }
    $installDir = Split-Path -Parent $target
    # JDK 24 jpackage EXE passes these arguments to msiexec. Preserve the current per-user install location.
    $argsList = @('/quiet','/norestart',('INSTALLDIR="' + $installDir + '"'),'/L*v',('"' + (Join-Path $directory 'msi.log') + '"'))
    $setup = Start-Process -FilePath $installer -ArgumentList $argsList -PassThru -Wait
    if ($setup.ExitCode -notin @(0,3010)) { throw ('Installer failed: ' + $setup.ExitCode) }
    $lock.Dispose(); $lock = $null
    $restartArgs = @('--updated',('"' + $m.receipt + '"'))
    if ($m.smoke) { $restartArgs = @('--update-smoke-result',('"' + $m.smoke + '"'),'--updated',('"' + $m.receipt + '"')) }
    Start-Process -FilePath $target -WorkingDirectory $installDir -ArgumentList $restartArgs | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ((Test-Path -LiteralPath $m.receipt) -and ([IO.File]::ReadAllText($m.receipt) -eq $m.version)) { $exitCode = 0; break }
        Start-Sleep -Milliseconds 300
    }
    if ($exitCode -ne 0) { throw 'New version did not acknowledge startup; keep logs and installer for recovery' }
    [IO.File]::WriteAllText($resultPath,('SUCCESS ' + $m.version))
    Remove-Item -LiteralPath $installer
} catch {
    # No credentials or mailbox data are part of this manifest or log.
    [IO.File]::WriteAllText($resultPath,('FAILED: ' + $_.Exception.Message))
} finally {
    if ($lock) { $lock.Dispose() }
}
exit $exitCode
