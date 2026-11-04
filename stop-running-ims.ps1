# Release the requested port only when its listener is an IMS Java process.
$ErrorActionPreference = 'Stop'
$port = 0
if ($env:SERVER_PORT -notmatch '^[0-9]+$' -or
    -not [int]::TryParse($env:SERVER_PORT, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
    Write-Error 'SERVER_PORT must be a valid TCP port from 1 to 65535.'
    exit 1
}

function Get-PortOwners {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
}

$owners = @(Get-PortOwners)
if ($owners.Count -eq 0) { exit 0 }
if ($owners.Count -ne 1) { throw "Port $port has multiple owners. No process was stopped." }

$owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])"
if (!$owner) {
    if (@(Get-PortOwners).Count -eq 0) { exit 0 }
    throw "The owner of port $port changed during startup."
}

$match = [regex]::Match($owner.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))')
$jar = ($match.Groups[1].Value + $match.Groups[2].Value).Replace('/', '\')
$target = Join-Path $PSScriptRoot 'server\target'
$validJar = $jar -match '^target\\ims-server-(?:1\.0\.0|run-[0-9]+)\.jar$' -or
    $jar -match ('^' + [regex]::Escape($target) + '\\ims-server-(?:1\.0\.0|run-[0-9]+)\.jar$')
if ($owner.Name -notin @('java.exe', 'javaw.exe') -or !$validJar) {
    throw "Port $port is in use by PID $($owner.ProcessId) ($($owner.Name)). Only iPage-IMS is stopped automatically."
}

$process = Get-Process -Id $owner.ProcessId -ErrorAction SilentlyContinue
if ($process -and [Math]::Abs(($process.StartTime - $owner.CreationDate).TotalMilliseconds) -lt 10) {
    Write-Host "Stopping iPage-IMS on port $port (PID $($owner.ProcessId))..."
    Stop-Process -InputObject $process
}
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    if (@(Get-PortOwners).Count -eq 0) { exit 0 }
    Start-Sleep -Milliseconds 250
}
throw "Port $port was not released after stopping iPage-IMS."
